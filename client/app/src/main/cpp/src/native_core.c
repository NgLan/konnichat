#include "../include/native_core.h"
#include "../include/utils/logger_utils.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <android/log.h>
#include <time.h>
#include <pthread.h>
#include <syslog.h>
#include <ctype.h>
#include <netdb.h>

static int g_socket = -1;
static int g_req_id = 0;
static int g_is_running = 0;
static pthread_t g_hb_thread = 0;
static pthread_t g_read_thread = 0;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t g_client_mutex = PTHREAD_MUTEX_INITIALIZER;

static NativeCallbacks g_callbacks;

// Helper: Lấy timestamp hiện tại
static uint64_t get_timestamp() {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (uint64_t) (ts.tv_sec) * 1000 + (uint64_t) (ts.tv_nsec) / 1000000;
}

// Helper: Gửi full data
static int send_all(int sock, void *data, int len) {
    int total = 0;
    int left = len;
    char *ptr = (char *) data;
    while (total < len) {
        int n = send(sock, ptr + total, left, 0);
        if (n == -1)
            return -1;
        total += n;
        left -= n;
    }
    return total;
}

// Helper: Nhận full data
static int recv_all(int sock, void *buffer, int len) {
    int total = 0;
    int left = len;
    char *ptr = (char *) buffer;
    while (total < len) {
        int n = recv(sock, ptr + total, left, 0);
        if (n <= 0)
            return n; // Error or Closed
        total += n;
        left -= n;
    }
    return total;
}

// Helper: Đọc bỏ payload rác
static void discard_data(int sock, int size) {
    if (size <= 0)
        return;
    char buffer[1024];
    int remaining = size;
    while (remaining > 0) {
        int to_read = (remaining < sizeof(buffer)) ? remaining : sizeof(buffer);
        int n = recv(sock, buffer, to_read, 0);
        if (n <= 0)
            break;
        remaining -= n;
    }
}

static void discard_payload(int sock, int size) {
    discard_data(sock, size);
    LOGW("Discarded %d bytes of garbage payload.", size);
}

// Helper: Gửi Header + Payload chung
static int send_request(int cmd, void *payload, int payload_size) {
    if (g_socket == -1)
        return -1;

    PacketHeader header;
    memset(&header, 0, sizeof(header));
    header.version = SERVER_PROTOCOL_VERSION;
    header.command_type = cmd;
    header.payload_size = payload_size;
    header.request_id = ++g_req_id;
    header.timestamp = get_timestamp();

    if (send_all(g_socket, &header, sizeof(PacketHeader)) < 0)
        return -1;

    if (payload_size > 0 && payload != NULL) {
        if (send_all(g_socket, payload, payload_size) < 0)
            return -1;
    }
    return header.request_id;
}

static int recv_and_validate_header(PacketHeader *header, int expected_cmd) {
    if (g_socket == -1)
        return ERR_NETWORK_CONN_FAILED;

    LOGI("Dang cho doc Header...");
    int n = recv_all(g_socket, header, sizeof(PacketHeader));
    if (n <= 0) {
        LOGE("Socket Error or Closed while waiting for CMD %d", expected_cmd);
        return ERR_NETWORK_RECV_FAILED;
    }

    if (header->command_type != expected_cmd) {
        LOGE("Protocol Mismatch! Expected %d but got %d", expected_cmd, header->command_type);
        discard_payload(g_socket, header->payload_size);
        return ERR_PROTOCOL_MISMATCH;
    }
    return CLIENT_OK;
}

static void trim_string(char *str) {
    if (!str)
        return;
    char *ptr = str;
    int len = strlen(ptr);
    while (len > 0 && isspace(ptr[len - 1]))
        ptr[--len] = 0;
    while (*ptr && isspace(*ptr))
        ptr++, len--;
    memmove(str, ptr, len + 1);
}

// --- INIT & CONNECT ---
// --- INIT & CONNECT ---
// File: native_core.c

// --- HELPER MỚI THÊM ---
// Hàm để thay đổi timeout của socket linh hoạt
static void set_socket_timeout(int sock, int seconds) {
    struct timeval timeout;
    timeout.tv_sec = seconds;
    timeout.tv_usec = 0;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, (const char*)&timeout, sizeof(timeout));
}

int client_init(const char *host, int port)
{
    // 1. DỌN DẸP TRIỆT ĐỂ TRẠNG THÁI CŨ
    // Tắt cờ chạy để các thread cũ (nếu còn) tự thoát
    g_is_running = 0;
    if (g_socket != -1) {
        shutdown(g_socket, SHUT_RDWR);
        close(g_socket);
        g_socket = -1;
    }

    // 2. PHẢI ĐỢI luồng cũ thoát hẳn trước khi cho phép kết nối mới
    if (g_read_thread != 0) {
        pthread_join(g_read_thread, NULL);
        g_read_thread = 0;
    }
    if (g_hb_thread != 0) {
        pthread_join(g_hb_thread, NULL);
        g_hb_thread = 0;
    }

    // Reset request ID về 0 cho phiên kết nối mới
    g_req_id = 0;

    // 2. TẠO SOCKET MỚI (Dùng biến tạm)
    int temp_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (temp_sock < 0) {
        LOGE("Could not create socket");
        return -1;
    }

    // 3. THIẾT LẬP ĐỊA CHỈ
    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);

    // BƯỚC 1: Thử coi đây là địa chỉ IP số (VD: 192.168.1.5)
    if (inet_pton(AF_INET, host, &serv_addr.sin_addr) > 0) {
        // Là IP hợp lệ, không cần làm gì thêm
    } else {
        // BƯỚC 2: Nếu không phải IP, thử phân giải tên miền (DNS) cho Ngrok
        LOGI("Input is not IP, trying DNS resolve for: %s", host);
        struct hostent *server = gethostbyname(host);
        if (server == NULL) {
            LOGE("DNS Resolution Failed: %s", host);
            close(temp_sock);
            return -2;
        }

        // Copy địa chỉ IP tìm được từ DNS vào struct
        memcpy((char *) &serv_addr.sin_addr.s_addr,
               (char *) server->h_addr,
               server->h_length);
    }

    // 4. THIẾT LẬP TIMEOUT CHO SOCKET (Tránh bị treo khi autoLogin)
    set_socket_timeout(temp_sock, 5);

    // BƯỚC 3: Kết nối
    if (connect(g_socket, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
        LOGE("Connect failed to %s:%d", host, port);
        close(temp_sock);
        return -3;
    }

    // 6. CHỈ GÁN VÀO BIẾN TOÀN CỤC KHI THÀNH CÔNG RỰC RỠ
    g_socket = temp_sock;

    LOGI("✅ Connected successfully to %s:%d (Socket: %d)", host, port, g_socket);
    return 0;
}

void client_close() {
    g_is_running = 0;
    if (g_socket != -1) {
        shutdown(g_socket, SHUT_RDWR);
        close(g_socket);
        g_socket = -1;
    }
    if (g_hb_thread != 0) {
        pthread_join(g_hb_thread, NULL);
        g_hb_thread = 0;
    }
    if (g_read_thread != 0) {
        pthread_join(g_read_thread, NULL);
        g_read_thread = 0;
    }
}

// --- AUTH ---
int client_register(const char *name, const char *email, const char *password) {
    pthread_mutex_lock(&g_client_mutex);
    RegisterPayload payload;
    memset(&payload, 0, sizeof(payload));
    strncpy(payload.name, name, MAX_NAME_LEN - 1);
    strncpy(payload.email, email, MAX_EMAIL_LEN - 1);
    strncpy(payload.password, password, MAX_PASS_LEN - 1);

    if (send_request(CMD_REGISTER, &payload, sizeof(payload)) < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }

    PacketHeader resp;
    int status = recv_and_validate_header(&resp, CMD_REGISTER_RESP);
    if (status < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return status;
    }
    if (resp.payload_size > 0)
        discard_payload(g_socket, resp.payload_size);
    pthread_mutex_unlock(&g_client_mutex);
    return resp.status_code;
}

int client_login(const char *email, const char *password, UserInfoPayload *user_out) {
    pthread_mutex_lock(&g_client_mutex);
    LoginPayload payload;
    memset(&payload, 0, sizeof(payload));
    strncpy(payload.email, email, MAX_EMAIL_LEN - 1);
    strncpy(payload.password, password, MAX_PASS_LEN - 1);

    if (send_request(CMD_LOGIN, &payload, sizeof(payload)) < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }

    PacketHeader resp;
    int status = recv_and_validate_header(&resp, CMD_LOGIN_RESP);
    if (status < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return status;
    }

    if (resp.status_code == STATUS_SUCCESS) {
        if (resp.payload_size == sizeof(UserInfoPayload)) {
            if (recv_all(g_socket, user_out, sizeof(UserInfoPayload)) < 0) {
                pthread_mutex_unlock(&g_client_mutex);
                return ERR_NETWORK_RECV_FAILED;
            }
            pthread_mutex_unlock(&g_client_mutex);
            return STATUS_SUCCESS;
        } else {
            discard_payload(g_socket, resp.payload_size);
            pthread_mutex_unlock(&g_client_mutex);
            return ERR_PROTOCOL_SIZE_ERR;
        }
    } else {
        if (resp.payload_size > 0)
            discard_payload(g_socket, resp.payload_size);
        pthread_mutex_unlock(&g_client_mutex);
        return resp.status_code;
    }
}

// --- FEATURES ---
int client_get_friends(int offset, int limit) {
    if (limit < 1)
        limit = 20;
    if (limit > 100)
        limit = 100;
    GetFriendListReq req;
    req.offset = offset;
    req.limit = limit;
    pthread_mutex_lock(&g_send_mutex);
    int res = send_request(CMD_GET_FRIEND_LIST, &req, sizeof(req));
    pthread_mutex_unlock(&g_send_mutex);
    return (res > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_send_friend_request(int target_id) {
    FriendReqPayload payload;
    payload.target_id = target_id;
    pthread_mutex_lock(&g_send_mutex);
    int res = send_request(CMD_SEND_FRIEND_REQ, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);
    return (res > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_respond_friend_req(int request_id, int is_accepted) {
    FriendRespondPayload payload;
    payload.request_id = request_id;
    payload.is_accepted = (int8_t) is_accepted;
    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_RESPOND_FRIEND_REQ, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);
    return (req_id < 0) ? ERR_NETWORK_SEND_FAILED : CLIENT_OK;
}

int client_unfriend(int target_id) {
    FriendReqPayload payload;
    payload.target_id = target_id;
    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_UNFRIEND, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);
    return (req_id < 0) ? ERR_NETWORK_SEND_FAILED : CLIENT_OK;
}

int client_search_users(const char *keyword, int offset, int limit) {
    if (!keyword || strlen(keyword) == 0)
        return CLIENT_OK;
    SearchReqPayload payload;
    memset(&payload, 0, sizeof(payload));
    strncpy(payload.keyword, keyword, 49);
    trim_string(payload.keyword);
    if (strlen(payload.keyword) == 0)
        return CLIENT_OK;
    payload.offset = offset;
    payload.limit = limit;
    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_SEARCH_USERS, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);
    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_send_message(int sender_id, int receiver_id, const char *content, int request_id,
                        const char *chat_type) {
    ChatPayload payload;
    memset(&payload, 0, sizeof(payload));
    payload.sender_id = sender_id;
    payload.receiver_id = receiver_id;
    payload.msg_type = 1;
    if (chat_type) {
        strncpy(payload.chat_type, chat_type, sizeof(payload.chat_type) - 1);
    } else {
        strcpy(payload.chat_type, "private");
    }
    strncpy(payload.content, content, MAX_CONTENT_LEN - 1);
    // created_at gửi lên là client time (chỉ để tham khảo, server sẽ ghi đè)
    payload.created_at = get_timestamp();

    pthread_mutex_lock(&g_send_mutex);
    PacketHeader header;
    memset(&header, 0, sizeof(header));
    header.version = SERVER_PROTOCOL_VERSION;
    header.command_type = CMD_SEND_MESSAGE;
    header.payload_size = sizeof(ChatPayload);
    header.request_id = request_id;
    header.timestamp = get_timestamp();

    if (send_all(g_socket, &header, sizeof(PacketHeader)) < 0 ||
        send_all(g_socket, &payload, sizeof(ChatPayload)) < 0) {
        pthread_mutex_unlock(&g_send_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }
    pthread_mutex_unlock(&g_send_mutex);
    return CLIENT_OK;
}

int client_get_pending_requests() {
    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_GET_PENDING_REQS, NULL, 0);
    pthread_mutex_unlock(&g_send_mutex);
    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

// --- LOGIC XỬ LÝ GÓI TIN ĐẾN (ĐÃ FIX BUG DESYNC) ---
int client_fetch_offline_msgs() {
    pthread_mutex_lock(&g_send_mutex);
    // Gửi lệnh rỗng (không cần payload)
    int req_id = send_request(CMD_FETCH_OFFLINE_MSGS, NULL, 0);
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_get_history(int target_id, int is_group, int offset, int limit) {
    GetHistoryPayload payload;
    payload.target_id = target_id;
    payload.is_group = is_group;
    payload.offset = offset;
    payload.limit = limit;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_GET_HISTORY, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_create_group(const char *name, int32_t *member_ids, int count) {
    if (g_socket == -1)
        return ERR_NETWORK_CONN_FAILED;

    int payload_size = sizeof(CreateGroupReqPayload) + (count * sizeof(int32_t));
    void *buffer = malloc(payload_size);
    if (!buffer)
        return ERR_INTERNAL_MEM;

    CreateGroupReqPayload *req = (CreateGroupReqPayload *) buffer;
    memset(req->group_name, 0, MAX_GROUP_NAME);
    strncpy(req->group_name, name, MAX_GROUP_NAME - 1);
    req->member_count = count;

    // Copy mảng ID vào phần sau của buffer
    memcpy((char *) buffer + sizeof(CreateGroupReqPayload), member_ids, count * sizeof(int32_t));

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_CREATE_GROUP, buffer, payload_size);
    pthread_mutex_unlock(&g_send_mutex);

    free(buffer);
    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_add_group_members(int group_id, int32_t *member_ids, int count) {
    if (g_socket == -1) return ERR_NETWORK_CONN_FAILED;
    if (count <= 0) return CLIENT_OK;

    int payload_size = sizeof(AddGroupMemberPayload) + (count * sizeof(int32_t));
    void *buffer = malloc(payload_size);
    if (!buffer) return ERR_INTERNAL_MEM;

    AddGroupMemberPayload *req = (AddGroupMemberPayload *) buffer;
    req->group_id = group_id;
    req->count = count;
    req->added_by_user = 0; // Server sẽ điền
    memset(req->added_by_name, 0, MAX_NAME_LEN);

    // Copy mảng ID vào đuôi
    memcpy((char *) buffer + sizeof(AddGroupMemberPayload), member_ids, count * sizeof(int32_t));

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_ADD_MEMBER, buffer, payload_size);
    pthread_mutex_unlock(&g_send_mutex);

    free(buffer);
    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_leave_group(int group_id) {
    LeaveGroupReqPayload payload;
    payload.group_id = group_id;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_LEAVE_GROUP, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_get_group_list(int offset, int limit) {
    GetGroupListReq req;
    req.offset = offset;
    req.limit = limit;

    pthread_mutex_lock(&g_send_mutex);
    int res = send_request(CMD_GET_GROUP_LIST, &req, sizeof(req));
    pthread_mutex_unlock(&g_send_mutex);

    return (res > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_kick_member(int group_id, int target_id) {
    RemoveMemberReqPayload payload;
    payload.group_id = group_id;
    payload.target_user_id = target_id;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_REMOVE_MEMBER, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_dissolve_group(int group_id) {
    DissolveGroupReqPayload payload;
    payload.group_id = group_id;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_DISSOLVE_GROUP, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_get_group_members(int group_id, int offset, int limit) {
    GetGroupMembersReq req;
    req.group_id = group_id;
    req.offset = offset;
    req.limit = limit;

    pthread_mutex_lock(&g_send_mutex);
    int res = send_request(CMD_GET_GROUP_MEMBERS, &req, sizeof(req));
    pthread_mutex_unlock(&g_send_mutex);

    return (res > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

void client_logout() {
    if (g_socket != -1) {
        // 1. Gửi gói tin thông báo cho Server biết
        // Không cần chờ phản hồi, gửi xong là cắt luôn
        pthread_mutex_lock(&g_send_mutex);
        send_request(CMD_LOGOUT, NULL, 0);
        pthread_mutex_unlock(&g_send_mutex);
    }

    // 2. Đóng kết nối và dọn dẹp tài nguyên Client
    client_close();
}

int client_recall_message(int message_id) {
    InteractionPayload payload;
    memset(&payload, 0, sizeof(payload));
    payload.message_id = message_id;
    payload.action_type = 1; // Recall code

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_RECALL_MESSAGE, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_react_message(int message_id, int reaction_code) {
    InteractionPayload payload;
    memset(&payload, 0, sizeof(payload));
    payload.message_id = message_id;
    payload.action_type = 2; // React
    payload.reaction_code = reaction_code;
    // reactor_id client gửi lên để 0, server sẽ điền

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_REACT_MESSAGE, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

// --- LOGIC XỬ LÝ GÓI TIN ĐẾN ---
static void handle_incoming_packet(PacketHeader *header) {
    LOGI("=== handle_incoming_packet: CMD=%d, Status=%d, Size=%d ===", header->command_type,
         header->status_code, header->payload_size);

    int bytes_processed = 0;

    // 1. FRIEND LIST
    if (header->command_type == CMD_GET_FRIEND_LIST_RESP) {
        int32_t count = 0;
        if (recv_all(g_socket, &count, sizeof(int32_t)) > 0) {
            bytes_processed += sizeof(int32_t); // FIX: Cập nhật byte đã đọc
            if (count > 0) {
                int data_size = count * sizeof(UserInfoPayload);
                // Kiểm tra size an toàn
                if (data_size <= header->payload_size - bytes_processed) {
                    UserInfoPayload *friends = (UserInfoPayload *) malloc(data_size);
                    if (recv_all(g_socket, friends, data_size) > 0) {
                        bytes_processed += data_size; // FIX: Cập nhật byte đã đọc
                        if (g_callbacks.on_friend_list)
                            g_callbacks.on_friend_list(count, friends);
                    }
                    free(friends);
                }
            } else {
                if (g_callbacks.on_friend_list)
                    g_callbacks.on_friend_list(0, NULL);
            }
        }
    }

        // 2. RECEIVE MESSAGE
    else if (header->command_type == CMD_RECEIVE_MESSAGE) {
        ChatPayload msg;
        if (recv_all(g_socket, &msg, sizeof(ChatPayload)) > 0) {
            bytes_processed += sizeof(ChatPayload); // FIX
            if (g_callbacks.on_message)
                g_callbacks.on_message(&msg);
        }
    }

        // Xử lý STATUS (Online/Offline)
    else if (header->command_type == CMD_NOTIFY_STATUS) {
        StatusNotifyPayload notify;
        if (recv_all(g_socket, &notify, sizeof(StatusNotifyPayload)) > 0) {
            bytes_processed += sizeof(StatusNotifyPayload); // FIX
            if (g_callbacks.on_status_change)
                g_callbacks.on_status_change(notify.friend_id, notify.is_online);
        }
    }

        // 4. FRIEND REQ NOTIFICATION
    else if (header->command_type == CMD_NOTIFY_FRIEND_REQ) {
        PendingReqInfo info;
        if (recv_all(g_socket, &info, sizeof(PendingReqInfo)) > 0) {
            bytes_processed += sizeof(PendingReqInfo); // FIX
            LOGI("Received Friend Request from: %s (ID: %d)", info.sender_name, info.sender_id);
            if (g_callbacks.on_friend_req)
                g_callbacks.on_friend_req(info.request_id, info.sender_id, info.sender_name);
        }
    }

        // 5. REQ ACCEPTED NOTIFICATION
    else if (header->command_type == CMD_NOTIFY_REQ_ACCEPTED) {
        UserInfoPayload friend_info;
        if (recv_all(g_socket, &friend_info, sizeof(UserInfoPayload)) > 0) {
            bytes_processed += sizeof(UserInfoPayload); // FIX
            LOGI("Notification: User %s (%d) accepted your friend request.", friend_info.name,
                 friend_info.user_id);
            if (g_callbacks.on_request_accepted)
                g_callbacks.on_request_accepted(&friend_info);
        }
    }

        // 6. UNFRIEND NOTIFICATION
    else if (header->command_type == CMD_NOTIFY_UNFRIENDED) {
        FriendReqPayload payload;
        if (recv_all(g_socket, &payload, sizeof(FriendReqPayload)) > 0) {
            bytes_processed += sizeof(FriendReqPayload); // FIX QUAN TRỌNG
            LOGI("Notification: You have been unfriended by User ID %d", payload.target_id);
            if (g_callbacks.on_unfriended)
                g_callbacks.on_unfriended(payload.target_id);
        }
    }

        // 7. SEARCH RESULT
    else if (header->command_type == CMD_SEARCH_USERS_RESP) {
        int32_t count = 0;
        if (recv_all(g_socket, &count, sizeof(int32_t)) > 0) {
            bytes_processed += sizeof(int32_t); // FIX
            if (count > 0) {
                int data_size = count * sizeof(UserSearchInfo);
                if (data_size <= header->payload_size - bytes_processed) {
                    UserSearchInfo *results = (UserSearchInfo *) malloc(data_size);
                    if (results && recv_all(g_socket, results, data_size) > 0) {
                        bytes_processed += data_size; // FIX
                        for (int i = 0; i < count; i++) {
                            results[i].name[MAX_NAME_LEN - 1] = '\0';
                            results[i].email[MAX_EMAIL_LEN - 1] = '\0';
                        }
                        if (g_callbacks.on_search_result)
                            g_callbacks.on_search_result(count, results);
                    }
                    if (results)
                        free(results);
                }
            } else {
                if (g_callbacks.on_search_result)
                    g_callbacks.on_search_result(0, NULL);
            }
        }
    }

        // 8. MSG SENT ACK
    else if (header->command_type == CMD_SEND_MESSAGE_RESP) {
        ChatPayload msg;
        if (recv_all(g_socket, &msg, sizeof(ChatPayload)) > 0) {
            bytes_processed += sizeof(ChatPayload); // FIX
            if (header->status_code == STATUS_SUCCESS) {
                if (g_callbacks.on_msg_sent)
                    g_callbacks.on_msg_sent(header->request_id, msg.message_id, msg.created_at);
            }
        }
    }

        // 9. MSG DELIVERED
    else if (header->command_type == CMD_NOTIFY_MSG_DELIVERED) {
        MsgDeliveredPayload payload;
        if (recv_all(g_socket, &payload, sizeof(MsgDeliveredPayload)) > 0) {
            bytes_processed += sizeof(MsgDeliveredPayload); // FIX
            if (g_callbacks.on_msg_delivered)
                g_callbacks.on_msg_delivered(payload.message_id);
        }
    }

        // 10. PENDING REQUESTS RESP
    else if (header->command_type == CMD_GET_PENDING_REQS_RESP) {
        int32_t count = 0;
        if (recv_all(g_socket, &count, sizeof(int32_t)) > 0) {
            bytes_processed += sizeof(int32_t); // FIX
            if (count > 0) {
                int data_size = count * sizeof(PendingReqInfo);
                PendingReqInfo *list = (PendingReqInfo *) malloc(data_size);
                if (list && recv_all(g_socket, list, data_size) > 0) {
                    bytes_processed += data_size; // FIX
                    if (g_callbacks.on_pending_list)
                        g_callbacks.on_pending_list(count, list);
                }
                if (list)
                    free(list);
            } else {
                if (g_callbacks.on_pending_list)
                    g_callbacks.on_pending_list(0, NULL);
            }
        }
    }

        // 11. Các lệnh chỉ có Header hoặc status (không payload hoặc payload rỗng)
    else if (header->command_type == CMD_SEND_FRIEND_REQ_RESP ||
             header->command_type == CMD_RESPOND_FRIEND_REQ_RESP ||
             header->command_type == CMD_UNFRIEND_RESP ||
             header->command_type == CMD_ADD_MEMBER_RESP ||
             header->command_type == CMD_LEAVE_GROUP_RESP ||
             header->command_type == CMD_REMOVE_MEMBER_RESP ||
             header->command_type == CMD_DISSOLVE_GROUP_RESP ||
             header->command_type == CMD_RECALL_MESSAGE_RESP ||
             header->command_type == CMD_REACT_MESSAGE_RESP) {
        if (g_callbacks.on_req_response)
            g_callbacks.on_req_response(header->command_type, header->status_code);
    } else if (header->command_type == CMD_FETCH_OFFLINE_MSGS_RESP) {
        if (header->payload_size > 0)
            discard_payload(g_socket, header->payload_size);
        LOGI("Offline messages fetch started.");
    } else if (header->command_type == CMD_GET_HISTORY_RESP) {
        int32_t count = 0;
        if (recv_all(g_socket, &count, sizeof(int32_t)) <= 0)
            return;

        LOGI("Processing History: %d messages", count);
        ChatPayload *msgs = NULL;
        if (count > 0) {
            int data_size = count * sizeof(ChatPayload);
            msgs = (ChatPayload *) malloc(data_size);

            if (msgs) {
                memset(msgs, 0, data_size);

                if (recv_all(g_socket, msgs, data_size) > 0) {
                    if (g_callbacks.on_history_received) {
                        g_callbacks.on_history_received(count, msgs);
                    }
                }
            } else {
                if (g_callbacks.on_history_received) {
                    g_callbacks.on_history_received(0, NULL);
                }
            }
        } else {
            if (g_callbacks.on_history_received) {
                g_callbacks.on_history_received(0, NULL);
            }
        }
        if (msgs)
            free(msgs);
        return;
    } else if (header->command_type == CMD_CREATE_GROUP_RESP ||
               header->command_type == CMD_NOTIFY_GROUP_CREATED) {
        if (header->status_code == STATUS_SUCCESS) {
            CreateGroupRespPayload resp;
            if (recv_all(g_socket, &resp, sizeof(resp)) > 0) {
                bytes_processed += sizeof(resp);

                LOGI("Group Event: %s (ID: %d)", resp.group_name, resp.group_id);
                if (g_callbacks.on_group_created) {
                    g_callbacks.on_group_created(resp.group_id, resp.group_name);
                }
            }
        } else {
            LOGE("Group Action Failed with status: %d", header->status_code);

            if (g_callbacks.on_req_response) {
                g_callbacks.on_req_response(header->command_type, header->status_code);
            }
        }
    } else if (header->command_type == CMD_NOTIFY_MEMBERS_ADDED) {
        int min_size = sizeof(AddGroupMemberPayload);
        if (header->payload_size >= min_size) {
            // Đọc phần Struct Header
            AddGroupMemberPayload info;
            if (recv_all(g_socket, &info, min_size) > 0) {
                bytes_processed += min_size;

                // Phần mảng int phía sau
                int array_size = info.count * sizeof(int32_t);

                // Kiểm tra an toàn bộ nhớ
                if (array_size > 0 && (bytes_processed + array_size <= header->payload_size)) {
                    int32_t *new_ids = (int32_t *) malloc(array_size);
                    if (new_ids) {
                        if (recv_all(g_socket, new_ids, array_size) > 0) {
                            bytes_processed += array_size;
                            if (g_callbacks.on_group_members_added) {
                                g_callbacks.on_group_members_added(info.group_id,
                                                                   info.added_by_name, info.count,
                                                                   new_ids);
                            }
                        }
                        free(new_ids);
                    }
                }
            }
        }
    } else if (header->command_type == CMD_NOTIFY_MEMBER_LEFT) {
        MemberLeftNotifyPayload notify;
        if (recv_all(g_socket, &notify, sizeof(notify)) > 0) {
            bytes_processed += sizeof(notify);
            if (g_callbacks.on_member_left) {
                g_callbacks.on_member_left(notify.group_id, notify.member_id, notify.member_name);
            }
        }
    } else if (header->command_type == CMD_GET_GROUP_LIST_RESP) {
        int32_t count = 0;
        if (recv_all(g_socket, &count, sizeof(int32_t)) > 0) {
            bytes_processed += sizeof(int32_t);
            if (count > 0) {
                int data_size = count * sizeof(GroupInfoPayload);
                // Check overflow
                if (data_size <= header->payload_size - bytes_processed) {
                    GroupInfoPayload *groups = (GroupInfoPayload *) malloc(data_size);
                    if (recv_all(g_socket, groups, data_size) > 0) {
                        bytes_processed += data_size;
                        if (g_callbacks.on_group_list)
                            g_callbacks.on_group_list(count, groups);
                    }
                    free(groups);
                }
            } else {
                if (g_callbacks.on_group_list)
                    g_callbacks.on_group_list(0, NULL);
            }
        }
    } else if (header->command_type == CMD_NOTIFY_MEMBER_REMOVED) {
        MemberRemovedNotifyPayload notify;
        if (recv_all(g_socket, &notify, sizeof(notify)) > 0) {
            bytes_processed += sizeof(notify);
            if (g_callbacks.on_member_removed) {
                g_callbacks.on_member_removed(notify.group_id, notify.member_id, notify.member_name,
                                              notify.admin_id, notify.admin_name);
            }
        }
    } else if (header->command_type == CMD_NOTIFY_GROUP_DISSOLVED) {
        GroupDissolvedNotifyPayload notify;
        if (recv_all(g_socket, &notify, sizeof(notify)) > 0) {
            bytes_processed += sizeof(notify);
            if (g_callbacks.on_group_dissolved) {
                g_callbacks.on_group_dissolved(notify.group_id);
            }
        }
    } else if (header->command_type == CMD_GET_GROUP_MEMBERS_RESP) {
        // 1. Nếu Server trả về lỗi (Status != 0) -> Gọi callback báo lỗi
        if (header->status_code != STATUS_SUCCESS) {
            if (g_callbacks.on_req_response)
                g_callbacks.on_req_response(header->command_type, header->status_code);
        }

        // 2. Nếu Success -> Parse dữ liệu
        // Cấu trúc mong đợi: [GroupID (4)] + [Count (4)] + [Data...]
        int min_size = sizeof(int32_t) + sizeof(int32_t);

        if (header->payload_size >= min_size) {
            int32_t resp_group_id = 0;
            int32_t count = 0;

            // 1. Đọc Group ID
            recv_all(g_socket, &resp_group_id, sizeof(int32_t));
            bytes_processed += sizeof(int32_t);

            // 2. Đọc Count
            recv_all(g_socket, &count, sizeof(int32_t));
            bytes_processed += sizeof(int32_t);

            if (count > 0) {
                int data_size = count * sizeof(GroupMemberInfo);
                // Check overflow
                if (data_size <= header->payload_size - bytes_processed) {
                    GroupMemberInfo *members = (GroupMemberInfo *) malloc(data_size);
                    if (recv_all(g_socket, members, data_size) > 0) {
                        bytes_processed += data_size;

                        if (g_callbacks.on_group_members_received) {
                            // Truyền resp_group_id lấy từ server lên UI
                            g_callbacks.on_group_members_received(resp_group_id, count, members);
                        }
                    }
                    free(members);
                }
            } else {
                // Group rỗng hoặc lỗi
                if (g_callbacks.on_group_members_received) {
                    g_callbacks.on_group_members_received(resp_group_id, 0, NULL);
                }
            }
        }
    } else if (header->command_type == CMD_NOTIFY_UPDATE_MSG) {
        InteractionPayload notify;
        if (recv_all(g_socket, &notify, sizeof(notify)) > 0) {
            bytes_processed += sizeof(notify);
            if (g_callbacks.on_message_update) {
                g_callbacks.on_message_update(notify.message_id, notify.action_type,
                                              notify.reaction_code, notify.reactor_id);
            }
        }
    } else if (header->command_type == CMD_NOTIFY_UPDATE_MSG) {
        InteractionPayload notify;
        if (recv_all(g_socket, &notify, sizeof(notify)) > 0) {
            bytes_processed += sizeof(notify);
            if (g_callbacks.on_message_update) {
                g_callbacks.on_message_update(notify.message_id, notify.action_type,
                                              notify.reaction_code, notify.reactor_id);
            }
        }
    } else {
        LOGW("Unhandled Packet Type: %d", header->command_type);
    }

    // DỌN DẸP PAYLOAD THỪA 
    int remaining = header->payload_size - bytes_processed;
    if (remaining > 0) {
        // Chỉ khi bytes_processed đã được cập nhật đúng thì remaining mới đúng
        // Nếu không cập nhật bytes_processed, remaining = full size -> đọc lẹm vào gói sau -> DESYNC
        LOGW("Packet %d: Discarding %d bytes (Size: %d, Processed: %d)",
             header->command_type, remaining, header->payload_size, bytes_processed);
        discard_data(g_socket, remaining);
    }
}

// --- THREAD LOOP ---
static void *read_thread_func(void *arg) {
    PacketHeader header;
    LOGI("=== READ THREAD STARTED ===");

    if (g_socket != -1) {
        set_socket_timeout(g_socket, 0);
        LOGI("Native Socket timeout cleared for long-polling.");
    }

    while (g_is_running)
    {
        if (g_socket == -1)
        {
            g_is_running = 0;
            break;
        }
        int n = recv_all(g_socket, &header, sizeof(PacketHeader));
        if (n <= 0) {
            LOGE("Server disconnected (recv=%d)", n);
            g_is_running = 0;
            if (g_callbacks.on_disconnect)
                g_callbacks.on_disconnect("Connection Lost");
            break;
        }
        handle_incoming_packet(&header);
    }
    g_read_thread = 0;
    LOGI("=== READ THREAD EXITED ===");
    return NULL;
}

static void *heartbeat_thread_func(void *arg) {
    while (g_is_running) {
        // Ngủ trước khi gửi (Interval)
        sleep(HEARTBEAT_INTERVAL_SEC);

        if (!g_is_running || g_socket == -1) break;

        // Gửi Ping (Gói tin rỗng, chỉ có Header CMD_HEARTBEAT)
        // Dùng mutex để tránh đánh nhau với luồng chính
        pthread_mutex_lock(&g_send_mutex);

        PacketHeader header;
        memset(&header, 0, sizeof(header));
        header.version = SERVER_PROTOCOL_VERSION;
        header.command_type = CMD_HEARTBEAT;
        header.timestamp = get_timestamp();

        int res = send(g_socket, &header, sizeof(PacketHeader), 0);

        pthread_mutex_unlock(&g_send_mutex);

        if (res < 0) {
            LOGE("Heartbeat send failed. Connection likely dead.");
            break;
        }
    }
    return NULL;
}

void start_reader_thread(NativeCallbacks callbacks) {
    g_callbacks = callbacks;

    // KIỂM TRA: Nếu luồng đang chạy thì không tạo thêm bất cứ cái gì nữa
    if (g_is_running && (g_read_thread != 0 || g_hb_thread != 0)) {
        LOGW("Threads are already running. Skipping creation.");
        return;
    }

    g_is_running = 1;

    // Tạo luồng đọc
    if (pthread_create(&g_read_thread, NULL, read_thread_func, NULL) != 0) {
        LOGE("Failed to create reader thread");
        g_is_running = 0;
        return;
    }

    // Tạo luồng Heartbeat
    if (pthread_create(&g_hb_thread, NULL, heartbeat_thread_func, NULL) != 0) {
        LOGE("Failed to create heartbeat thread");
    }

    LOGI("✅ All native threads started successfully.");
}