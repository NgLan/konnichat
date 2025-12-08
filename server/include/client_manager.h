// File: include/client_manager.h
#ifndef CLIENT_MANAGER_H
#define CLIENT_MANAGER_H

#include <pthread.h>

typedef struct {
    int user_id;
    int socket;
} OnlineUser;

// Khởi tạo mảng quản lý
void client_manager_init();

// Thêm user vào danh sách online (khi đăng nhập thành công)
void add_online_user(int user_id, int socket);

// Xóa user khỏi danh sách (khi disconnect)
void remove_online_user(int socket);

// Tìm socket dựa trên user_id (Dùng cho Task 8 để bắn thông báo)
int get_socket_by_user_id(int user_id);

#endif