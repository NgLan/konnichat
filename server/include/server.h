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
    CMD_FRIEND_REQ = 5,
    CMD_GET_FRIEND_LIST = 6,
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


#define PORT 8080           // Cổng server sẽ mở
#define BUFFER_SIZE 1024    // Kích thước bộ đệm tin nhắn

// Hàm xử lý logic của từng Client (Chạy trên luồng riêng)
void *handle_client(void *socket_desc);

#endif