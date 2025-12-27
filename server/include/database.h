#ifndef DATABASE_H
#define DATABASE_H

#include <mysql/mysql.h>
#include <pthread.h>

extern MYSQL *conn;
extern pthread_mutex_t db_mutex;

// Hàm khởi tạo DB (Tạo bảng nếu chưa có)
void init_database();

#endif
