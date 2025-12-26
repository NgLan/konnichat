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

static int g_socket = -1;
static int g_req_id = 0;
static int g_is_running = 0;
static pthread_t g_read_thread;
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
    int n = recv_all(g_socket, header, sizeof(PacketHeader));
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

int client_init(const char *ip, int port) {
    if (g_socket != -1) return 0; // Đã connect rồi

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
        return -2;
    }

    if (connect(g_socket, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
        LOGE("Connection Failed");
        close(g_socket);
        g_socket = -1;
        return -3;
    }

    LOGI("Connected to Server %s:%d", ip, port);
    return 0;
}

void client_close() {
    if (g_socket != -1) {
        close(g_socket);
        g_socket = -1;
        LOGI("Connection closed");
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
int client_get_friends(int offset, int limit, UserInfoPayload *out_friends) {
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

// --- LOGIC XỬ LÝ GÓI TIN ĐẾN ---
static void handle_incoming_packet(PacketHeader *header) {
    // 1. Xử lý FRIEND LIST
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

    // 2. Xử lý TIN NHẮN ĐẾN
    else if (header->command_type == CMD_RECEIVE_MESSAGE) {
        ChatPayload msg;
        // Đọc payload tin nhắn
        if (recv_all(g_socket, &msg, sizeof(ChatPayload)) > 0) {
            if (g_callbacks.on_message) {
                g_callbacks.on_message(&msg);
            }
        }
    }

    // 3. Xử lý STATUS (Online/Offline)
    else if (header->command_type == CMD_NOTIFY_STATUS) {
        StatusNotifyPayload notify;
        if (recv_all(g_socket, &notify, sizeof(StatusNotifyPayload)) > 0) {
            if (g_callbacks.on_status_change) {
                g_callbacks.on_status_change(notify.friend_id, notify.is_online);
            }
        }
    }

    // ... Các case khác ...
    else {
        LOGW("Unhandled Packet Type: %d. Size: %d", header->command_type, header->payload_size);
        discard_payload(g_socket, header->payload_size);
    }
}

// --- THREAD LOOP ---
static void* read_thread_func(void* arg) {
    PacketHeader header;

    while (g_is_running) {
        if (g_socket == -1) break;

        // Blocking Read Header
        int n = recv_all(g_socket, &header, sizeof(PacketHeader));

        if (n <= 0) {
            LOGE("Server disconnected or Read Error.");
            g_is_running = 0;
            if (g_callbacks.on_disconnect) g_callbacks.on_disconnect("Connection Lost");
            break;
        }

        // Có gói tin -> Xử lý
        handle_incoming_packet(&header);
    }
    return NULL;
}

// --- PUBLIC FUNCTIONS ---
void start_reader_thread(NativeCallbacks callbacks) {
    if (g_is_running) return;

    g_callbacks = callbacks; // Lưu callback
    g_is_running = 1;

    // Tạo thread riêng
    pthread_create(&g_read_thread, NULL, read_thread_func, NULL);
    LOGI("Reader Thread Started.");
}
