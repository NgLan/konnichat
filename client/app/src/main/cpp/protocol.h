#ifndef PROTOCOL_H
#define PROTOCOL_H

// Định nghĩa các loại lệnh (Phải khớp 100% với Server)
typedef enum {
    CMD_LOGIN = 1,
    CMD_REGISTER = 2,
    CMD_CHAT_SINGLE = 3,
    CMD_CHAT_GROUP = 4,
    CMD_FRIEND_REQ = 5,
    CMD_GET_FRIEND_LIST = 6,
    CMD_RESPONSE = 99
} CommandType;

// Cấu trúc gói tin header
// __attribute__((packed)) rất quan trọng để khớp byte với C trên Linux
// Gửi yêu cầu lấy list friend
typedef struct __attribute__((packed)) {
    int user_id;
} GetFriendListPayload;

// Thông tin 1 người bạn trả về cho Client
typedef struct __attribute__((packed)) {
    int id;
    char name[50];
    int is_online; // 1: Online, 0: Offline
} FriendInfo;

typedef struct __attribute__((packed)) {
    int command_type;
    int payload_size;
} PacketHeader;

// Payload Login/Register
typedef struct __attribute__((packed)) {
    char email[256];
    char password[32];
} LoginPayload;

#endif