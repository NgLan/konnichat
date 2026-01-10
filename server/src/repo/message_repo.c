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
#include <time.h>
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
             "FROM messages "
             "WHERE receiver_id = %d AND status = 'sent' AND chat_type = 'private' "
             "ORDER BY created_at ASC, id ASC LIMIT %d",
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
            memset(&messages_out[count], 0, sizeof(ChatPayload));
            messages_out[count].message_id = atoi(row[0]);
            messages_out[count].sender_id = atoi(row[1]);
            messages_out[count].receiver_id = atoi(row[2]);
            strncpy(messages_out[count].content, row[3], MAX_CONTENT_LEN - 1);
            messages_out[count].created_at = row[4] ? parse_mysql_time(row[4]) : 0;

            if (row[5])
            {
                strncpy(messages_out[count].chat_type, row[5], 15);
            }
            else
            {
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
int db_get_chat_history(int current_user_id, int target_id, int is_group, ChatPayload *messages_out, int limit, int offset)
{
    char query[1024];

    if (is_group)
    {
        // LOGIC GROUP: Lấy tất cả tin nhắn gửi vào Group này
        snprintf(query, sizeof(query),
                 "SELECT id, sender_id, receiver_id, content, created_at, chat_type, msg_type, status "
                 "FROM messages "
                 "WHERE receiver_id = %d AND chat_type = 'group' "
                 "ORDER BY created_at DESC, id DESC LIMIT %d OFFSET %d",
                 target_id, limit, offset);
    }
    else
    {
        // LOGIC PRIVATE: 2 chiều A->B và B->A
        snprintf(query, sizeof(query),
                 "SELECT id, sender_id, receiver_id, content, created_at, chat_type, msg_type, status "
                 "FROM messages "
                 "WHERE ((sender_id = %d AND receiver_id = %d) "
                 "   OR (sender_id = %d AND receiver_id = %d)) "
                 "AND chat_type = 'private' "
                 "ORDER BY created_at DESC, id DESC LIMIT %d OFFSET %d",
                 current_user_id, target_id, target_id, current_user_id, limit, offset);
    }

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get History Error: %s", mysql_error(conn));
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

            // Msg Type
            messages_out[count].msg_type = row[6] ? atoi(row[6]) : 1;

            char status[20];
            if (row[7])
                strcpy(status, row[7]);

            if (strcmp(status, "revoked") == 0)
            {
                strcpy(messages_out[count].content, "Tin nhắn đã bị thu hồi");
                messages_out[count].msg_type = 1; // Về dạng text
            }
            else
            {
                strncpy(messages_out[count].content, row[3], MAX_CONTENT_LEN - 1);
            }

            // Time
            messages_out[count].created_at = row[4] ? parse_mysql_time(row[4]) : 0;

            // Chat Type
            if (row[5])
                strncpy(messages_out[count].chat_type, row[5], 15);
            else
                strcpy(messages_out[count].chat_type, "private");

            count++;
        }
        mysql_free_result(result);
    }

    db_release_conn(conn);
    return count;
}

int db_revoke_message(int msg_id, int user_id, int *out_receiver_id, char *out_chat_type)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    // 1. Kiểm tra quyền sở hữu và lấy thông tin routing
    char query_check[256];
    snprintf(query_check, sizeof(query_check),
             "SELECT sender_id, receiver_id, chat_type FROM messages WHERE id = %d", msg_id);

    if (mysql_query(conn, query_check))
    {
        db_release_conn(conn);
        return 0;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    int is_owner = 0;
    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row)
        {
            int sender_id = atoi(row[0]);
            if (sender_id == user_id)
            {
                is_owner = 1;
                *out_receiver_id = atoi(row[1]);
                if (row[2])
                    strcpy(out_chat_type, row[2]);
            }
        }
        mysql_free_result(res);
    }

    if (!is_owner)
    {
        db_release_conn(conn);
        return -1; // Không phải chính chủ
    }

    // 2. Update status -> 'revoked'
    // KHÔNG XÓA MESSAGE, chỉ update status
    char query_update[256];
    snprintf(query_update, sizeof(query_update),
             "UPDATE messages SET status = 'revoked' WHERE id = %d", msg_id);

    int success = 0;
    if (mysql_query(conn, query_update) == 0)
    {
        success = 1;
        LOG_INFO("Revoke Msg Successfully");
    }
    else
    {
        LOG_ERROR("Revoke Msg Error: %s", mysql_error(conn));
    }

    db_release_conn(conn);
    return success;
}

// Hàm Routing (Lấy thông tin tin nhắn để biết gửi notify cho ai)
int db_get_message_routing(int msg_id, int *out_receiver_id, char *out_chat_type)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[256];
    snprintf(query, sizeof(query), "SELECT receiver_id, chat_type FROM messages WHERE id = %d", msg_id);

    int found = 0;
    if (mysql_query(conn, query) == 0)
    {
        MYSQL_RES *res = mysql_store_result(conn);
        if (res)
        {
            MYSQL_ROW row = mysql_fetch_row(res);
            if (row)
            {
                *out_receiver_id = atoi(row[0]);
                if (row[1])
                    strcpy(out_chat_type, row[1]);
                found = 1;
            }
            mysql_free_result(res);
        }
    }
    db_release_conn(conn);
    return found;
}

// 2. Hàm React
int db_react_message(int user_id, int msg_id, int reaction_code)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[512];
    int success = 0;

    if (reaction_code == 0)
    {
        // CASE: Bỏ reaction -> DELETE
        snprintf(query, sizeof(query),
                 "DELETE FROM reactions WHERE user_id = %d AND message_id = %d",
                 user_id, msg_id);
    }
    else
    {
        // CASE: Thả reaction mới hoặc đổi reaction -> UPSERT
        snprintf(query, sizeof(query),
                 "INSERT INTO reactions (user_id, message_id, icon_id, created_at) "
                 "VALUES (%d, %d, %d, NOW()) "
                 "ON DUPLICATE KEY UPDATE icon_id = %d, created_at = NOW()",
                 user_id, msg_id, reaction_code, reaction_code);
    }

    if (mysql_query(conn, query))
    {
        LOG_ERROR("React Message DB Error: %s", mysql_error(conn));
    }
    else
    {
        success = 1;
    }

    db_release_conn(conn);
    return success;
}

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
