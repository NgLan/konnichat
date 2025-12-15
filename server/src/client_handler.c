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

    header.version = PROTOCOL_VERSION;
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

// --- LOGIC HANDLERS ---
static void handle_register(int sock, PacketHeader *reqHeader, void *payload)
{
    AuthPayload *data = (AuthPayload *)payload;
    LOG_INFO("Register Request: %s", data->email);

    int success = db_register_user(data->name, data->email, data->password);
    int status = success ? STATUS_SUCCESS : STATUS_ERROR_AUTH;

    send_response(sock, CMD_RESPONSE, reqHeader->request_id, status, NULL, 0);
}

static int handle_login(int sock, PacketHeader *reqHeader, void *payload)
{
    AuthPayload *data = (AuthPayload *)payload;
    LOG_INFO("Login Request: %s", data->email);

    UserInfoPayload userInfo;
    memset(&userInfo, 0, sizeof(UserInfoPayload));

    int userId = db_check_login(data->email, data->password, &userInfo);

    if (userId > 0)
    {
        // 1. Send Success Response
        send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_SUCCESS, &userInfo, sizeof(UserInfoPayload));

        // 2. Update State
        db_update_user_status(userId, 1);
        add_connected_client(sock, userId);

        // 3. Notify Friends
        UserInfoPayload friends[100];
        memset(friends, 0, sizeof(friends));
        int count = db_get_friends(userId, friends, 100);
        for (int i = 0; i < count; i++)
        {
            int f_sock = get_socket_by_user_id(friends[i].user_id);
            if (f_sock != -1)
            {
                StatusNotifyPayload notify = {userId, 1};
                send_response(f_sock, CMD_NOTIFY_STATUS, 0, STATUS_SUCCESS, &notify, sizeof(StatusNotifyPayload));
            }
        }

        LOG_INFO("User %d logged in.", userId);
        return userId;
    }
    else
    {
        send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_ERROR_AUTH, NULL, 0);
        LOG_WARN("Login failed: %s", data->email);
        return -1;
    }
}

static void handle_get_friends(int sock, PacketHeader *reqHeader, int current_user_id)
{
    // 1. Kiểm tra xem đã login chưa
    if (current_user_id == -1)
    {
        LOG_WARN("Unauthenticated request for Friend List.");
        send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_ERROR_AUTH, NULL, 0);
        return;
    }

    UserInfoPayload friends[100];
    memset(friends, 0, sizeof(friends));
    int count = db_get_friends(current_user_id, friends, 100);

    PacketHeader respHeader;
    memset(&respHeader, 0, sizeof(PacketHeader));
    respHeader.version = PROTOCOL_VERSION;
    respHeader.command_type = CMD_RESPONSE;
    respHeader.request_id = reqHeader->request_id;
    respHeader.status_code = STATUS_SUCCESS;

    // Payload gồm: 4 bytes (Count) + N * Kích thước Struct
    respHeader.payload_size = sizeof(int32_t) + (count * sizeof(UserInfoPayload));
    respHeader.timestamp = get_current_timestamp_ms();

    // Gửi Header
    send(sock, &respHeader, sizeof(PacketHeader), 0);

    // Gửi Payload
    // Gửi số lượng
    send(sock, &count, sizeof(int32_t), 0);
    if (count > 0)
    {
        // Gửi mảng bạn bè
        send(sock, friends, count * sizeof(UserInfoPayload), 0);
    }

    LOG_INFO("Sent %d friends to User %d.", count, current_user_id);
}

static void handle_send_message(int sock, PacketHeader *reqHeader, void *payload)
{
    ChatPayload *chat = (ChatPayload *)payload;

    // 1. Lấy thời gian thực của Server ngay lúc nhận tin 
    uint64_t now = get_current_timestamp_ms(); 
    chat->created_at = now; 

    LOG_INFO("Msg: %d -> %d: %s", chat->sender_id, chat->receiver_id, chat->content);

    // 2. Lưu vào DB
    int new_msg_id = db_save_message(chat->sender_id, chat->receiver_id, chat->content, now);

    if (new_msg_id > 0)
    {
        // 3. Forward to receiver if online
        int receiver_sock = get_socket_by_user_id(chat->receiver_id);
        if (receiver_sock != -1)
        {
            chat->message_id = new_msg_id; // Cập nhật ID thật
            send_response(receiver_sock, CMD_RECEIVE_MESSAGE, 0, STATUS_SUCCESS, chat, sizeof(ChatPayload));

            db_mark_message_delivered(new_msg_id);
            LOG_INFO("Forwarded msg %d to User %d.", new_msg_id, chat->receiver_id);
        }
        else
        {
            LOG_INFO("User %d offline. Msg saved.", chat->receiver_id);
        }

        // 4. Phản hồi cho người gửi để họ cập nhật UI (VD: hiện dấu tích "Đã gửi")
        chat->message_id = new_msg_id;
        send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_SUCCESS, chat, sizeof(ChatPayload));
    }
    else
    {
        send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_ERROR_DB, NULL, 0);
    }
}

static void handle_fetch_offline(int sock, PacketHeader *reqHeader, int current_user_id)
{
    // Kiểm tra login
    if (current_user_id == -1)
    {
        LOG_WARN("Unauthenticated request for Offline Messages.");
        send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_ERROR_AUTH, NULL, 0);
        return;
    }

    ChatPayload msgs[50];
    int limit = 50;
    int count = db_get_offline_messages(current_user_id, msgs, limit);

    PacketHeader respHeader;
    memset(&respHeader, 0, sizeof(PacketHeader));
    respHeader.version = PROTOCOL_VERSION;
    respHeader.command_type = CMD_RESPONSE;
    respHeader.request_id = reqHeader->request_id;
    respHeader.status_code = STATUS_SUCCESS;

    // Payload gồm: 4 bytes (Count) + N * Kích thước Struct
    respHeader.payload_size = sizeof(int32_t) + (count * sizeof(ChatPayload));
    respHeader.timestamp = get_current_timestamp_ms();

    // Gửi Header
    send(sock, &respHeader, sizeof(PacketHeader), 0);

    // Gửi Payload
    send(sock, &count, sizeof(int32_t), 0);
    if (count > 0)
    {
        send(sock, msgs, count * sizeof(ChatPayload), 0);

        // Mark delivered
        for (int i = 0; i < count; i++)
        {
            db_mark_message_delivered(msgs[i].message_id);
        }
    }
    LOG_INFO("Synced %d offline msgs for User %d.", count, current_user_id);
}

static void handle_search_users(int sock, PacketHeader *reqHeader, void *payload, int current_user_id) {
    SearchReqPayload *req = (SearchReqPayload *)payload;
    LOG_INFO("User %d searching for: %s", current_user_id, req->keyword);

    // Chuẩn bị buffer kết quả
    UserSearchInfo results[20];
    int count = db_search_users(req->keyword, current_user_id, results, 20);

    // Payload trả về: [Count (4 bytes)] + [Array Data]
    int payload_size = sizeof(int32_t) + (count * sizeof(UserSearchInfo));
    void *resp_buffer = malloc(payload_size);
    
    // Đóng gói
    memcpy(resp_buffer, &count, sizeof(int32_t));
    if (count > 0) {
        memcpy((char*)resp_buffer + sizeof(int32_t), results, count * sizeof(UserSearchInfo));
    }

    send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_SUCCESS, resp_buffer, payload_size);
    free(resp_buffer);
}

static void handle_send_friend_req(int sock, PacketHeader *reqHeader, void *payload, int current_user_id) {
    FriendReqPayload *req = (FriendReqPayload *)payload;
    
    // Gọi Repo xử lý logic
    int result_id = db_send_friend_request(current_user_id, req->target_id);
    
    // Phản hồi cho người gửi
    int status = (result_id > 0) ? STATUS_SUCCESS : STATUS_ERROR_UNKNOWN;
    send_response(sock, CMD_RESPONSE, reqHeader->request_id, status, NULL, 0);

    // --- REAL-TIME NOTIFICATION ---
    if (result_id > 0) {
        int target_sock = get_socket_by_user_id(req->target_id);
        if (target_sock > 0) {
            PendingReqInfo notif;
            notif.request_id = result_id;
            notif.sender_id = current_user_id;
            
            // Lấy tên người gửi để hiển thị thông báo đẹp hơn
            // (Tạm thời hardcode hoặc query DB thêm 1 lần nữa nếu cần thiết)
            snprintf(notif.sender_name, sizeof(notif.sender_name), "User %d", current_user_id); 

            send_response(target_sock, CMD_NOTIFY_FRIEND_REQ, 0, STATUS_SUCCESS, &notif, sizeof(PendingReqInfo));
            LOG_INFO("Sent friend request notification to socket %d", target_sock);
        }
    }
}

static void handle_get_pending_reqs(int sock, PacketHeader *reqHeader, int current_user_id) {
    PendingReqInfo list[50];
    int count = db_get_pending_requests(current_user_id, list, 50);

    // Đóng gói: [Count] + [List]
    int payload_size = sizeof(int32_t) + (count * sizeof(PendingReqInfo));
    void *resp_buffer = malloc(payload_size);
    
    memcpy(resp_buffer, &count, sizeof(int32_t));
    if (count > 0) {
        memcpy((char*)resp_buffer + sizeof(int32_t), list, count * sizeof(PendingReqInfo));
    }

    send_response(sock, CMD_RESPONSE, reqHeader->request_id, STATUS_SUCCESS, resp_buffer, payload_size);
    free(resp_buffer);
}

static void handle_respond_friend_req(int sock, PacketHeader *reqHeader, void *payload, int current_user_id) {
    FriendRespondPayload *resp = (FriendRespondPayload *)payload;
    int sender_id_of_req = 0;

    int success = db_respond_friend_request(resp->request_id, current_user_id, resp->is_accepted, &sender_id_of_req);
    int status = success ? STATUS_SUCCESS : STATUS_ERROR_UNKNOWN;

    send_response(sock, CMD_RESPONSE, reqHeader->request_id, status, NULL, 0);

    // --- REAL-TIME NOTIFICATION (Báo cho người gửi biết) ---
    if (success && resp->is_accepted && sender_id_of_req > 0) {
        int sender_sock = get_socket_by_user_id(sender_id_of_req);
        if (sender_sock > 0) {
            UserInfoPayload my_info;
            my_info.user_id = current_user_id;
            my_info.is_online = 1;
            // Cần query lấy tên thật nếu muốn chuẩn, tạm thời để trống hoặc ID
            snprintf(my_info.name, sizeof(my_info.name), "Friend ID %d", current_user_id);

            send_response(sender_sock, CMD_NOTIFY_REQ_ACCEPTED, 0, STATUS_SUCCESS, &my_info, sizeof(UserInfoPayload));
        }
    }
}

static void handle_unfriend(int sock, PacketHeader *reqHeader, void *payload, int current_user_id) {
    FriendReqPayload *req = (FriendReqPayload *)payload; // Dùng chung struct vì chỉ cần target_id
    
    int success = db_remove_friend(current_user_id, req->target_id);
    int status = success ? STATUS_SUCCESS : STATUS_ERROR_UNKNOWN;
    
    send_response(sock, CMD_RESPONSE, reqHeader->request_id, status, NULL, 0);
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
            header.command_type != CMD_LOGIN) {
            
            LOG_WARN("Unauthorized access attempt from socket %d", sock);
            send_response(sock, CMD_RESPONSE, header.request_id, STATUS_ERROR_AUTH, NULL, 0);
            if (payload) free(payload);
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
                handle_get_friends(sock, &header, current_user_id);
                break;
            case CMD_SEND_MESSAGE:
                handle_send_message(sock, &header, payload);
                break;
            case CMD_FETCH_OFFLINE_MSGS:
                handle_fetch_offline(sock, &header, current_user_id);
                break;
        // --- SEARCH ---
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
        if (current_user_id != -1) {
            remove_connected_client(current_user_id); // Truyền ID, đừng truyền sock
        }

        // Notify friends offline
        UserInfoPayload friends[100];
        memset(friends, 0, sizeof(friends));
        int count = db_get_friends(current_user_id, friends, 100);
        for (int i = 0; i < count; i++)
        {
            int f_sock = get_socket_by_user_id(friends[i].user_id);
            if (f_sock != -1)
            {
                StatusNotifyPayload notify = {current_user_id, 0};
                send_response(f_sock, CMD_NOTIFY_STATUS, 0, STATUS_SUCCESS, &notify, sizeof(StatusNotifyPayload));
            }
        }
    }

    close(sock);
    LOG_INFO("Client disconnected (Sock %d)", sock);
    return NULL;
}
