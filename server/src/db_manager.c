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

    mysql_set_character_set(conn, "utf8mb4"); 

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

// Hàm lưu tin nhắn mới vào DB (Mặc định status = 'sent')
int db_save_message(int sender_id, int receiver_id, const char *content)
{
    char query[2048];
    // status mặc định là 'sent' (chưa nhận)
    snprintf(query, sizeof(query),
             "INSERT INTO Messages (sender_id, receiver_id, content, status) "
             "VALUES (%d, %d, '%s', 'sent')",
             sender_id, receiver_id, content);

    if (mysql_query(conn, query))
    {
        fprintf(stderr, "Save Msg Error: %s\n", mysql_error(conn));
        return 0;
    }
    return 1;
}

// Hàm lấy các tin nhắn chưa nhận (status = 'sent') khi User đăng nhập
int db_get_pending_messages(int user_id, MessageInfo *messages_out, int max_count)
{
    char query[1024];
    snprintf(query, sizeof(query),
             "SELECT id, sender_id, content, created_at "
             "FROM Messages WHERE receiver_id = %d AND status = 'sent' "
             "ORDER BY created_at ASC LIMIT %d",
             user_id, max_count);

    if (mysql_query(conn, query))
        return 0;

    MYSQL_RES *result = mysql_store_result(conn);
    int count = 0;
    if (result)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < max_count)
        {
            messages_out[count].message_id = atoi(row[0]);
            messages_out[count].sender_id = atoi(row[1]);
            strncpy(messages_out[count].content, row[2], 511);
            strncpy(messages_out[count].timestamp, row[3], 19);
            count++;
        }
        mysql_free_result(result);
    }
    return count;
}

// Hàm đánh dấu tin nhắn đã gửi xong (status -> 'delivered')
void db_mark_message_delivered(int message_id)
{
    char query[256];
    snprintf(query, sizeof(query),
             "UPDATE Messages SET status = 'delivered' WHERE id = %d", message_id);
    mysql_query(conn, query);
}
