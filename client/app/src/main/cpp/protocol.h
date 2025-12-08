#ifndef PROTOCOL_H
#define PROTOCOL_H

// --- 1. ĐỊNH NGHĨA COMMAND (Phải khớp 100% với Server) ---
typedef enum {
    CMD_LOGIN = 1,
    CMD_REGISTER = 2,
    CMD_CHAT_SINGLE = 3,
    CMD_CHAT_GROUP = 4,
    CMD_GET_FRIEND_LIST = 6,

    // --- TASK 8, 9, 10 ---
    CMD_SEND_FRIEND_REQ = 7,      // Gửi yêu cầu kết bạn
    CMD_RESPOND_FRIEND_REQ = 8,   // Phản hồi (Đồng ý/Từ chối)
    CMD_GET_PENDING_REQS = 9,     // Lấy danh sách lời mời đang chờ
    CMD_UNFRIEND = 10,            // Hủy kết bạn

    CMD_SEARCH_USERS = 11,
    // --- REAL-TIME ---
    CMD_NOTIFY_FRIEND_REQ = 50,   // Server báo có lời mời mới
    CMD_NOTIFY_REQ_ACCEPTED = 51, // Server báo lời mời đã được chấp nhận

    CMD_RESPONSE = 99             // Server phản hồi kết quả
} CommandType;

// --- 2. CẤU TRÚC HEADER ---
typedef struct __attribute__((packed)) {
    int command_type;
    int payload_size;
} PacketHeader;

// --- 3. CÁC PAYLOAD CŨ ---
typedef struct __attribute__((packed)) {
    int user_id;
} GetFriendListPayload;

typedef struct __attribute__((packed)) {
    int id;
    char name[50];
    int is_online;
} FriendInfo;

typedef struct __attribute__((packed)) {
    char email[256];
    char password[32];
} LoginPayload;

// --- 4. CÁC PAYLOAD MỚI (CHO TASK 8, 9, 10) ---

// Task 8: Gửi lời mời
typedef struct __attribute__((packed)) {
    int sender_id;
    int receiver_id;
} FriendReqPayload;

// Task 9A: Thông tin lời mời hiển thị cho User (Server trả về)
typedef struct __attribute__((packed)) {
    int request_id;
    int sender_id;
    char sender_name[50];
} PendingReqInfo;

// Task 9B: Phản hồi lời mời
typedef struct __attribute__((packed)) {
    int request_id;
    int is_accepted; // 1: Đồng ý, 0: Từ chối
} RespondReqPayload;

// Task 10: Hủy kết bạn
typedef struct __attribute__((packed)) {
    int user_id;
    int friend_id;
} UnfriendPayload;

typedef struct __attribute__((packed)) {
    char keyword[50];    // Tên người muốn tìm
    int current_user_id; // ID của người đang tìm (để loại trừ bản thân khỏi KQ)
} SearchReqPayload;

// Kết quả tìm kiếm trả về (1 item)
typedef struct __attribute__((packed)) {
    int id;
    char name[50];
    char email[50];
} UserSearchInfo;

#endif