#ifndef SERVER_H
#define SERVER_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <pthread.h> 

// Định nghĩa các loại lệnh (Command Types)
typedef enum {
    CMD_LOGIN = 1,
    CMD_REGISTER = 2,
    CMD_CHAT_SINGLE = 3,
    CMD_CHAT_GROUP = 4,
    CMD_GET_FRIEND_LIST = 6,

   // --- TASK 8, 9, 10 ---
    CMD_SEND_FRIEND_REQ = 7,      // Client gửi yêu cầu kết bạn
    CMD_RESPOND_FRIEND_REQ = 8,   // Client phản hồi (Đồng ý/Từ chối)
    CMD_GET_PENDING_REQS = 9,     // Client lấy danh sách lời mời đang chờ
    CMD_UNFRIEND = 10,            // Client hủy kết bạn

    CMD_SEARCH_USERS = 11,

    // --- REAL-TIME NOTIFICATIONS (Server -> Client) ---
    CMD_NOTIFY_FRIEND_REQ = 50,   // Server báo có lời mời kết bạn mới
    CMD_NOTIFY_REQ_ACCEPTED = 51, // Server báo lời mời đã được chấp nhận

    CMD_RESPONSE = 99 // Server phản hồi kết quả
} CommandType;

// Cấu trúc gói tin (Packet Structure)
// __attribute__((packed)) giúp loại bỏ padding, giảm dung lượng và dễ parse

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
    int command_type;       // 4 bytes: Loại lệnh (CommandType)
    int payload_size;       // 4 bytes: Độ dài của phần dữ liệu đi kèm
} PacketHeader;

// Ví dụ về payload cho Login (dùng cho CMD_LOGIN)
typedef struct __attribute__((packed)) {
    char email[256];
    char password[32];
} LoginPayload;

// Ví dụ về payload cho Chat (dùng cho CMD_CHAT_SINGLE)
typedef struct __attribute__((packed)) {
    int sender_id;          // ID người gửi
    int receiver_id;        // ID người nhận
    char message[0];        // Mảng động (flexible array member) chứa nội dung tin nhắn
} ChatPayload;

// Task 8: Gửi lời mời
typedef struct __attribute__((packed)) {
    int sender_id;
    int receiver_id; // ID người mình muốn kết bạn
} FriendReqPayload;

// Task 9: Phản hồi lời mời
typedef struct __attribute__((packed)) {
    int request_id;  // ID của dòng trong bảng FriendRequests
    int is_accepted; // 1: Đồng ý, 0: Từ chối
} RespondReqPayload;

// Task 9: Thông tin lời mời hiển thị cho User
typedef struct __attribute__((packed)) {
    int request_id;
    int sender_id;
    char sender_name[50];
    // Có thể thêm timestamp nếu cần
} PendingReqInfo;

// Task 10: Hủy kết bạn
typedef struct __attribute__((packed)) {
    int user_id;
    int friend_id;
} UnfriendPayload;

// [MỚI] Payload gửi lên để tìm kiếm (chứa từ khóa)
typedef struct __attribute__((packed)) {
    char keyword[50]; // Tên người dùng muốn tìm
    int current_user_id; // Để loại trừ chính mình ra khỏi kết quả
} SearchReqPayload;

// [MỚI] Thông tin người dùng trả về (Kết quả tìm kiếm)
typedef struct __attribute__((packed)) {
    int id;
    char name[50];
    char email[50]; // Thêm email để dễ phân biệt nếu trùng tên
} UserSearchInfo;


#define PORT 8080           // Cổng server sẽ mở
#define BUFFER_SIZE 1024    // Kích thước bộ đệm tin nhắn

// Hàm xử lý logic của từng Client (Chạy trên luồng riêng)
void *handle_client(void *socket_desc);

#endif