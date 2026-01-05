#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stdint.h> 

// --- CẤU HÌNH CHUNG ---
#define SERVER_PORT 8080
#define SERVER_PROTOCOL_VERSION 1

#define MAX_EMAIL_LEN 256
#define MAX_PASS_LEN 128
#define MAX_NAME_LEN 64
#define MAX_CONTENT_LEN 1024
#define MAX_GROUP_NAME 100
#define MAX_GROUP_MEMBERS 20
#define MAX_AVATAR_LEN 256 
#define MAX_ROLE_LEN 20

// --- DANH SÁCH LỆNH ---
typedef enum {
    // 0. System
    CMD_HEARTBEAT = 0,         // Ping/Pong giữ kết nối

    // 1. Auth  
    CMD_REGISTER            = 10,
    CMD_REGISTER_RESP       = 11, // Phản hồi đăng ký
    
    CMD_LOGIN               = 12,
    CMD_LOGIN_RESP          = 13, // Phản hồi đăng nhập

    CMD_LOGOUT              = 14,

    // 2. Chat 1-1 
    CMD_SEND_MESSAGE        = 20, // Client gửi tin
    CMD_SEND_MESSAGE_RESP   = 21, // Server xác nhận đã nhận (ACK)
    CMD_RECEIVE_MESSAGE     = 22, // Server đẩy tin nhắn tới người nhận 

    // 3. Chat Group 
    CMD_CREATE_GROUP        = 30,
    CMD_CREATE_GROUP_RESP   = 31,

    CMD_ADD_MEMBER          = 32,
    CMD_ADD_MEMBER_RESP     = 33,

    CMD_REMOVE_MEMBER       = 34,
    CMD_REMOVE_MEMBER_RESP  = 35,

    CMD_LEAVE_GROUP         = 36,
    CMD_LEAVE_GROUP_RESP    = 37,

    CMD_DISSOLVE_GROUP      = 38,
    CMD_DISSOLVE_GROUP_RESP = 39,

    CMD_GET_GROUP_LIST      = 50,
    CMD_GET_GROUP_LIST_RESP = 51,

    CMD_GET_GROUP_MEMBERS   = 52,
    CMD_GET_GROUP_MEMBERS_RESP = 53,

    // 4. Friend Management 
    CMD_GET_FRIEND_LIST     = 40,
    CMD_GET_FRIEND_LIST_RESP= 41,

    CMD_SEND_FRIEND_REQ     = 42,
    CMD_SEND_FRIEND_REQ_RESP= 43,

    CMD_RESPOND_FRIEND_REQ  = 44, // Chấp nhận/Từ chối yêu cầu kết bạn
    CMD_RESPOND_FRIEND_REQ_RESP = 45,

    CMD_UNFRIEND            = 46,
    CMD_UNFRIEND_RESP       = 47,
    
    CMD_GET_PENDING_REQS    = 48,
    CMD_GET_PENDING_REQS_RESP = 49,

    // 5. Search
    CMD_SEARCH_USERS        = 60,
    CMD_SEARCH_USERS_RESP   = 61,

    // 6. Advanced Features 
    CMD_GET_HISTORY         = 70,
    CMD_GET_HISTORY_RESP    = 71,

    CMD_FETCH_OFFLINE_MSGS  = 72,
    CMD_FETCH_OFFLINE_MSGS_RESP = 73,

    CMD_RECALL_MESSAGE = 74,     // Thu hồi
    CMD_REACT_MESSAGE = 75,      // Thả tim, like...

    // 6. Notifications
    CMD_NOTIFY_FRIEND_REQ   = 80,   // Có lời mời kết bạn mới
    CMD_NOTIFY_REQ_ACCEPTED = 81,   // Lời mời đã được chấp nhận
    CMD_NOTIFY_STATUS       = 82,   // Bạn bè on/off
    CMD_NOTIFY_UPDATE_MSG   = 83,   // Tin nhắn bị thu hồi/react
    CMD_NOTIFY_UNFRIENDED   = 84,   // Thông báo bị unfriend
    CMD_NOTIFY_MSG_DELIVERED = 85,  // Server báo cho người gửi (A) biết tin đã đến máy người nhận (B)

    CMD_NOTIFY_GROUP_CREATED = 86,
    CMD_NOTIFY_MEMBERS_ADDED = 87,
    CMD_NOTIFY_MEMBER_LEFT   = 88, 
    CMD_NOTIFY_MEMBER_REMOVED = 89,
    CMD_NOTIFY_GROUP_DISSOLVED = 90,

    // 99. Error 
    CMD_ERROR_UNKNOWN       = 999            
} CommandType;

// --- MÃ TRẠNG THÁI ---
typedef enum {
    STATUS_SUCCESS = 0,
    STATUS_ERROR_UNKNOWN = 1,
    STATUS_ERROR_AUTH = 2,       
    STATUS_ERROR_USER_NOT_FOUND = 3,
    STATUS_ERROR_DB = 4,
    STATUS_ERROR_INVALID_PARAM = 5,
    STATUS_ERROR_ALREADY_EXIST = 6,
    STATUS_ERROR_ALREADY_FRIEND = 7,  // Đã là bạn rồi
    STATUS_ERROR_REQ_PENDING = 8,      // Đã gửi rồi, đừng spam
    STATUS_ERROR_GROUP_FULL = 9,       // Nhóm đã đầy
    STATUS_ERROR_USER_NOT_IN_GROUP = 10,  // Người dùng không thuộc nhóm
    STATUS_ERROR_NOT_GROUP_ADMIN = 11,   // Người dùng không phải admin nhóm
    STATUS_ERROR_CANNOT_REMOVE_SELF = 12  // Không thể tự kick chính mình khỏi nhóm
} StatusCode;

// --- ĐỊNH NGHĨA TRẠNG THÁI QUAN HỆ ---
#define RELATION_NONE 0         // Người lạ
#define RELATION_FRIEND 1       // Bạn bè
#define RELATION_SENT 2         // Mình đã gửi lời mời (chờ duyệt)
#define RELATION_RECEIVED 3     // Họ đã gửi lời mời cho mình (chờ duyệt)

// --- ĐỊNH NGHĨA LOẠI TIN NHẮN ---
#define MSG_TYPE_TEXT 1
#define MSG_TYPE_IMAGE 2
#define MSG_TYPE_FILE 3
#define MSG_TYPE_SYSTEM 9 

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

// 1. Authentication
typedef struct __attribute__((packed)) {
    char name[MAX_NAME_LEN];
    char email[MAX_EMAIL_LEN];
    char password[MAX_PASS_LEN];
} RegisterPayload;

typedef struct __attribute__((packed)) {
    char email[MAX_EMAIL_LEN];
    char password[MAX_PASS_LEN];
} LoginPayload;

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
    char chat_type[16];     // 1: Private, 2: Group 
    char content[MAX_CONTENT_LEN];
    uint64_t created_at;    // Server Time
} ChatPayload;

typedef struct __attribute__((packed)) {
    int32_t message_id;
    int32_t receiver_id;    // Người đã nhận được tin
} MsgDeliveredPayload;

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
    int32_t member_count; 
} CreateGroupReqPayload;

typedef struct __attribute__((packed)) {
    int32_t group_id;
    char group_name[MAX_GROUP_NAME];
} CreateGroupRespPayload;

// Search (Tìm kiếm)
typedef struct __attribute__((packed)) {
    char keyword[MAX_NAME_LEN];
    int32_t offset; // Vị trí bắt đầu (cho phân trang)
    int32_t limit;  // Số lượng muốn lấy
} SearchReqPayload;

typedef struct __attribute__((packed)) {
    int32_t user_id;
    char name[MAX_NAME_LEN];
    char email[MAX_EMAIL_LEN];
    int32_t status;
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
    char sender_name[MAX_NAME_LEN];
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
    int32_t is_group;       // 0: Private Chat, 1: Group Chat
} GetHistoryPayload;

// 9. Status Notification
typedef struct __attribute__((packed)) {
    int32_t friend_id;
    int8_t is_online;
} StatusNotifyPayload;

// 10. Get Friend List Request
typedef struct __attribute__((packed)) {
    int32_t offset; // Bắt đầu từ 0
    int32_t limit;  // Mặc định 20 - 100
} GetFriendListReq;

/**
 * Payload dùng cho:
 * 1. Request: Client gửi lên Server (CMD_ADD_MEMBER)
 * 2. Notification: Server báo về Client (CMD_NOTIFY_MEMBERS_ADDED)
 *
 * Cấu trúc gói tin thực tế: 
 * [PacketHeader] + [AddGroupMemberPayload] + [int32_t member_ids[]]
 */
typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t count;          // Số lượng người được thêm
    int32_t added_by_user;  // Server điền ID người thêm (Client gửi lên để 0 cũng được)
    char added_by_name[MAX_NAME_LEN]; // Server điền tên người thêm
} AddGroupMemberPayload;

// Request rời nhóm (Client -> Server)
typedef struct __attribute__((packed)) {
    int32_t group_id;
} LeaveGroupReqPayload;

// Notify: Server báo cho người khác biết có thành viên rời nhóm
typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t member_id;              // ID người rời
    char member_name[MAX_NAME_LEN]; // Tên người rời 
} MemberLeftNotifyPayload;

// Request (Client -> Server)
typedef struct __attribute__((packed)) {
    int32_t offset; // Bắt đầu từ 0
    int32_t limit;  // Mặc định 20 - 100
} GetGroupListReq;

// Response Item (Server -> Client)
typedef struct __attribute__((packed)) {
    int32_t group_id;
    char group_name[MAX_GROUP_NAME]; 
    char avatar_url[MAX_AVATAR_LEN]; 
} GroupInfoPayload;

// Request (Client Admin -> Server)
typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t target_user_id; // Người bị kick
} RemoveMemberReqPayload;

// Notify (Server -> Client Members)
typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t member_id;              // ID người bị kick
    char member_name[MAX_NAME_LEN]; // Tên người bị kick (để hiển thị UI)
    int32_t admin_id;               // ID người thực hiện kick
    char admin_name[MAX_NAME_LEN];  // Tên admin
} MemberRemovedNotifyPayload;

// Request
typedef struct __attribute__((packed)) {
    int32_t group_id;
} DissolveGroupReqPayload;

// Notify
typedef struct __attribute__((packed)) {
    int32_t group_id;
    char group_name[MAX_GROUP_NAME];
} GroupDissolvedNotifyPayload;

// Request
typedef struct __attribute__((packed)) {
    int32_t group_id;
    int32_t offset; // Hỗ trợ phân trang nếu nhóm đông
    int32_t limit;
} GetGroupMembersReq;

// Payload Header cho Response (Server -> Client)
typedef struct __attribute__((packed)) {
    int32_t group_id;
} GroupMembersRespHeader;

// Response Item
typedef struct __attribute__((packed)) {
    int32_t user_id;
    char name[MAX_NAME_LEN];
    char email[MAX_EMAIL_LEN];
    int8_t is_online;           // 1: Online, 0: Offline
    char role[MAX_ROLE_LEN];    // "admin", "member"
} GroupMemberInfo;

#endif
