#ifndef DATABASE_H
#define DATABASE_H

#include <mysql/mysql.h>

// Định nghĩa kích thước bể kết nối
#define POOL_SIZE 10

// Hàm khởi tạo toàn bộ Pool
void init_database();

// Dọn dẹp, đóng tất cả kết nối khi tắt server
void close_database();

// Lấy 1 connection rảnh từ Pool (Thread-safe)
MYSQL *db_get_conn();

// Trả connection về Pool sau khi dùng xong
void db_release_conn(MYSQL *conn);

#endif
