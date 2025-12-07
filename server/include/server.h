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

typedef enum {
    CMD_LOGIN = 1,
    CMD_REGISTER = 2,
    CMD_SEND_MESSAGE = 3,
    CMD_RECEIVE_MESSAGE = 4,
    CMD_FRIEND_REQ = 5,
    CMD_GET_FRIEND_LIST = 6,
    CMD_FETCH_OFFLINE_MSGS = 7, 
    CMD_GET_HISTORY = 8,
    CMD_RESPONSE = 99
} CommandType;

// --- HEADER ---
typedef struct __attribute__((packed)) {
    int command_type;
    int payload_size;
} PacketHeader;

// --- PAYLOADS ---

// 1. Payload chung cho các request chỉ cần gửi User ID
// (Dùng cho: GetFriendList, FetchOfflineMsgs...)
typedef struct __attribute__((packed)) {
    int user_id;
} UserIdPayload;

// 2. Payload Login/Register
typedef struct __attribute__((packed)) {
    char email[256];
    char password[32];
} LoginPayload;

// 3. Payload Chat (Gửi đi)
typedef struct __attribute__((packed)) {
    int sender_id;
    int receiver_id;
    char content[512];
} ChatPayload;

// --- RESPONSE DATA ---

// 1. Thông tin User (Trả về khi Login thành công để lưu vào DB)
typedef struct __attribute__((packed)) {
    int id;
    char email[256];
    char name[50];
} UserInfo;

// 2. Thông tin Bạn bè
typedef struct __attribute__((packed)) {
    int id;
    char name[50];
    int is_online;
} FriendInfo;

// 3. Thông tin Tin nhắn (Nhận về)
typedef struct __attribute__((packed)) {
    int message_id;
    int sender_id;
    char content[512];
    char timestamp[20];
} MessageInfo;

typedef struct __attribute__((packed)) {
    int user_id;
    int friend_id;
} HistoryPayload;

#define PORT 8080           // Cổng server sẽ mở
#define BUFFER_SIZE 1024    // Kích thước bộ đệm tin nhắn

// Hàm xử lý logic của từng Client (Chạy trên luồng riêng)
void *handle_client(void *socket_desc);

#endif