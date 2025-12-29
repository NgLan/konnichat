/**
 * @file message_repo.c
 * @brief Handles database operations related to Chat messages.
 */

#include "../../include/repo/message_repo.h"
#include "../../include/database.h"
#include "../../include/utils/logger.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h> // <--- Cần thêm cái này để dùng struct tm, time_t
#include <stdint.h>

static uint64_t parse_mysql_time(const char *str);

/**
 * @brief Saves a new message to DB.
 * @return New Message ID or 0 if failed.
 */
int db_save_message(int sender_id, int receiver_id, const char *content, uint64_t created_at, int msg_type, const char *chat_type)
{
    char query[2048];
    char time_str[20];

    // 1. Đổi timestamp (ms) sang chuỗi SQL "YYYY-MM-DD HH:MM:SS"
    time_t seconds = (time_t)(created_at / 1000);
    struct tm *t = localtime(&seconds);
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", t);

    // 2. Insert vào DB
    snprintf(query, sizeof(query),
             "INSERT INTO messages (sender_id, receiver_id, content, status, created_at, chat_type, msg_type) "
             "VALUES (%d, %d, '%s', 'sent', '%s', '%s', %d)",
             sender_id, receiver_id, content, time_str, chat_type, msg_type);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Save Msg Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    int new_id = (int)mysql_insert_id(conn);
    db_release_conn(conn);
    return new_id;
}

/**
 * @brief Marks a message as delivered.
 */
void db_mark_message_delivered(int message_id)
{
    char query[256];
    snprintf(query, sizeof(query),
             "UPDATE messages SET status = 'delivered' WHERE id = %d", message_id);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Mark Delivered Error (MsgID %d): %s", message_id, mysql_error(conn));
    }
    db_release_conn(conn);
}

/**
 * @brief Retrieves offline messages (status = 'sent').
 */
int db_get_offline_messages(int user_id, ChatPayload *messages_out, int limit)
{
    char query[1024];
    // Sắp xếp: ASC (Cũ nhất gửi trước -> Mới nhất gửi sau)
    snprintf(query, sizeof(query),
             "SELECT id, sender_id, receiver_id, content, created_at, chat_type, msg_type "
             "FROM messages WHERE receiver_id = %d AND status = 'sent' "
             "ORDER BY created_at ASC LIMIT %d",
             user_id, limit);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Offline Msgs Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    MYSQL_RES *result = mysql_store_result(conn);

    int count = 0;
    if (result)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < limit)
        {
            messages_out[count].message_id = atoi(row[0]);
            messages_out[count].sender_id = atoi(row[1]);
            messages_out[count].receiver_id = atoi(row[2]);
            strncpy(messages_out[count].content, row[3], MAX_CONTENT_LEN - 1);
            messages_out[count].created_at = row[4] ? parse_mysql_time(row[4]) : 0;
            if (row[5]) {
                strncpy(messages_out[count].chat_type, row[5], 15);
            } else {
                strcpy(messages_out[count].chat_type, "private");
            }
            messages_out[count].msg_type = row[6] ? atoi(row[6]) : 1;

            count++;
        }
        mysql_free_result(result);
    }

    db_release_conn(conn);
    return count;
}

/**
 * @brief Retrieves chat history between two users.
 */
// int db_get_chat_history(int user1, int user2, ChatPayload *messages_out, int limit)
// {
//     char query[1024];
//     snprintf(query, sizeof(query),
//              "SELECT id, sender_id, receiver_id, content, created_at "
//              "FROM messages "
//              "WHERE (sender_id = %d AND receiver_id = %d) "
//              "   OR (sender_id = %d AND receiver_id = %d) "
//              "ORDER BY created_at DESC LIMIT %d", // Lấy tin mới nhất trở về trước
//              user1, user2, user2, user1, limit);

//     MYSQL *conn = db_get_conn();
//     if (!conn)
//         return 0;
//     if (mysql_query(conn, query))
//     {
//         LOG_ERROR("Get History Error: %s", mysql_error(conn));
//         db_release_conn(conn);
//         return 0;
//     }

//     MYSQL_RES *result = mysql_store_result(conn);

//     int count = 0;
//     if (result)
//     {
//         MYSQL_ROW row;
//         while ((row = mysql_fetch_row(result)) && count < limit)
//         {
//             messages_out[count].message_id = atoi(row[0]);
//             messages_out[count].sender_id = atoi(row[1]);
//             messages_out[count].receiver_id = atoi(row[2]);
//             messages_out[count].msg_type = 1;
//             strncpy(messages_out[count].content, row[3], MAX_CONTENT_LEN - 1);
//             if (row[4])
//             {
//                 messages_out[count].created_at = parse_mysql_time(row[4]);
//             }
//             else
//             {
//                 messages_out[count].created_at = 0;
//             }

//             count++;
//         }
//         mysql_free_result(result);
//     }

//     db_release_conn(conn);
//     return count;
// }

static uint64_t parse_mysql_time(const char *str)
{
    struct tm tm;
    memset(&tm, 0, sizeof(struct tm));

    // Parse chuỗi
    if (sscanf(str, "%d-%d-%d %d:%d:%d",
               &tm.tm_year, &tm.tm_mon, &tm.tm_mday,
               &tm.tm_hour, &tm.tm_min, &tm.tm_sec) != 6)
    {
        return 0;
    }

    tm.tm_year -= 1900; // Năm tính từ 1900
    tm.tm_mon -= 1;     // Tháng 0-11

    // Chuyển sang time_t (giây)
    time_t t = mktime(&tm);
    if (t == -1)
        return 0;

    // Nhân 1000 để ra milliseconds
    return (uint64_t)t * 1000;
}
