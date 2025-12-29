/**
 * @file client_handler.c
 * @brief Handles main logic for connected clients
 */

#include "../include/client_handler.h"
#include "../include/protocol.h"
#include "../include/connection_manager.h"
#include "../include/utils/logger.h"

#include "../include/repo/user_repo.h"
#include "../include/repo/friend_repo.h"
#include "../include/repo/message_repo.h"
#include "../include/repo/group_repo.h"

#include <stdlib.h>
#include <sys/socket.h>
#include <unistd.h>
#include <string.h>
#include <time.h>

// --- HELPER FUNCTIONS ---

/**
 * @brief Helper to ensure full data reception.
 */
static int recv_all(int sock, void *buffer, int size)
{
    int total_received = 0;
    int bytes_left = size;
    char *ptr = (char *)buffer;
    while (total_received < size)
    {
        int received = recv(sock, ptr + total_received, bytes_left, 0);
        if (received <= 0)
            return received;
        total_received += received;
        bytes_left -= received;
    }
    return total_received;
}

/**
 * @brief Sends a packet header + optional payload.
 */
static void send_response(int sock, int32_t cmd_type, int32_t req_id, int32_t status, void *payload, int32_t payload_size)
{
    PacketHeader header;
    memset(&header, 0, sizeof(PacketHeader));

    header.version = SERVER_PROTOCOL_VERSION;
    header.command_type = cmd_type;
    header.request_id = req_id;
    header.status_code = status;
    header.payload_size = payload_size;
    header.timestamp = get_current_timestamp_ms();

    send(sock, &header, sizeof(PacketHeader), 0);
    if (payload_size > 0 && payload != NULL)
    {
        send(sock, payload, payload_size, 0);
    }
}

static void send_list_response(int sock, int32_t cmd, int32_t req_id, int32_t status, int32_t count, void *items, int32_t item_size)
{
    PacketHeader header;
    memset(&header, 0, sizeof(PacketHeader));

    header.version = SERVER_PROTOCOL_VERSION;
    header.command_type = cmd;
    header.request_id = req_id;
    header.status_code = status;
    header.timestamp = get_current_timestamp_ms();

    // Payload Size = [4 bytes Count] + [Dữ liệu mảng]
    int32_t data_size = count * item_size;
    header.payload_size = sizeof(int32_t) + data_size;

    // 1. Gửi Header
    send(sock, &header, sizeof(PacketHeader), 0);

    // 2. Gửi Count (Luôn luôn gửi, kể cả count = 0)
    send(sock, &count, sizeof(int32_t), 0);

    // 3. Gửi Data (Chỉ gửi nếu có dữ liệu)
    if (count > 0 && items != NULL)
    {
        send(sock, items, data_size, 0);
    }
}

/**
 * @brief Hàm loại bỏ dữ liệu thừa từ socket (Dùng khi status trong Header là lỗi)
 */
static void discard_payload(int sock, int size)
{
    char buffer[1024];
    int remaining = size;
    while (remaining > 0)
    {
        int to_read = (remaining < sizeof(buffer)) ? remaining : sizeof(buffer);
        int received = recv(sock, buffer, to_read, 0);
        if (received <= 0)
            break;
        remaining -= received;
    }
}

/**
 * Gửi thông báo trạng thái cho TOÀN BỘ bạn bè
 * status: 1 = Online, 0 = Offline
 */
static void notify_friends_status(int user_id, int status)
{
    // 1. Lấy danh sách ID bạn bè
    int batch_size = 1000; // Mỗi lần lấy 1000 bạn
    int offset = 0;
    int *friend_ids = (int *)malloc(batch_size * sizeof(int));

    if (friend_ids == NULL)
    {
        LOG_ERROR("Malloc failed in notify_friends_status");
        return;
    }

    // 2. Chuẩn bị gói tin
    StatusNotifyPayload notify;
    notify.friend_id = user_id;
    notify.is_online = (int8_t)status;

    // 3. Loop và gửi (Chỉ gửi cho người đang có Socket Online)
    while (1)
    {
        int count = db_get_friend_ids(user_id, friend_ids, batch_size, offset);

        if (count <= 0)
            break; // Hết bạn rồi, thoát vòng lặp

        // Loop gửi thông báo
        for (int i = 0; i < count; i++)
        {
            int f_sock = get_socket_by_user_id(friend_ids[i]);
            if (f_sock != -1)
            {
                send_response(f_sock, CMD_NOTIFY_STATUS, 0, STATUS_SUCCESS, &notify, sizeof(StatusNotifyPayload));
            }
        }

        // Tăng offset để lấy đợt tiếp theo
        offset += count;

        // Nếu số lượng lấy được < batch_size nghĩa là đã là trang cuối cùng
        if (count < batch_size)
            break;
    }

    free(friend_ids);
    LOG_INFO("User %d status (%d). Processed check for %d friends.", user_id, status, offset);
}

static void notify_friend_req_received(int request_id, int sender_id, int target_id)
{
    // 1. Kiểm tra target có online không
    int target_sock = get_socket_by_user_id(target_id);
    if (target_sock == -1)
        return; // Offline -> Thôi

    // 2. Chuẩn bị payload thông báo
    PendingReqInfo notif;
    memset(&notif, 0, sizeof(PendingReqInfo));
    notif.request_id = request_id;
    notif.sender_id = sender_id;

    // 3. Lấy tên người gửi để hiện thông báo
    get_user_name_by_id(sender_id, notif.sender_name, sizeof(notif.sender_name));

    // 4. Gửi
    send_response(target_sock, CMD_NOTIFY_FRIEND_REQ, 0, STATUS_SUCCESS, &notif, sizeof(PendingReqInfo));
    LOG_INFO("Notified User %d about friend req from %s (ID: %d)", target_id, notif.sender_name, sender_id);
}

/**
 * @brief Thông báo cho người đã gửi lời mời biết rằng lời mời đã được chấp nhận
 *
 * @param acceptor_id ID của người vừa chấp nhận lời mời (B)
 * @param receiver_id ID của người nhận thông báo (A - người đã gửi lời mời trước đó)
 */
static void notify_req_accepted_realtime(int acceptor_id, int receiver_id)
{
    int sock_receiver = get_socket_by_user_id(receiver_id);
    if (sock_receiver == -1)
        return; // Người nhận không online -> Bỏ qua

    UserInfoPayload acceptor_info;
    memset(&acceptor_info, 0, sizeof(UserInfoPayload));

    // Lấy thông tin người chấp nhận để gửi cho người kia
    // Hàm này giả định bạn đã có hoặc tự viết thêm trong user_repo
    if (db_get_user_info_by_id(acceptor_id, &acceptor_info) == 0)
    {
        // Fallback nếu không query được DB
        acceptor_info.user_id = acceptor_id;
        acceptor_info.is_online = 1;
        snprintf(acceptor_info.name, sizeof(acceptor_info.name), "User %d", acceptor_id);
    }
    acceptor_info.is_online = 1; // Chắc chắn đang online vì vừa bấm nút

    // Gửi đi
    send_response(sock_receiver, CMD_NOTIFY_REQ_ACCEPTED, 0, STATUS_SUCCESS,
                  &acceptor_info, sizeof(UserInfoPayload));

    LOG_INFO("Real-time: Notified User %d that User %d accepted.", receiver_id, acceptor_id);
}

/**
 * @brief Xử lý đẩy tin nhắn offline (Batch processing)
 * Logic: Lấy 50 tin -> Gửi -> Notify Sender -> Lặp lại đến khi hết tin 'sent'
 */
static void push_offline_messages(int sock, int user_id)
{
    int batch_size = 50;
    ChatPayload msgs[50];
    int count;

    do
    {
        // 1. Lấy batch tin nhắn (Luôn lấy limit 50 tin sent cũ nhất)
        // Vì sau khi xử lý xong ta update status='delivered', nên lần query sau sẽ ra 50 tin tiếp theo.
        count = db_get_offline_messages(user_id, msgs, batch_size);

        if (count > 0)
        {
            LOG_INFO("Pushing batch of %d offline messages to User %d", count, user_id);

            for (int i = 0; i < count; i++)
            {
                // 2. Gửi cho User vừa Login (Offline -> Online)
                send_response(sock, CMD_RECEIVE_MESSAGE, 0, STATUS_SUCCESS,
                              &msgs[i], sizeof(ChatPayload));

                // 3. Update DB thành Delivered
                db_mark_message_delivered(msgs[i].message_id);

                // 4. Báo ngược lại cho người gửi (A) biết tin đã đến (Nếu A đang online)
                int sender_sock = get_socket_by_user_id(msgs[i].sender_id);
                if (sender_sock != -1)
                {
                    MsgDeliveredPayload notify;
                    notify.message_id = msgs[i].message_id;
                    notify.receiver_id = user_id;

                    send_response(sender_sock, CMD_NOTIFY_MSG_DELIVERED, 0, STATUS_SUCCESS,
                                  &notify, sizeof(MsgDeliveredPayload));
                }
            }
        }
        // Nếu lấy đủ 50 tin, có thể vẫn còn tin nữa -> Lặp tiếp
        // Nếu lấy < 50 tin -> Đã hết tin -> Dừng vòng lặp
    } while (count == batch_size);
}

// --- LOGIC HANDLERS ---
static void handle_register(int sock, PacketHeader *reqHeader, void *payload)
{
    RegisterPayload *data = (RegisterPayload *)payload;
    LOG_INFO("Register Request: %s", data->email);

    int result = db_register_user(data->name, data->email, data->password);

    int status;
    if (result == 1)
    {
        status = STATUS_SUCCESS;
    }
    else if (result == -1)
    {
        status = STATUS_ERROR_ALREADY_EXIST;
    }
    else
    {
        status = STATUS_ERROR_DB;
    }

    send_response(sock, CMD_REGISTER_RESP, reqHeader->request_id, status, NULL, 0);
}

static int handle_login(int sock, PacketHeader *reqHeader, void *payload)
{
    LoginPayload *data = (LoginPayload *)payload;
    LOG_INFO("Login Request: %s", data->email);

    UserInfoPayload userInfo;
    memset(&userInfo, 0, sizeof(UserInfoPayload));

    int userId = db_check_login(data->email, data->password, &userInfo);

    if (userId > 0)
    {
        // 1. Send Success Response
        send_response(sock, CMD_LOGIN_RESP, reqHeader->request_id, STATUS_SUCCESS, &userInfo, sizeof(UserInfoPayload));

        // 2. Update State
        db_update_user_status(userId, 1);
        add_connected_client(sock, userId);

        // 3. Notify Friends
        notify_friends_status(userId, 1);

        LOG_INFO("User %d logged in.", userId);
        return userId;
    }
    else
    {
        send_response(sock, CMD_LOGIN_RESP, reqHeader->request_id, STATUS_ERROR_AUTH, NULL, 0);
        LOG_WARN("Login failed: %s", data->email);
        return -1;
    }
}

static void handle_get_friends(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    // 1. Kiểm tra xem đã login chưa
    if (current_user_id == -1)
    {
        LOG_WARN("Unauthenticated request for Friend List.");
        send_response(sock, CMD_GET_FRIEND_LIST_RESP, reqHeader->request_id, STATUS_ERROR_AUTH, NULL, 0);
        return;
    }

    // 2. Parse Offset/Limit từ Payload request
    int offset = 0;
    int limit = 100; // Mặc định

    if (reqHeader->payload_size == sizeof(GetFriendListReq))
    {
        GetFriendListReq *req = (GetFriendListReq *)payload;
        offset = req->offset;
        limit = req->limit;
        if (limit > 100)
            limit = 100;
        if (limit < 1)
            limit = 20;
    }

    // 3. Query DB
    UserInfoPayload *friends = (UserInfoPayload *)malloc(limit * sizeof(UserInfoPayload));
    if (friends == NULL)
    {
        send_response(sock, CMD_GET_FRIEND_LIST_RESP, reqHeader->request_id, STATUS_ERROR_UNKNOWN, NULL, 0);
        return;
    }

    int count = db_get_friends(current_user_id, offset, limit, friends);

    send_list_response(sock, CMD_GET_FRIEND_LIST_RESP, reqHeader->request_id, STATUS_SUCCESS,
                       count, friends, sizeof(UserInfoPayload));

    free(friends);
    LOG_INFO("Sent %d friends to User %d.", count, current_user_id);
}

static void handle_send_message(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    ChatPayload *msg = (ChatPayload *)payload;

    // 1. Validate: Sender ID trong gói tin phải khớp với người đang login
    if (msg->sender_id != current_user_id)
    {
        LOG_WARN("User %d tried to spoof sender_id as %d", current_user_id, msg->sender_id);
        msg->sender_id = current_user_id;
    }

    // 2. Lấy thời gian thực của Server (Server Time)
    uint64_t server_time = get_current_timestamp_ms();

    // 3. Lưu vào DB (Trạng thái mặc định là 'sent')
    int new_msg_id = db_save_message(current_user_id, msg->receiver_id, msg->content, server_time, msg->msg_type, "private");

    if (new_msg_id <= 0)
    {
        LOG_ERROR("Failed to save message from User %d", current_user_id);
        send_response(sock, CMD_SEND_MESSAGE_RESP, reqHeader->request_id, STATUS_ERROR_DB, NULL, 0);
        return;
    }

    LOG_INFO("Msg Saved ID: %d. Sender: %d -> Receiver: %d", new_msg_id, current_user_id, msg->receiver_id);

    // 4. Gửi ACK về cho người gửi (A)
    // Mục đích: Để A cập nhật trạng thái "Sending" -> "Sent" và cập nhật lại Timestamp chuẩn Server
    ChatPayload respPayload = *msg;
    respPayload.message_id = new_msg_id;  // ID thật trong DB
    respPayload.created_at = server_time; // Thời gian thật

    send_response(sock, CMD_SEND_MESSAGE_RESP, reqHeader->request_id, STATUS_SUCCESS,
                  &respPayload, sizeof(ChatPayload));

    // 5. Kiểm tra người nhận (B) có Online không?
    int receiver_sock = get_socket_by_user_id(msg->receiver_id);

    if (receiver_sock != -1)
    {
        // --- TRƯỜNG HỢP ONLINE ---

        // 5.1 Gửi tin nhắn cho B (Real-time)
        // Request ID gửi cho B là 0 hoặc số mới, không liên quan đến Request ID của A
        send_response(receiver_sock, CMD_RECEIVE_MESSAGE, 0, STATUS_SUCCESS,
                      &respPayload, sizeof(ChatPayload));

        LOG_INFO("Forwarded Msg %d to User %d (Online)", new_msg_id, msg->receiver_id);

        // 5.2 Cập nhật trạng thái 'delivered' trong DB Server
        db_mark_message_delivered(new_msg_id);

        // 5.3 [Optional] Báo ngược lại cho A biết là B đã nhận
        MsgDeliveredPayload delivPayload;
        delivPayload.message_id = new_msg_id;
        delivPayload.receiver_id = msg->receiver_id;

        send_response(sock, CMD_NOTIFY_MSG_DELIVERED, 0, STATUS_SUCCESS,
                      &delivPayload, sizeof(MsgDeliveredPayload));
    }
    else
    {
        // --- TRƯỜNG HỢP OFFLINE ---
        LOG_INFO("User %d is Offline. Msg %d saved as 'sent'.", msg->receiver_id, new_msg_id);
        // Tin nhắn vẫn nằm trong DB với status 'sent', chờ user B login gọi API fetch_offline
    }
}

/**
 * @brief Xử lý yêu cầu tìm kiếm người dùng.
 */
static void handle_search_users(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    SearchReqPayload *req = (SearchReqPayload *)payload;

    // Validate độ dài từ khóa
    if (strlen(req->keyword) < 1)
    {
        send_response(sock, CMD_SEARCH_USERS_RESP, reqHeader->request_id, STATUS_ERROR_INVALID_PARAM, NULL, 0);
        return;
    }

    // Xử lý Limit/Offset
    int limit = req->limit;
    int offset = req->offset;

    if (limit <= 0 || limit > 50)
        limit = 20; // Mặc định 20, max 50
    if (offset < 0)
        offset = 0;

    UserSearchInfo *results = (UserSearchInfo *)malloc(limit * sizeof(UserSearchInfo));

    if (!results)
    {
        LOG_ERROR("Malloc failed in handle_search_users");
        send_response(sock, CMD_SEARCH_USERS_RESP, reqHeader->request_id, STATUS_ERROR_UNKNOWN, NULL, 0);
        return;
    }

    int count = db_search_users(req->keyword, current_user_id, results, limit, offset);

    send_list_response(sock, CMD_SEARCH_USERS_RESP, reqHeader->request_id, STATUS_SUCCESS,
                       count, results, sizeof(UserSearchInfo));

    free(results);
    LOG_INFO("Search '%s' (Off:%d, Lim:%d) -> Found %d", req->keyword, offset, limit, count);
}

static void handle_send_friend_req(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    FriendReqPayload *req = (FriendReqPayload *)payload;

    LOG_INFO("=== HANDLE_SEND_FRIEND_REQ: User %d -> Target %d ===", current_user_id, req->target_id);

    // 1. Gọi Repo
    int result = db_send_friend_request(current_user_id, req->target_id);

    LOG_INFO("db_send_friend_request returned: %d", result);

    // 2. Map kết quả DB sang Status Code Protocol
    int status = STATUS_SUCCESS;
    if (result > 0)
        status = STATUS_SUCCESS;
    else if (result == -1)
        status = STATUS_ERROR_ALREADY_FRIEND;
    else if (result == -2)
        status = STATUS_ERROR_REQ_PENDING;
    else if (result == -3)
        status = STATUS_ERROR_INVALID_PARAM; // Tự kết bạn
    else
        status = STATUS_ERROR_DB;

    LOG_INFO("Mapped status code: %d", status);

    // 3. Phản hồi cho người gửi (để UI hiện Toast Success/Fail)
    send_response(sock, CMD_SEND_FRIEND_REQ_RESP, reqHeader->request_id, status, NULL, 0);
    LOG_INFO("Sent CMD_SEND_FRIEND_REQ_RESP with status %d to User %d", status, current_user_id);

    // 4. Nếu thành công -> Thông báo cho người nhận
    if (result > 0)
    {
        notify_friend_req_received(result, current_user_id, req->target_id);
    }
}

static void handle_get_pending_reqs(int sock, PacketHeader *reqHeader, int current_user_id)
{
    PendingReqInfo list[50];
    int count = db_get_pending_requests(current_user_id, list, 50);

    // Đóng gói: [Count] + [List]
    int payload_size = sizeof(int32_t) + (count * sizeof(PendingReqInfo));
    void *resp_buffer = malloc(payload_size);

    memcpy(resp_buffer, &count, sizeof(int32_t));
    if (count > 0)
    {
        memcpy((char *)resp_buffer + sizeof(int32_t), list, count * sizeof(PendingReqInfo));
    }

    send_response(sock, CMD_GET_PENDING_REQS_RESP, reqHeader->request_id, STATUS_SUCCESS, resp_buffer, payload_size);
    free(resp_buffer);
}

static void handle_respond_friend_req(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    FriendRespondPayload *resp = (FriendRespondPayload *)payload;
    int sender_id_of_req = 0;

    // 1. Cập nhật DB (Chấp nhận/Từ chối)
    int success = db_respond_friend_request(resp->request_id, current_user_id, resp->is_accepted, &sender_id_of_req);

    // 2. Phản hồi cho người đang thao tác
    int status = success ? STATUS_SUCCESS : STATUS_ERROR_DB;
    send_response(sock, CMD_RESPOND_FRIEND_REQ_RESP, reqHeader->request_id, status, NULL, 0);

    LOG_INFO("User %d responded to req %d (Accepted: %d). Status: %d",
             current_user_id, resp->request_id, resp->is_accepted, status);

    // --- REAL-TIME NOTIFICATION (Nếu chấp nhận -> Báo cho người gửi biết) ---
    if (success && resp->is_accepted && sender_id_of_req > 0)
    {
        notify_req_accepted_realtime(current_user_id, sender_id_of_req);
    }
}

static void handle_unfriend(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    FriendReqPayload *req = (FriendReqPayload *)payload;
    int target_id = req->target_id; // Người bị unfriend
    LOG_INFO("User %d requesting UNFRIEND target %d", current_user_id, req->target_id);

    int success = db_remove_friend(current_user_id, req->target_id);
    int status = STATUS_SUCCESS;
    if (!success)
    {
        LOG_WARN("Unfriend failed or relationship not found.");
        status = STATUS_ERROR_DB;
    }

    send_response(sock, CMD_UNFRIEND_RESP, reqHeader->request_id, status, NULL, 0);

    if (success)
    {
        int target_sock = get_socket_by_user_id(target_id);
        if (target_sock != -1)
        {
            FriendReqPayload notifyPayload;
            notifyPayload.target_id = current_user_id; // ID của người vừa unfriend mình

            send_response(target_sock, CMD_NOTIFY_UNFRIENDED, 0, STATUS_SUCCESS,
                          &notifyPayload, sizeof(FriendReqPayload));

            LOG_INFO("Real-time: Notified User %d that User %d unfriended them.", target_id, current_user_id);
        }
    }
}

static void handle_fetch_offline(int sock, PacketHeader *reqHeader, int current_user_id)
{
    push_offline_messages(sock, current_user_id);
    send_response(sock, CMD_FETCH_OFFLINE_MSGS_RESP, reqHeader->request_id, STATUS_SUCCESS, NULL, 0);
}

static void handle_get_history(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    GetHistoryPayload *req = (GetHistoryPayload *)payload;

    int target_id = req->target_id;
    int limit = req->limit;
    int offset = req->offset;

    if (limit <= 0 || limit > 50)
        limit = 20; // Default logic
    if (offset < 0)
        offset = 0;

    LOG_INFO("User %d fetch history with %d (Off: %d, Lim: %d)", current_user_id, target_id, offset, limit);

    // Cấp phát bộ nhớ
    ChatPayload *history = (ChatPayload *)malloc(limit * sizeof(ChatPayload));
    if (!history)
    {
        send_response(sock, CMD_GET_HISTORY_RESP, reqHeader->request_id, STATUS_ERROR_UNKNOWN, NULL, 0);
        return;
    }

    // Query DB
    int count = db_get_chat_history(current_user_id, target_id, history, limit, offset);

    // Gửi phản hồi dạng List
    send_list_response(sock, CMD_GET_HISTORY_RESP, reqHeader->request_id, STATUS_SUCCESS,
                       count, history, sizeof(ChatPayload));

    free(history);
    LOG_INFO("Sent %d history messages to User %d", count, current_user_id);
}

static void handle_create_group(int sock, PacketHeader *reqHeader, void *payload, int current_user_id)
{
    // 1. Kiểm tra kích thước tối thiểu (phải chứa được struct req)
    if (reqHeader->payload_size < (int32_t)sizeof(CreateGroupReqPayload))
    {
        LOG_WARN("User %d sent invalid create group payload size", current_user_id);
        send_response(sock, CMD_CREATE_GROUP_RESP, reqHeader->request_id, STATUS_ERROR_INVALID_PARAM, NULL, 0);
        return;
    }

    CreateGroupReqPayload *req = (CreateGroupReqPayload *)payload;

    // 2. Validate tính toán vẹn để chống Buffer Over-read
    int32_t expected_size = sizeof(CreateGroupReqPayload) + (req->member_count * sizeof(int32_t));
    if (reqHeader->payload_size != expected_size)
    {
        LOG_WARN("User %d: Payload size mismatch. Expected %d, got %d",
                 current_user_id, expected_size, reqHeader->payload_size);
        send_response(sock, CMD_CREATE_GROUP_RESP, reqHeader->request_id, STATUS_ERROR_INVALID_PARAM, NULL, 0);
        return;
    }

    // 3. Kiểm tra tên nhóm rỗng
    if (strlen(req->group_name) == 0)
    {
        send_response(sock, CMD_CREATE_GROUP_RESP, reqHeader->request_id, STATUS_ERROR_INVALID_PARAM, NULL, 0);
        return;
    }

    // Xác định vị trí mảng ID thành viên trong memory
    int32_t *member_ids = (int32_t *)((char *)payload + sizeof(CreateGroupReqPayload));

    // 4. Gọi DB thực hiện Transaction
    int group_id = db_create_group(req->group_name, current_user_id, member_ids, req->member_count);

    if (group_id > 0)
    {
        CreateGroupRespPayload resp;
        memset(&resp, 0, sizeof(CreateGroupRespPayload)); 
        resp.group_id = group_id;

        strncpy(resp.group_name, req->group_name, MAX_GROUP_NAME - 1);
        resp.group_name[MAX_GROUP_NAME - 1] = '\0';

        // 5. Phản hồi cho người tạo (ACK)
        send_response(sock, CMD_CREATE_GROUP_RESP, reqHeader->request_id, STATUS_SUCCESS, &resp, sizeof(resp));

        // 6. BROADCAST cho các thành viên khác đang Online
        LOG_INFO("Broadcasting new group %d to members...", group_id);
        for (int i = 0; i < req->member_count; i++)
        {
            int target_id = member_ids[i];

            // Không gửi thông báo cho chính mình (vì đã nhận ACK ở trên)
            if (target_id == current_user_id)
                continue;

            int target_sock = get_socket_by_user_id(target_id);
            if (target_sock != -1)
            {
                // Sử dụng cùng gói resp để tiết kiệm tài nguyên
                send_response(target_sock, CMD_NOTIFY_GROUP_CREATED, 0, STATUS_SUCCESS, &resp, sizeof(resp));
                LOG_INFO("Notified User %d about new group '%s' (ID: %d)", target_id, resp.group_name, group_id);
            }
        }
    }
    else
    {
        LOG_ERROR("User %d failed to create group in DB", current_user_id);
        send_response(sock, CMD_CREATE_GROUP_RESP, reqHeader->request_id, STATUS_ERROR_DB, NULL, 0);
    }
}

// --- MAIN THREAD LOOP ---
void *handle_client(void *socket_desc)
{
    int sock = *(int *)socket_desc;
    free(socket_desc);

    PacketHeader header;
    int current_user_id = -1;

    while (1)
    {
        // 1. Read Header
        int status = recv_all(sock, &header, sizeof(PacketHeader));
        if (status <= 0)
            break;

        // 2. KIỂM TRA STATUS CODE TRONG HEADER
        // Nếu Header báo lỗi, ta KHÔNG xử lý payload, nhưng PHẢI đọc bỏ payload để dọn socket
        if (header.status_code != STATUS_SUCCESS)
        {
            LOG_WARN("Received packet with error status: %d. Command: %d. Discarding payload...",
                     header.status_code, header.command_type);

            if (header.payload_size > 0)
            {
                discard_payload(sock, header.payload_size);
            }
            continue; // Bỏ qua vòng lặp này, chờ gói tin tiếp theo
        }

        // 3. Read Payload
        void *payload = NULL;
        if (header.payload_size > 0)
        {
            payload = malloc(header.payload_size);
            if (payload == NULL)
            {
                LOG_ERROR("Malloc failed for size %d", header.payload_size);
                break;
            }

            if (recv_all(sock, payload, header.payload_size) <= 0)
            {
                free(payload);
                break;
            }
        }

        // 4. AUTH CHECK
        if (current_user_id == -1 &&
            header.command_type != CMD_REGISTER &&
            header.command_type != CMD_LOGIN)
        {

            LOG_WARN("Unauthorized access attempt from socket %d", sock);
            send_response(sock, CMD_ERROR_UNKNOWN, header.request_id, STATUS_ERROR_AUTH, NULL, 0);
            if (payload)
                free(payload);
            continue;
        }

        // 5. Dispatch Command
        switch (header.command_type)
        {
        case CMD_REGISTER:
            handle_register(sock, &header, payload);
            break;
        case CMD_LOGIN:
        {
            int uid = handle_login(sock, &header, payload);
            if (uid > 0)
                current_user_id = uid;
            break;
        }
        case CMD_GET_FRIEND_LIST:
            handle_get_friends(sock, &header, payload, current_user_id);
            break;
        case CMD_SEND_MESSAGE:
            handle_send_message(sock, &header, payload, current_user_id);
            break;
        case CMD_FETCH_OFFLINE_MSGS:
            handle_fetch_offline(sock, &header, current_user_id);
            break;
        case CMD_SEARCH_USERS:
            handle_search_users(sock, &header, payload, current_user_id);
            break;
        case CMD_SEND_FRIEND_REQ:
            handle_send_friend_req(sock, &header, payload, current_user_id);
            break;
        case CMD_GET_PENDING_REQS:
            handle_get_pending_reqs(sock, &header, current_user_id);
            break;
        case CMD_RESPOND_FRIEND_REQ:
            handle_respond_friend_req(sock, &header, payload, current_user_id);
            break;
        case CMD_UNFRIEND:
            handle_unfriend(sock, &header, payload, current_user_id);
            break;
        case CMD_GET_HISTORY:
            handle_get_history(sock, &header, payload, current_user_id);
            break;
        case CMD_CREATE_GROUP:
            handle_create_group(sock, &header, payload, current_user_id);
            break;

        default:
            LOG_WARN("Unknown Command: %d", header.command_type);
            break;
        }

        if (payload)
            free(payload);
    }

    // Cleanup khi disconnect
    if (current_user_id != -1)
    {
        db_update_user_status(current_user_id, 0);
        remove_connected_client(current_user_id);
        notify_friends_status(current_user_id, 0);
    }

    close(sock);
    LOG_INFO("Client disconnected (Sock %d)", sock);
    return NULL;
}
