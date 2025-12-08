#include "../include/db_manager.h"
#include "../include/dotenv.h"
#include <stdio.h>
#include <stdlib.h>

MYSQL *conn;

void init_database()
{
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
    if (!host || !user || !pass || !name)
    {
        fprintf(stderr, "LỖI: Thiếu cấu hình DB trong file .env\n");
        exit(1);
    }

    conn = mysql_init(NULL);
    if (mysql_real_connect(conn, host, user, pass, name, port, NULL, 0) == NULL)
    {
        fprintf(stderr, "Lỗi kết nối MySQL: %s\n", mysql_error(conn));
        exit(1);
    }

    printf("Đã kết nối Database thành công (Host: %s)\n", host);
}

int db_register_user(const char *username, const char *password)
{
    char query[1024];
    snprintf(query, sizeof(query),
             "INSERT INTO Users (email, password) VALUES ('%s', '%s')",
             username, password);
    if (mysql_query(conn, query))
        return 0;
    return 1;
}

int db_check_login(const char *email, const char *password)
{
    char query[1024];
    int user_id = -1;
    snprintf(query, sizeof(query),
             "SELECT id FROM Users WHERE email='%s' AND password='%s'",
             email, password);

    if (mysql_query(conn, query))
        return -1;

    MYSQL_RES *result = mysql_store_result(conn);
    if (result)
    {
        if (mysql_num_rows(result) > 0)
        {
            MYSQL_ROW row = mysql_fetch_row(result);
            if (row && row[0])
                user_id = atoi(row[0]);
        }
        mysql_free_result(result);
    }
    return user_id;
}

int db_get_friends(int user_id, FriendInfo *friends_out, int max_count)
{
    char query[1024];

    // JOIN bảng Users và Friends
    // Lấy ID, Tên, Trạng thái (online/offline) của người bạn
    snprintf(query, sizeof(query),
             "SELECT u.id, u.name, u.is_online "
             "FROM Users u "
             "JOIN Friends f ON u.id = f.friend_id "
             "WHERE f.user_id = %d "
             "LIMIT %d",
             user_id, max_count);

    if (mysql_query(conn, query))
    {
        fprintf(stderr, "Query Error: %s\n", mysql_error(conn));
        return 0;
    }

    MYSQL_RES *result = mysql_store_result(conn);
    int count = 0;

    if (result)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < max_count)
        {
            // Cột 0: ID
            friends_out[count].id = atoi(row[0]);

            // Cột 1: Name
            strncpy(friends_out[count].name, row[1], 49);
            friends_out[count].name[49] = '\0';

            // Cột 2: is_online (VARCHAR) -> Chuyển thành int (0/1) cho Client dễ dùng
            // row[2] có thể là "online" hoặc "offline"
            if (row[2] && strcmp(row[2], "online") == 0)
            {
                friends_out[count].is_online = 1;
            }
            else
            {
                friends_out[count].is_online = 0;
            }

            count++;
        }
        mysql_free_result(result);
    }
    return count;
}


// Task 8: Gửi yêu cầu kết bạn
// Return: 
//  1: Thành công
//  0: Lỗi database
// -1: Đã là bạn bè rồi
// -2: Đã gửi lời mời trước đó rồi (đang chờ)
// File: src/db_manager.c
int db_send_friend_request(int sender_id, int receiver_id)
{
    char query[1024];
    MYSQL_RES *result;
    
    // 1. Kiểm tra xem đã là bạn bè chưa (Trong bảng Friends)
    snprintf(query, sizeof(query), 
             "SELECT id FROM Friends WHERE (user_id=%d AND friend_id=%d) OR (user_id=%d AND friend_id=%d)", 
             sender_id, receiver_id, receiver_id, sender_id);
    
    if (mysql_query(conn, query)) return 0;
    
    result = mysql_store_result(conn);
    if (result) {
        if (mysql_num_rows(result) > 0) {
            mysql_free_result(result);
            return -1; // Đã là bạn bè
        }
        mysql_free_result(result);
    }

    // 2. Kiểm tra xem đã có lời mời đang chờ (waiting) chưa
    snprintf(query, sizeof(query),
             "SELECT id FROM FriendRequests WHERE sender_id=%d AND receiver_id=%d AND status='waiting'",
             sender_id, receiver_id);

    if (mysql_query(conn, query)) return 0;
    
    result = mysql_store_result(conn);
    if (result) {
        if (mysql_num_rows(result) > 0) {
            mysql_free_result(result);
            return -2; // Đang chờ duyệt, không gửi lại
        }
        mysql_free_result(result);
    }

    // 3. Insert hoặc Update (Fix cho trường hợp đã từng unfriend)
    // Nếu cặp (sender, receiver) đã tồn tại (do lần kết bạn trước), ta update lại status thành 'waiting'
    snprintf(query, sizeof(query),
             "INSERT INTO FriendRequests (sender_id, receiver_id, status) VALUES (%d, %d, 'waiting') "
             "ON DUPLICATE KEY UPDATE status='waiting', created_at=CURRENT_TIMESTAMP",
             sender_id, receiver_id);

    if (mysql_query(conn, query)) {
        fprintf(stderr, "Insert/Update Request Error: %s\n", mysql_error(conn));
        return 0;
    }

    // Nếu Update, mysql_insert_id có thể không trả về đúng ID dòng cũ.
    // Nên ta cần query lại ID để trả về chính xác cho Client dùng
    snprintf(query, sizeof(query), 
            "SELECT id FROM FriendRequests WHERE sender_id=%d AND receiver_id=%d", 
            sender_id, receiver_id);
    mysql_query(conn, query);
    result = mysql_store_result(conn);
    int req_id = 0;
    if (result) {
        MYSQL_ROW row = mysql_fetch_row(result);
        if (row) req_id = atoi(row[0]);
        mysql_free_result(result);
    }

    return req_id;
}
// Hàm hỗ trợ lấy tên người dùng (để gửi thông báo Real-time)
void db_get_user_name(int user_id, char *name_buffer) {
    char query[256];
    snprintf(query, sizeof(query), "SELECT name FROM Users WHERE id=%d", user_id);
    
    if (mysql_query(conn, query) == 0) {
        MYSQL_RES *res = mysql_store_result(conn);
        if (res) {
            MYSQL_ROW row = mysql_fetch_row(res);
            if (row && row[0]) {
                strcpy(name_buffer, row[0]);
            }
            mysql_free_result(res);
        }
    }
}

// Task 9: Lấy danh sách lời mời kết bạn đang chờ (để hiển thị lên UI)
int db_get_pending_requests(int user_id, PendingReqInfo *list_out, int max_count)
{
    char query[1024];
    // Lấy thông tin request + tên người gửi
    snprintf(query, sizeof(query),
             "SELECT fr.id, fr.sender_id, u.name "
             "FROM FriendRequests fr "
             "JOIN Users u ON fr.sender_id = u.id "
             "WHERE fr.receiver_id = %d AND fr.status = 'waiting' "
             "LIMIT %d",
             user_id, max_count);

    if (mysql_query(conn, query)) return 0;

    MYSQL_RES *result = mysql_store_result(conn);
    int count = 0;
    if (result) {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < max_count) {
            list_out[count].request_id = atoi(row[0]);
            list_out[count].sender_id = atoi(row[1]);
            strncpy(list_out[count].sender_name, row[2], 49);
            list_out[count].sender_name[49] = '\0';
            count++;
        }
        mysql_free_result(result);
    }
    return count;
}

// Task 9: Phản hồi lời mời (Đồng ý hoặc Từ chối)
// is_accepted: 1 (Đồng ý), 0 (Từ chối)
int db_respond_friend_request(int request_id, int current_user_id, int is_accepted, int *sender_id_out)
{
    char query[1024];
    
    // 1. Lấy thông tin VÀ KIỂM TRA QUYỀN SỞ HỮU (receiver_id phải == current_user_id)
    int sender_id = 0, receiver_id = 0;
    snprintf(query, sizeof(query), 
             "SELECT sender_id, receiver_id FROM FriendRequests WHERE id=%d", 
             request_id);
    
    if (mysql_query(conn, query)) return 0;
    MYSQL_RES *res = mysql_store_result(conn);
    if (res) {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row) {
            sender_id = atoi(row[0]);
            receiver_id = atoi(row[1]);
        }
        mysql_free_result(res);
    }

    // --- CHECK BẢO MẬT: FIX LỖI 2 ---
    if (sender_id == 0 || receiver_id == 0) return 0; // Không tìm thấy request
    if (receiver_id != current_user_id) {
        printf("[WARN] User %d cố tình can thiệp Request %d của User %d -> CHẶN!\n", current_user_id, request_id, receiver_id);
        return 0; // Không có quyền xử lý request của người khác
    }

    // Trả sender_id ra ngoài để handle_client bắn thông báo
    if (sender_id_out) *sender_id_out = sender_id;

    // 2. Cập nhật trạng thái
    const char *new_status = is_accepted ? "approved" : "denied";
    snprintf(query, sizeof(query), 
             "UPDATE FriendRequests SET status='%s' WHERE id=%d", 
             new_status, request_id);
    if (mysql_query(conn, query)) return 0;

    // 3. Nếu ĐỒNG Ý -> Thêm vào bảng Friends
    if (is_accepted) {
        snprintf(query, sizeof(query), "INSERT IGNORE INTO Friends (user_id, friend_id) VALUES (%d, %d)", sender_id, receiver_id);
        mysql_query(conn, query);
        snprintf(query, sizeof(query), "INSERT IGNORE INTO Friends (user_id, friend_id) VALUES (%d, %d)", receiver_id, sender_id);
        mysql_query(conn, query);
    }

    return 1;
}
// Task 10: Hủy kết bạn
int db_remove_friend(int user_id, int friend_id)
{
    char query[1024];
    // Xóa cả 2 chiều quan hệ
    snprintf(query, sizeof(query),
             "DELETE FROM Friends WHERE (user_id=%d AND friend_id=%d) OR (user_id=%d AND friend_id=%d)",
             user_id, friend_id, friend_id, user_id);

    if (mysql_query(conn, query)) return 0;
    
    // Hàm mysql_affected_rows trả về số dòng bị xóa. Nếu > 0 là có xóa thật.
    return (mysql_affected_rows(conn) > 0) ? 1 : 0; 
}

// [MỚI] Task 11: Tìm kiếm người dùng theo tên
// Input: keyword (tên cần tìm), current_user_id (để không tìm thấy chính mình)
// Output: Danh sách UserSearchInfo, trả về số lượng bản ghi
int db_search_users(const char *keyword, int current_user_id, UserSearchInfo *list_out, int max_count)
{
    char query[1024];
    
    // Sử dụng %keyword% để tìm kiếm mọi tên có chứa từ khóa
    // AND id != %d để không hiện ra chính bản thân người tìm
    snprintf(query, sizeof(query),
             "SELECT id, name, email FROM Users "
             "WHERE name LIKE '%%%s%%' AND id != %d "
             "LIMIT %d",
             keyword, current_user_id, max_count);

    if (mysql_query(conn, query))
    {
        fprintf(stderr, "Search Error: %s\n", mysql_error(conn));
        return 0;
    }

    MYSQL_RES *result = mysql_store_result(conn);
    int count = 0;

    if (result)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < max_count)
        {
            list_out[count].id = atoi(row[0]);
            
            // Copy Name
            if (row[1]) {
                strncpy(list_out[count].name, row[1], 49);
                list_out[count].name[49] = '\0';
            } else {
                strcpy(list_out[count].name, "Unknown");
            }

            // Copy Email
            if (row[2]) {
                strncpy(list_out[count].email, row[2], 49);
                list_out[count].email[49] = '\0';
            } else {
                strcpy(list_out[count].email, "");
            }

            count++;
        }
        mysql_free_result(result);
    }
    return count;
}