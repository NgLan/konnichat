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

static int g_socket = -1;
static int g_req_id = 0;
static int g_is_running = 0;
static pthread_t g_read_thread = 0;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER; // Khóa bảo vệ khi gửi
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
        if (n == -1) return -1;
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
        if (n <= 0) return n; // Error or Closed
        total += n;
        left -= n;
    }
    return total;
}

// Helper: Đọc bỏ payload rác (nếu có)
static void discard_payload(int sock, int size) {
    if (size <= 0) return;
    char buffer[1024];
    int remaining = size;
    while (remaining > 0) {
        int to_read = (remaining < sizeof(buffer)) ? remaining : sizeof(buffer);
        int n = recv(sock, buffer, to_read, 0);
        if (n <= 0) break;
        remaining -= n;
    }
    LOGW("Discarded %d bytes of garbage payload.", size - remaining);
}

// Helper: Gửi Header + Payload chung
static int send_request(int cmd, void *payload, int payload_size) {
    if (g_socket == -1) return -1;

    PacketHeader header;
    memset(&header, 0, sizeof(header));
    header.version = PROTOCOL_VERSION;
    header.command_type = cmd;
    header.payload_size = payload_size;
    header.request_id = ++g_req_id;
    header.timestamp = get_timestamp();

    // 1. Send Header
    if (send_all(g_socket, &header, sizeof(PacketHeader)) < 0) return -1;

    // 2. Send Payload (nếu có)
    if (payload_size > 0 && payload != NULL) {
        if (send_all(g_socket, payload, payload_size) < 0) return -1;
    }
    return header.request_id;
}

// Helper: Nhận Header và Validate Command
// Trả về: CLIENT_OK (0) hoặc ClientErrorCode (số âm)
static int recv_and_validate_header(PacketHeader *header, int expected_cmd) {
    if (g_socket == -1) return ERR_NETWORK_CONN_FAILED;

    // 1. Nhận Header
    LOGI("Dang cho doc Header...");
    int n = recv_all(g_socket, header, sizeof(PacketHeader));
    LOGI("Da doc duoc %d bytes, Cmd Type: %d", n, header->command_type );
    if (n <= 0) {
        LOGE("Socket Error or Closed while waiting for %s", cmd_to_string(expected_cmd));
        return ERR_NETWORK_RECV_FAILED;
    }

    LOGI("Received Header: Cmd=%s, Status=%s, Size=%d",
         cmd_to_string(header->command_type),
         status_to_string(header->status_code),
         header->payload_size);

    // 2. Validate Protocol
    if (header->command_type != expected_cmd) {
        LOGE("Protocol Mismatch! Expected %s but got %s",
             cmd_to_string(expected_cmd), cmd_to_string(header->command_type));

        // Nếu lệch gói, vẫn phải đọc bỏ payload của gói lệch này
        discard_payload(g_socket, header->payload_size);
        return ERR_PROTOCOL_MISMATCH;
    }

    return CLIENT_OK;
}

// Helper: Xóa khoảng trắng đầu và cuối chuỗi
static void trim_string(char *str) {
    if (!str) return;
    char *ptr = str;
    int len = strlen(ptr);

    // Trim trailing (cuối chuỗi)
    while (len > 0 && isspace(ptr[len - 1])) ptr[--len] = 0;

    // Trim leading (đầu chuỗi)
    while (*ptr && isspace(*ptr)) ptr++, len--;

    // Move về đầu buffer
    memmove(str, ptr, len + 1);
}

int client_init(const char *ip, int port) {
    if (g_socket != -1) {
        LOGW("client_init called but socket is already open (%d)", g_socket);
        return 0;
    }

    g_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (g_socket < 0) {
        LOGE("Socket creation failed");
        return -1;
    }

    struct sockaddr_in serv_addr;
    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);

    if (inet_pton(AF_INET, ip, &serv_addr.sin_addr) <= 0) {
        LOGE("Invalid address/ Address not supported");
        close(g_socket);
        g_socket = -1;
        return -2;
    }

    if (connect(g_socket, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
        LOGE("Connection Failed");
        close(g_socket);
        g_socket = -1;
        return -3;
    }

    g_is_running = 0;

    LOGI("Connected to Server %s:%d", ip, port);
    return 0;
}

void client_close() {
    g_is_running = 0; // 1. Báo hiệu dừng vòng lặp

    if (g_socket != -1) {
        // Shutdown giúp ngắt recv() đang bị block
        shutdown(g_socket, SHUT_RDWR);
        close(g_socket);
        g_socket = -1;
        LOGI("Connection closed");
    }

    // 2. Chờ thread đọc cũ chết hẳn để không ảnh hưởng lần connect sau
    if (g_read_thread != 0) {
        pthread_join(g_read_thread, NULL);
        g_read_thread = 0;
        LOGI("Reader thread stopped and joined.");
    }
}

int client_register(const char *name, const char *email, const char *password) {
    pthread_mutex_lock(&g_client_mutex);

    RegisterPayload payload;
    memset(&payload, 0, sizeof(payload));
    strncpy(payload.name, name, MAX_NAME_LEN - 1);
    strncpy(payload.email, email, MAX_EMAIL_LEN - 1);
    strncpy(payload.password, password, MAX_PASS_LEN - 1);

    // Gửi Request
    if (send_request(CMD_REGISTER, &payload, sizeof(payload)) < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }

    // Nhận Response
    PacketHeader resp;
    int status = recv_and_validate_header(&resp, CMD_REGISTER_RESP);
    if (status < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return status;
    }

    if (resp.payload_size > 0) discard_payload(g_socket, resp.payload_size);

    pthread_mutex_unlock(&g_client_mutex);
    return resp.status_code;
}

int client_login(const char *email, const char *password, UserInfoPayload *user_out) {
    pthread_mutex_lock(&g_client_mutex);

    LoginPayload payload;
    memset(&payload, 0, sizeof(payload));
    strncpy(payload.email, email, MAX_EMAIL_LEN - 1);
    strncpy(payload.password, password, MAX_PASS_LEN - 1);

    // Gửi Request
    if (send_request(CMD_LOGIN, &payload, sizeof(payload)) < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }

    // Nhận Header
    PacketHeader resp;
    int status = recv_and_validate_header(&resp, CMD_LOGIN_RESP);
    if (status < 0) {
        pthread_mutex_unlock(&g_client_mutex);
        return status;
    }

    if (resp.status_code == STATUS_SUCCESS) {
        // Nếu thành công, Server sẽ gửi kèm UserInfoPayload
        if (resp.payload_size == sizeof(UserInfoPayload)) {
            if (recv_all(g_socket, user_out, sizeof(UserInfoPayload)) < 0) {
                pthread_mutex_unlock(&g_client_mutex);
                return ERR_NETWORK_RECV_FAILED;
            }

            pthread_mutex_unlock(&g_client_mutex);
            return STATUS_SUCCESS;
        } else {
            LOGE("Payload size mismatch! Expected %zu, got %d", sizeof(UserInfoPayload),
                 resp.payload_size);
            discard_payload(g_socket, resp.payload_size);
            pthread_mutex_unlock(&g_client_mutex);
            return ERR_PROTOCOL_SIZE_ERR;
        }
    } else {
        // Nếu thất bại, đọc bỏ payload rác (nếu có)
        if (resp.payload_size > 0) {
            discard_payload(g_socket, resp.payload_size);
        }
        pthread_mutex_unlock(&g_client_mutex);
        return resp.status_code;
    }
}

/*
 * Hàm lấy danh sách bạn bè
 * out_friends: Danh sách bạn bè
 * offset: Bắt đầu từ 0
 * limit: Mặc định 20 - 100
 * return: Số lượng thực tế lấy được (hoặc mã lỗi âm)
 */
int client_get_friends(int offset, int limit) {
    if (limit < 1) limit = 20;
    if (limit > 100) limit = 100;

    // 1. Chuẩn bị Request Payload
    GetFriendListReq req;
    req.offset = offset;
    req.limit = limit;

    // 2. Gửi Request
    pthread_mutex_lock(&g_send_mutex);
    int res = send_request(CMD_GET_FRIEND_LIST, &req, sizeof(req));
    pthread_mutex_unlock(&g_send_mutex);

    return (res > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_send_friend_request(int target_id) {
    LOGI("=== client_send_friend_request: Sending request to target %d ===", target_id);

    // 1. Chuẩn bị Payload
    FriendReqPayload payload;
    payload.target_id = target_id;

    // 2. Khóa Mutex và Gửi
    pthread_mutex_lock(&g_send_mutex);
    int res = send_request(CMD_SEND_FRIEND_REQ, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    if (res > 0) {
        LOGI("Request sent successfully with req_id=%d", res);
        return CLIENT_OK;
    } else {
        LOGE("Failed to send request");
        return ERR_NETWORK_SEND_FAILED;
    }
}

/**
 * Hàm gửi phản hồi chấp nhận/từ chối yêu cầu kết bạn
 * @param request_id
 * @param is_accepted
 * @return 0 nếu thành công, mã lỗi âm nếu thất bại
 */
int client_respond_friend_req(int request_id, int is_accepted) {
    FriendRespondPayload payload;
    payload.request_id = request_id;
    payload.is_accepted = (int8_t)is_accepted;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_RESPOND_FRIEND_REQ, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    if (req_id < 0) return ERR_NETWORK_SEND_FAILED;
    return CLIENT_OK;
}

int client_unfriend(int target_id) {
    FriendReqPayload payload;
    payload.target_id = target_id;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_UNFRIEND, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    if (req_id < 0) {
        LOGE("Failed to send Unfriend request");
        return ERR_NETWORK_SEND_FAILED;
    }

    return CLIENT_OK;
}

int client_search_users(const char *keyword, int offset, int limit) {
    if (!keyword || strlen(keyword) == 0) return CLIENT_OK;

    SearchReqPayload payload;
    memset(&payload, 0, sizeof(payload));
    strncpy(payload.keyword, keyword, 49);

    trim_string(payload.keyword);

    // Nếu trim xong mà rỗng thì thôi không gửi
    if (strlen(payload.keyword) == 0) return CLIENT_OK;

    payload.offset = offset;
    payload.limit = limit;

    pthread_mutex_lock(&g_send_mutex);
    int req_id = send_request(CMD_SEARCH_USERS, &payload, sizeof(payload));
    pthread_mutex_unlock(&g_send_mutex);

    return (req_id > 0) ? CLIENT_OK : ERR_NETWORK_SEND_FAILED;
}

int client_send_message(int receiver_id, const char *content, int request_id) {
    ChatPayload payload;
    memset(&payload, 0, sizeof(payload));

    payload.receiver_id = receiver_id;
    // payload.sender_id sẽ được server tự điền dựa trên session -> không cần gửi
    payload.msg_type = 1;
    strncpy(payload.content, content, MAX_CONTENT_LEN - 1);

    // created_at gửi lên là client time (chỉ để tham khảo, server sẽ ghi đè)
    payload.created_at = get_timestamp();

    pthread_mutex_lock(&g_send_mutex);

    // Gửi request với ID do Client quản lý (request_id)
    PacketHeader header;
    memset(&header, 0, sizeof(header));
    header.version = PROTOCOL_VERSION;
    header.command_type = CMD_SEND_MESSAGE;
    header.payload_size = sizeof(ChatPayload);
    header.request_id = request_id;
    header.timestamp = get_timestamp();

    if (send_all(g_socket, &header, sizeof(PacketHeader)) < 0) {
        pthread_mutex_unlock(&g_send_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }
    if (send_all(g_socket, &payload, sizeof(ChatPayload)) < 0) {
        pthread_mutex_unlock(&g_send_mutex);
        return ERR_NETWORK_SEND_FAILED;
    }

    pthread_mutex_unlock(&g_send_mutex);
    return CLIENT_OK;
}

// --- LOGIC XỬ LÝ GÓI TIN ĐẾN ---
static void handle_incoming_packet(PacketHeader *header) {
    LOGI("=== handle_incoming_packet: CMD=%d, Status=%d, Size=%d ===", header->command_type, header->status_code, header->payload_size);

    // Xử lý FRIEND LIST
    if (header->command_type == CMD_GET_FRIEND_LIST_RESP) {
        int32_t count = 0;
        if (recv_all(g_socket, &count, sizeof(int32_t)) <= 0) return;

        if (count > 0) {
            int data_size = count * sizeof(UserInfoPayload);
            UserInfoPayload* friends = (UserInfoPayload*)malloc(data_size);
            if (recv_all(g_socket, friends, data_size) > 0) {
                // GỌI CALLBACK
                if (g_callbacks.on_friend_list) {
                    g_callbacks.on_friend_list(count, friends);
                }
            }
            free(friends);
        } else {
            if (g_callbacks.on_friend_list) g_callbacks.on_friend_list(0, NULL);
        }
    }

    // Xử lý STATUS (Online/Offline)
    else if (header->command_type == CMD_NOTIFY_STATUS) {
        StatusNotifyPayload notify;
        if (recv_all(g_socket, &notify, sizeof(StatusNotifyPayload)) > 0) {
            if (g_callbacks.on_status_change) {
                g_callbacks.on_status_change(notify.friend_id, notify.is_online);
            }
        }
    }

    // Xử lý THÔNG BÁO phản hồi của lệnh gửi kết bạn
    else if (header->command_type == CMD_SEND_FRIEND_REQ_RESP) {
        LOGI("Received CMD_SEND_FRIEND_REQ_RESP with status=%d", header->status_code);

        if (header->payload_size > 0) discard_payload(g_socket, header->payload_size);

        if (g_callbacks.on_req_response) {
            LOGI("Calling callback on_req_response(%d, %d)", header->command_type, header->status_code);
            g_callbacks.on_req_response(header->command_type, header->status_code);
        } else {
            LOGW("Callback on_req_response is NULL!");
        }
    }

    // Có lời mời kết bạn (Real-time)
    else if (header->command_type == CMD_NOTIFY_FRIEND_REQ) {
        PendingReqInfo info;
        // Đọc payload
        if (recv_all(g_socket, &info, sizeof(PendingReqInfo)) > 0) {
            LOGI("Received Friend Request from: %s (ID: %d)", info.sender_name, info.sender_id);

            // Gọi callback lên JNI
            if (g_callbacks.on_friend_req) {
                g_callbacks.on_friend_req(info.request_id, info.sender_id, info.sender_name);
            }
        }
    }

    else if (header->command_type == CMD_RESPOND_FRIEND_REQ_RESP) {
        LOGI("Received CMD_RESPOND_FRIEND_REQ_RESP with status=%d", header->status_code);
        if (header->payload_size > 0) discard_payload(g_socket, header->payload_size);

        if (g_callbacks.on_req_response) {
            LOGI("Calling callback on_req_response(Cmd: %d, Status: %d)", header->command_type, header->status_code);
            g_callbacks.on_req_response(header->command_type, header->status_code);
        } else {
            LOGW("Callback on_req_response is NULL!");
        }
    }

    else if (header->command_type == CMD_NOTIFY_REQ_ACCEPTED) {
        UserInfoPayload friend_info;
        if (recv_all(g_socket, &friend_info, sizeof(UserInfoPayload)) > 0) {
            LOGI("Notification: User %s (%d) accepted your friend request.", friend_info.name, friend_info.user_id);

            // Gọi callback lên JNI để hiện thông báo
            if (g_callbacks.on_request_accepted) {
                g_callbacks.on_request_accepted(&friend_info);
            }
        }
    }

    else if (header->command_type == CMD_UNFRIEND_RESP) {
        LOGI("Received CMD_UNFRIEND_RESP with status=%d", header->status_code);
        if (header->payload_size > 0) discard_payload(g_socket, header->payload_size);

        if (g_callbacks.on_req_response) {
            g_callbacks.on_req_response(header->command_type, header->status_code);
        }
    }

    else if (header->command_type == CMD_NOTIFY_UNFRIENDED) {
        FriendReqPayload payload;
        if (recv_all(g_socket, &payload, sizeof(FriendReqPayload)) > 0) {
            LOGI("Notification: You have been unfriended by User ID %d", payload.target_id);

            if (g_callbacks.on_unfriended) {
                // payload.target_id ở đây chính là ID người vừa unfriend mình
                g_callbacks.on_unfriended(payload.target_id);
            }
        }
    }

    else if (header->command_type == CMD_SEARCH_USERS_RESP) {
        int32_t count = 0;

        // 1. Đọc số lượng kết quả
        if (recv_all(g_socket, &count, sizeof(int32_t)) <= 0) return;

        UserSearchInfo *results = NULL;
        if (count > 0) {
            int data_size = count * sizeof(UserSearchInfo);
            results = (UserSearchInfo *)malloc(data_size);

            // 2. Đọc mảng dữ liệu
            if (results && recv_all(g_socket, results, data_size) > 0) {
                // Thành công -> Gọi callback
                if (g_callbacks.on_search_result) {
                    g_callbacks.on_search_result(count, results);
                }
            } else {
                LOGE("Failed to read search results body");
            }
        } else {
            // Không có kết quả
            if (g_callbacks.on_search_result) {
                g_callbacks.on_search_result(0, NULL);
            }
        }

        if (results) free(results);
    }

    // Case 1: Server phản hồi gửi tin thành công (Server đã nhận tin từ người gửi và lưu vào db) -> Update Room "Sent"
    else if (header->command_type == CMD_SEND_MESSAGE_RESP) {
        ChatPayload msg;
        if (recv_all(g_socket, &msg, sizeof(ChatPayload)) > 0) {
            if (header->status_code == STATUS_SUCCESS) {
                LOGI("Message Sent OK. TempID=%d -> ServerID=%d", header->request_id, msg.message_id);

                if (g_callbacks.on_msg_sent) {
                    // request_id: ID của message trong room (để tìm record update)
                    // msg.message_id: ID thật ở Server (để lưu lại)
                    // msg.created_at: Thời gian chuẩn Server
                    g_callbacks.on_msg_sent(header->request_id, msg.message_id, msg.created_at);
                }
            } else {
                LOGE("Send Message Failed. Status: %d", header->status_code);
            }
        }
    }

    // Case 2: Server truyền tin đến cho người nhận
    else if (header->command_type == CMD_RECEIVE_MESSAGE) {
        ChatPayload msg;
        if (recv_all(g_socket, &msg, sizeof(ChatPayload)) > 0) {
            LOGI("Incoming Msg from User %d: %s", msg.sender_id, msg.content);

            if (g_callbacks.on_message) {
                g_callbacks.on_message(&msg);
            }
        }
    }

    // Case 3: Gửi notify về cho người gửi là tin nhắn của bạn đã được gửi thành công
    else if (header->command_type == CMD_NOTIFY_MSG_DELIVERED) {
        MsgDeliveredPayload payload;
        if (recv_all(g_socket, &payload, sizeof(MsgDeliveredPayload)) > 0) {
            LOGI("Message %d Delivered to User %d", payload.message_id, payload.receiver_id);

            if (g_callbacks.on_msg_delivered) {
                g_callbacks.on_msg_delivered(payload.message_id);
            }
        }
    }

    else {
        LOGW("Unhandled Packet Type: %d. Size: %d", header->command_type, header->payload_size);
        discard_payload(g_socket, header->payload_size);
    }
}

// --- THREAD LOOP ---
static void* read_thread_func(void* arg) {
    PacketHeader header;
    LOGI("=== READ THREAD STARTED ===");

    while (g_is_running) {
        if (g_socket == -1) {
            g_is_running = 0; // Reset cờ nếu chưa connect
            LOGW("Socket closed, exiting read thread");
            break;
        }

        LOGI("Waiting for packet...");
        // Blocking Read Header
        int n = recv_all(g_socket, &header, sizeof(PacketHeader));

        if (n <= 0) {
            LOGE("Server disconnected or Read Error (recv returned %d)", n);
            g_is_running = 0;
            if (g_callbacks.on_disconnect) g_callbacks.on_disconnect("Connection Lost");
            break;
        }

        // Có gói tin -> Xử lý
        LOGI("Received packet header");
        handle_incoming_packet(&header);
    }

    LOGI("=== READ THREAD EXITED ===");
    return NULL;
}

// --- PUBLIC FUNCTIONS ---
void start_reader_thread(NativeCallbacks callbacks) {
    if (g_read_thread != 0) {
        LOGW("Reader thread already running.");
        return;
    }

    g_callbacks = callbacks; // Lưu callback
    g_is_running = 1;

    // Tạo thread riêng
    if (pthread_create(&g_read_thread, NULL, read_thread_func, NULL) != 0) {
        LOGE("Failed to create reader thread");
        g_read_thread = 0;
    } else {
        LOGI("Reader Thread Started.");
    }
}
