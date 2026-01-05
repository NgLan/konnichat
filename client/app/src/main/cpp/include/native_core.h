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
    // temp_req_id: ID tạm client sinh ra lúc gửi
    // server_msg_id: ID server cấp phát
    // server_time: Thời gian chuẩn server
    void (*on_msg_sent)(int temp_req_id, int server_msg_id, uint64_t server_time);
    void (*on_msg_delivered)(int server_msg_id);
    void (*on_status_change)(int friend_id, int is_online);
    void (*on_friend_req)(int req_id, int sender_id, const char* sender_name);
    void (*on_req_response)(int cmd, int status);
    void (*on_request_accepted)(UserInfoPayload* user);
    void (*on_unfriended)(int ex_friend_id);
    void (*on_search_result)(int count, UserSearchInfo *results);
    void (*on_pending_list)(int count, PendingReqInfo* list);
    void (*on_history_received)(int count, ChatPayload *messages);
    void (*on_group_created)(int group_id, const char* name);
    void (*on_group_members_added)(int group_id, const char* added_by, int count, int* new_member_ids);
    void (*on_member_left)(int group_id, int member_id, const char* member_name);
    void (*on_group_list)(int count, GroupInfoPayload* groups);
    void (*on_member_removed)(int group_id, int member_id, const char* member_name, int admin_id, const char* admin_name);
    void (*on_group_dissolved)(int group_id);
    void (*on_group_members_received)(int group_id, int count, GroupMemberInfo* members);
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

int client_send_friend_request(int target_id);

int client_respond_friend_req(int request_id, int is_accepted);

int client_unfriend(int target_id);

int client_search_users(const char *keyword, int offset, int limit);

int client_send_message(int sender_id, int receiver_id, const char *content, int request_id, const char* chat_type);

int client_fetch_offline_msgs();

int client_get_history(int target_id, int is_group, int offset, int limit);

int client_get_pending_requests();

int client_create_group(const char* name, int32_t* members, int member_count);

int client_add_group_members(int group_id, int32_t* member_ids, int count);

int client_leave_group(int group_id);

int client_get_group_list(int offset, int limit);

int client_kick_member(int group_id, int target_id);

int client_dissolve_group(int group_id);

int client_get_group_members(int group_id, int offset, int limit);

void client_logout();

#endif // NATIVE_CORE_H
