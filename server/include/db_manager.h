#ifndef DB_MANAGER_H
#define DB_MANAGER_H

#include <sqlite3.h>

// Hàm khởi tạo DB (Tạo bảng nếu chưa có)
void init_database();

// Hàm đăng nhập: Trả về UserID nếu thành công, -1 nếu thất bại
int db_check_login(const char *username, const char *password);

// Hàm đăng ký
int db_register_user(const char *username, const char *password);

#endif