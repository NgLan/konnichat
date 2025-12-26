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
static pthread_mutex_t g_client_mutex = PTHREAD_MUTEX_INITIALIZER;

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
    if (status < 0) return status;

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

    // 1. Chuẩn bị Request Payload
    GetFriendListReq req;
    req.offset = offset;
    req.limit = limit;

    // 2. Gửi Request
    if (send_request(CMD_GET_FRIEND_LIST, &req, sizeof(req)) < 0) {
        return ERR_NETWORK_SEND_FAILED;
    }

    // 3. Nhận Header
    PacketHeader resp;
    int err = recv_and_validate_header(&resp, CMD_GET_FRIEND_LIST_RESP);
    if (err < 0) return err;

    if (resp.status_code != STATUS_SUCCESS) {
        if (resp.payload_size > 0) discard_payload(g_socket, resp.payload_size);
        return resp.status_code; // Trả về lỗi Server (dương)
    }

    // 4. Xử lý Payload danh sách
    // Cấu trúc: [Count (4 bytes)] + [Array]
    int32_t count = 0;

    // Đọc Count trước
    if (recv_all(g_socket, &count, sizeof(int32_t)) < 0) {
        return ERR_NETWORK_RECV_FAILED;
    }

    LOGI("Friend List Resp: Count=%d", count);

    // Nếu Count = 0, xong
    if (count == 0) return 0;

    // Nếu Count > limit request -> Có vấn đề (Server trả về quá nhiều so với bộ nhớ Client chuẩn bị)
    if (count > limit) {
        LOGE("Server returned too many items (%d) vs buffer size (%d)", count, limit);
        // Đọc phần cho phép
        recv_all(g_socket, out_friends, limit * sizeof(UserInfoPayload));
        // Discard phần thừa
        discard_payload(g_socket, (count - limit) * sizeof(UserInfoPayload));
        return limit; // Chỉ trả về số lượng đã đọc
    }

    // Đọc Array Data
    int data_size = count * sizeof(UserInfoPayload);
    if (recv_all(g_socket, out_friends, data_size) < 0) {
        return ERR_NETWORK_RECV_FAILED;
    }

    return count;
}
