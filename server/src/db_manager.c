#include "../include/db_manager.h"
#include "../include/dotenv.h" 
#include <stdio.h>
#include <stdlib.h>

MYSQL *conn;

void connect_database() {
    // 1. Gọi thư viện load file .env vào RAM
    env_load(".env");

    // 2. Lấy giá trị bằng hàm getenv()
    char *host = getenv("DB_HOST");
    char *user = getenv("DB_USER");
    char *pass = getenv("DB_PASS");
    char *name = getenv("DB_NAME");
    
    // Xử lý port (chuyển string sang int), mặc định 3306 nếu thiếu
    char *port_str = getenv("DB_PORT");
    int port = (port_str != NULL) ? atoi(port_str) : 3306;

    // Kiểm tra biến bắt buộc
    if (!host || !user || !pass || !name) {
        fprintf(stderr, "LỖI: Thiếu cấu hình DB trong file .env\n");
        exit(1);
    }

    conn = mysql_init(NULL);
    if (mysql_real_connect(conn, host, user, pass, name, port, NULL, 0) == NULL) {
        fprintf(stderr, "Lỗi kết nối MySQL: %s\n", mysql_error(conn));
        exit(1);
    }

    printf("Đã kết nối Database thành công (Host: %s)\n", host);
}

int db_register_user(const char *username, const char *password) {
    char query[1024];
    snprintf(query, sizeof(query), 
             "INSERT INTO Users (Username, Password) VALUES ('%s', '%s')", 
             username, password);
    if (mysql_query(conn, query)) return 0; 
    return 1;
}

int db_check_login(const char *username, const char *password) {
    char query[1024];
    int user_id = -1;
    snprintf(query, sizeof(query), 
             "SELECT ID FROM Users WHERE Username='%s' AND Password='%s'", 
             username, password);
    
    if (mysql_query(conn, query)) return -1;

    MYSQL_RES *result = mysql_store_result(conn);
    if (result) {
        if (mysql_num_rows(result) > 0) {
            MYSQL_ROW row = mysql_fetch_row(result);
            if (row && row[0]) user_id = atoi(row[0]);
        }
        mysql_free_result(result);
    }
    return user_id;
}
