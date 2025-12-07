#ifndef DB_MANAGER_H
#define DB_MANAGER_H

#include <mysql/mysql.h>
#include "../include/server.h"

// Hàm khởi tạo DB (Tạo bảng nếu chưa có)
void init_database();

// Hàm đăng nhập: Trả về UserID nếu thành công, -1 nếu thất bại
int db_check_login(const char *username, const char *password);

// Hàm đăng ký
int db_register_user(const char *username, const char *password);

// Hàm lấy danh sách bạn bè
int db_get_friends(int user_id, FriendInfo *friends_out, int max_count);

// Các hàm liên quan đến tin nhắn
int db_save_message(int sender_id, int receiver_id, const char* content);
int db_get_pending_messages(int user_id, MessageInfo* messages_out, int max_count);
void db_mark_message_delivered(int message_id);

#endif