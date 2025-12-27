#ifndef NATIVE_CORE_H
#define NATIVE_CORE_H

#include "protocol.h"

// --- 1. LỖI TỪ CLIENT (SỐ ÂM) ---
// Định nghĩa enum cho các lỗi kỹ thuật xảy ra tại Client
typedef enum {
    // Không lỗi (Dùng cho các hàm internal)
    CLIENT_OK = 0,

    // Lỗi mạng (Socket, Timeout, Connect fail)
    ERR_NETWORK_CONN_FAILED = -100,
    ERR_NETWORK_SEND_FAILED = -101,
    ERR_NETWORK_RECV_FAILED = -102,

    // Lỗi giao thức (Packet sai header, sai command, sai size)
    ERR_PROTOCOL_MISMATCH = -200, // Mong đợi CMD_LOGIN_RESP lại nhận CMD_CHAT
    ERR_PROTOCOL_SIZE_ERR = -201, // Size quá lớn hoặc không khớp struct

    // Lỗi hệ thống Client
    ERR_INTERNAL_MEM = -300  // Malloc fail
} ClientErrorCode;

// --- 1. ĐỊNH NGHĨA CALLBACKS ---
typedef struct {
    void (*on_friend_list)(int count, UserInfoPayload* friends);
    void (*on_message)(ChatPayload* msg);
    void (*on_status_change)(int friend_id, int is_online);
    void (*on_disconnect)(const char* reason);
} NativeCallbacks;

// --- 2. CÁC HÀM QUẢN LÝ ---

// Khởi tạo kết nối Socket
int client_init(const char *ip, int port);

// Đóng kết nối
void client_close();

void start_reader_thread(NativeCallbacks callbacks);

// Các hàm này trả về:
// >= 0: StatusCode của Server (STATUS_SUCCESS, STATUS_ERROR_AUTH...)
// < 0 : ClientErrorCode (ERR_NETWORK_..., ERR_PROTOCOL_...)

// Gửi lệnh đăng ký
int client_register(const char *name, const char *email, const char *password);

// Gửi lệnh đăng nhập (Trả về STATUS code, nếu thành công thì điền dữ liệu vào user_out)
int client_login(const char *email, const char *password, UserInfoPayload *user_out);

int client_get_friends(int offset, int limit);

#endif // NATIVE_CORE_H
