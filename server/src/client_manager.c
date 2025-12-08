// File: src/client_manager.c
#include "../include/client_manager.h"
#include <stdio.h>
#include <string.h>

#define MAX_CLIENTS 1000

static OnlineUser online_users[MAX_CLIENTS];
static pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

void client_manager_init() {
    memset(online_users, 0, sizeof(online_users));
}

void add_online_user(int user_id, int socket) {
    pthread_mutex_lock(&clients_mutex);
    
    // Xóa entry cũ nếu tồn tại
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (online_users[i].user_id == user_id) {
            online_users[i].user_id = 0;
            online_users[i].socket = 0;
        }
    }

    // Tìm chỗ trống để thêm mới
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (online_users[i].socket == 0) {
            online_users[i].user_id = user_id;
            online_users[i].socket = socket;
            break;
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

void remove_online_user(int socket) {
    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (online_users[i].socket == socket) {
            online_users[i].user_id = 0;
            online_users[i].socket = 0;
            break;
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

int get_socket_by_user_id(int user_id) {
    int target_socket = -1;
    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (online_users[i].user_id == user_id && online_users[i].socket != 0) {
            target_socket = online_users[i].socket;
            break;
        }
    }
    pthread_mutex_unlock(&clients_mutex);
    return target_socket;
}