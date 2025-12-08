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

// Hàm gửi lời mời kết bạn
int db_send_friend_request(int sender_id, int receiver_id);

// Hàm lấy tên người dùng từ user_id
void db_get_user_name(int user_id, char *name_buffer);

// Hàm lấy danh sách lời mời kết bạn đang chờ
int db_get_pending_requests(int user_id, PendingReqInfo *list_out, int max_count);

// Hàm phản hồi lời mời kết bạn
// Sửa prototype: Thêm tham số current_user_id và con trỏ sender_id_out
int db_respond_friend_request(int request_id, int current_user_id, int is_accepted, int *sender_id_out);
// Hàm hủy kết bạn
int db_remove_friend(int user_id, int friend_id);

// [MỚI] Hàm tìm kiếm user theo tên (gần đúng)
// Trả về số lượng tìm thấy
int db_search_users(const char *keyword, int current_user_id, UserSearchInfo *list_out, int max_count);

#endif