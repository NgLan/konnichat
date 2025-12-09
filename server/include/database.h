#ifndef DATABASE_H
#define DATABASE_H

#include <mysql/mysql.h>

extern MYSQL *conn;

// Hàm khởi tạo DB (Tạo bảng nếu chưa có)
void init_database();

#endif
