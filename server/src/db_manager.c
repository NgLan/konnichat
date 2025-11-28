#include "../include/db_manager.h"
#include <stdio.h>
#include <stdlib.h>

sqlite3 *db;

void init_database() {
    int rc = sqlite3_open("chat_app.db", &db);
    if (rc) {
        fprintf(stderr, "Không thể mở database: %s\n", sqlite3_errmsg(db));
        exit(1);
    } else {
        printf("Đã kết nối Database SQLite thành công.\n");
    }

    // Tạo bảng Users nếu chưa có
    char *sql = "CREATE TABLE IF NOT EXISTS Users(" \
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," \
                "Username TEXT UNIQUE NOT NULL," \
                "Password TEXT NOT NULL);";

    char *errMsg = 0;
    rc = sqlite3_exec(db, sql, 0, 0, &errMsg);
    if (rc != SQLITE_OK) {
        fprintf(stderr, "Lỗi SQL: %s\n", errMsg);
        sqlite3_free(errMsg);
    }
}

int db_register_user(const char *username, const char *password) {
    sqlite3_stmt *stmt;
    const char *sql = "INSERT INTO Users (Username, Password) VALUES (?, ?);";
    
    // 1. Chuẩn bị câu lệnh (Prepare)
    if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) != SQLITE_OK) {
        return 0; 
    }
    
    // 2. Gán dữ liệu vào dấu ? (Bind)
    sqlite3_bind_text(stmt, 1, username, -1, SQLITE_STATIC);
    sqlite3_bind_text(stmt, 2, password, -1, SQLITE_STATIC);
    
    // 3. Thực thi (Step)
    int result = 0;
    if (sqlite3_step(stmt) == SQLITE_DONE) {
        result = 1; // Đăng ký thành công
    }
    
    // 4. Dọn dẹp
    sqlite3_finalize(stmt);
    return result;
}


// Trả về: UserID (>0) nếu đúng, -1 nếu sai
int db_check_login(const char *username, const char *password) {
    sqlite3_stmt *stmt;
    const char *sql = "SELECT ID FROM Users WHERE Username = ? AND Password = ?;";
    int user_id = -1;

    if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) != SQLITE_OK) {
        return -1;
    }

    sqlite3_bind_text(stmt, 1, username, -1, SQLITE_STATIC);
    sqlite3_bind_text(stmt, 2, password, -1, SQLITE_STATIC);

    if (sqlite3_step(stmt) == SQLITE_ROW) {
        user_id = sqlite3_column_int(stmt, 0); // Lấy cột đầu tiên (ID)
    }

    sqlite3_finalize(stmt);
    return user_id;
}