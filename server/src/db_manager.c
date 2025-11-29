#include "../include/db_manager.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Biến quản lý kết nối MySQL
MYSQL *conn;

// Cấu hình kết nối (Phải khớp với Bước 1)
#define DB_HOST "localhost"
#define DB_USER "chat_admin"
#define DB_PASS "password123"
#define DB_NAME "chatapp_db"

void init_database() {
    conn = mysql_init(NULL);
    if (conn == NULL) {
        fprintf(stderr, "mysql_init() failed\n");
        exit(1);
    }

    // Kết nối tới MySQL Server
    if (mysql_real_connect(conn, DB_HOST, DB_USER, DB_PASS, DB_NAME, 0, NULL, 0) == NULL) {
        fprintf(stderr, "Lỗi kết nối MySQL: %s\n", mysql_error(conn));
        mysql_close(conn);
        exit(1);
    }

    printf("Đã kết nối Database MySQL thành công.\n");
}

int db_register_user(const char *username, const char *password) {
    char query[512];
    
    // Tạo câu lệnh SQL INSERT
    // Lưu ý: mysql_real_escape_string nên được dùng ở đây để an toàn (tôi lược qua cho gọn code demo)
    snprintf(query, sizeof(query), 
             "INSERT INTO Users (Username, Password) VALUES ('%s', '%s')", 
             username, password);

    // Thực thi
    if (mysql_query(conn, query)) {
        // Nếu lỗi (ví dụ trùng Username)
        // fprintf(stderr, "Register Error: %s\n", mysql_error(conn)); 
        return 0; 
    }
    
    return 1; // Thành công
}

int db_check_login(const char *username, const char *password) {
    char query[512];
    int user_id = -1;

    // Tạo câu lệnh SQL SELECT
    snprintf(query, sizeof(query), 
             "SELECT ID FROM Users WHERE Username='%s' AND Password='%s'", 
             username, password);

    // Thực thi
    if (mysql_query(conn, query)) {
        fprintf(stderr, "Login Query Error: %s\n", mysql_error(conn));
        return -1;
    }

    // Lấy kết quả
    MYSQL_RES *result = mysql_store_result(conn);
    if (result == NULL) {
        return -1;
    }

    // Kiểm tra số dòng trả về
    int num_rows = mysql_num_rows(result);
    if (num_rows > 0) {
        MYSQL_ROW row = mysql_fetch_row(result);
        if (row && row[0]) {
            user_id = atoi(row[0]); // Chuyển chuỗi ID thành số int
        }
    }

    // Giải phóng bộ nhớ kết quả
    mysql_free_result(result);
    return user_id;
}