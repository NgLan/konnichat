#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stdint.h> 

// --- CẤU HÌNH CHUNG ---
#define SERVER_PORT 8080
#define UDP_PORT 8888
#define PROTOCOL_VERSION 1

#define MAX_EMAIL_LEN 256
#define MAX_PASS_LEN 64
#define MAX_NAME_LEN 64
#define MAX_CONTENT_LEN 1024
#define MAX_GROUP_NAME 100

// --- DANH SÁCH LỆNH ---
typedef enum {
    // 0. System
    CMD_HEARTBEAT = 0,         // Ping/Pong giữ kết nối

    // 1. Auth & Account 
    CMD_REGISTER = 10,
    CMD_LOGIN = 11,
    CMD_LOGOUT = 12,

    // 2. Chat 1-1 
    CMD_SEND_MESSAGE = 20,     // Client gửi lên
    CMD_RECEIVE_MESSAGE = 21,  // Server đẩy về Client

    // 3. Chat Group 
    CMD_CREATE_GROUP = 30,
    CMD_ADD_MEMBER = 31,
    CMD_REMOVE_MEMBER = 32,
    CMD_LEAVE_GROUP = 33,
    CMD_SEND_GROUP_MSG = 34,
    CMD_RECEIVE_GROUP_MSG = 35,

    // 4. Friend Management 
    CMD_GET_FRIEND_LIST = 40,
    CMD_SEND_FRIEND_REQ = 41,
    CMD_RESPOND_FRIEND_REQ = 42, // Chấp nhận/Từ chối
    CMD_UNFRIEND = 43,
    CMD_NOTIFY_FRIEND_REQ = 44,  // Server báo có lời mời
    CMD_SEARCH_USERS = 45,       // Client tìm kiếm user
    CMD_GET_PENDING_REQS = 46,

    // 5. Advanced Features 
    CMD_GET_HISTORY = 50,
    CMD_FETCH_OFFLINE_MSGS = 51,
    CMD_RECALL_MESSAGE = 52,     // Thu hồi
    CMD_REACT_MESSAGE = 53,      // Thả tim, like...
    CMD_NOTIFY_UPDATE_MSG = 54,  // Server báo tin nhắn đã bị đổi (thu hồi/react)
    CMD_NOTIFY_REQ_ACCEPTED = 55,

    // 6. Notifications
    CMD_NOTIFY_STATUS = 60,      // Báo online/offline

    // 99. Generic Response
    CMD_RESPONSE = 99            // Gói phản hồi chung
} CommandType;

// --- MÃ TRẠNG THÁI ---
typedef enum {
    STATUS_SUCCESS = 0,
    STATUS_ERROR_UNKNOWN = 1,
    STATUS_ERROR_AUTH = 2,       
    STATUS_ERROR_USER_NOT_FOUND = 3,
    STATUS_ERROR_DB = 4,
    STATUS_ERROR_INVALID_PARAM = 5
} StatusCode;

// --- HEADER ---
// Tổng kích thước: 4*5 + 8 = 28 bytes
typedef struct __attribute__((packed)) {
    int32_t version;        // Phiên bản protocol 
    int32_t command_type;   // Loại lệnh (CommandType)
    int32_t payload_size;   // Kích thước phần dữ liệu đi kèm
    int32_t request_id;     // ID định danh request (Do Client sinh ra, Server trả lại y nguyên)
    int32_t status_code;    // Kết quả xử lý (StatusCode). Chỉ có ý nghĩa khi là gói phản hồi.
    uint64_t timestamp;     // Thời gian gửi
} PacketHeader;

// ================= PAYLOADS =================

// 1. Authentication (Login/Register)
typedef struct __attribute__((packed)) {
    char name[MAX_NAME_LEN];
    char email[MAX_EMAIL_LEN];
    char password[MAX_PASS_LEN];
} AuthPayload;

// 2. User Info (Dùng trong phản hồi Login, Search, FriendList)
typedef struct __attribute__((packed)) {
    int32_t user_id;
    char name[MAX_NAME_LEN];
    char email[MAX_EMAIL_LEN];
    int8_t is_online;       // 1: Online, 0: Offline
} UserInfoPayload;

// 3. Chat 1-1 
typedef struct __attribute__((packed)) {
    int32_t message_id;     // Server sinh ra (Gửi đi để 0, Nhận về sẽ có giá trị)
    int32_t sender_id;
    int32_t receiver_id;
    int32_t msg_type;       // 1: Text, 2: Image, 3: File...
    char content[MAX_CONTENT_LEN];
    uint64_t created_at;
} ChatPayload;

// 4. Chat Group
typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t sender_id;      // Người gửi
    char content[MAX_CONTENT_LEN];
    char sender_name[MAX_NAME_LEN]; // Kèm tên để hiển thị cho nhanh
} GroupMessagePayload;

// 5. Group Management
typedef struct __attribute__((packed)) {
    char group_name[MAX_GROUP_NAME];
    // Danh sách ID thành viên ban đầu (dạng chuỗi "1,5,9" hoặc gửi gói riêng)
    // Để đơn giản giai đoạn đầu, tạo nhóm xong mới add member sau.
} CreateGroupPayload;

// Search (Tìm kiếm)
typedef struct __attribute__((packed)) {
    char keyword[50];
} SearchReqPayload;

typedef struct __attribute__((packed)) {
    int32_t user_id;
    char name[64];
    char email[256];
} UserSearchInfo;

typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t target_user_id; // Người được thêm hoặc bị kick
} GroupActionPayload;

// 6. Friend Management
typedef struct __attribute__((packed)) {
    int32_t target_id;      // Người muốn kết bạn / unfriend
} FriendReqPayload;

// Pending Request (Lời mời đang chờ)
typedef struct __attribute__((packed)) {
    int32_t request_id;
    int32_t sender_id;
    char sender_name[64];
    uint64_t created_at; // Optional
} PendingReqInfo;

typedef struct __attribute__((packed)) {
    int32_t request_id;     // ID của lời mời kết bạn (trong DB)
    int8_t is_accepted;     // 1: Chấp nhận, 0: Từ chối
} FriendRespondPayload;

// 7. Interaction (Recall / Reaction)
typedef struct __attribute__((packed)) {
    int32_t message_id;     // ID tin nhắn cần tương tác
    int32_t group_id;       // 0 nếu là chat 1-1, >0 nếu là chat nhóm
    int32_t action_type;    // 1: Recall, 2: React
    int32_t reaction_code;  // 0: None, 1: Heart, 2: Haha... (Nếu là Recall thì bỏ qua)
} InteractionPayload;

// 8. Request by ID (Dùng chung cho nhiều việc: GetHistory, FetchOffline...)
typedef struct __attribute__((packed)) {
    int32_t target_id;      // FriendID hoặc GroupID
    int32_t offset;         // Vị trí bắt đầu lấy (Phân trang)
    int32_t limit;          // Số lượng lấy
} GetHistoryPayload;

// 9. Status Notification
typedef struct __attribute__((packed)) {
    int32_t friend_id;
    int8_t is_online;
} StatusNotifyPayload;

#endif
