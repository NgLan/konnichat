/**
 * @file friend_repo.c
 * @brief Handles database operations related to Friends.
 */

#include "../../include/repo/friend_repo.h"
#include "../../include/database.h"
#include "../../include/utils/logger.h"
#include <stdio.h>
#include <string.h>

/**
 * @brief Retrieves friend list for a user.
 * @param friends_out Array of UserInfoPayload to store results.
 * @return Number of friends found.
 */
int db_get_friends(int user_id, int offset, int limit, UserInfoPayload *friends_out)
{
    char query[1024];

    snprintf(query, sizeof(query),
             "SELECT u.id, u.name, u.email, u.is_online "
             "FROM Users u "
             "JOIN Friends f ON u.id = f.friend_id "
             "WHERE f.user_id = %d "
             "LIMIT %d OFFSET %d",
             user_id, limit, offset);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Friends DB Error: %s", mysql_error(conn));
        return 0;
    }

    MYSQL_RES *result = mysql_store_result(conn);
    int count = 0;

    if (result)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < limit)
        {
            friends_out[count].user_id = atoi(row[0]);
            strncpy(friends_out[count].name, row[1], MAX_NAME_LEN - 1);
            strncpy(friends_out[count].email, row[2], MAX_EMAIL_LEN - 1);
            friends_out[count].is_online = (row[3] && atoi(row[3]) == 1) ? 1 : 0;

            count++;
        }
        mysql_free_result(result);
    }

    return count;
}

int db_get_friend_ids(int user_id, int *ids_out, int limit, int offset)
{
    char query[256];
    snprintf(query, sizeof(query),
             "SELECT friend_id FROM Friends WHERE user_id = %d LIMIT %d OFFSET %d",
             user_id, limit, offset);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Friend IDs Error: %s", mysql_error(conn));
        return 0;
    }

    MYSQL_RES *result = mysql_store_result(conn);
    int count = 0;
    if (result)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(result)) && count < limit)
        {
            if (row[0])
            {
                ids_out[count] = atoi(row[0]);
                count++;
            }
        }
        mysql_free_result(result);
    }
    return count;
}

int db_send_friend_request(int sender_id, int target_id)
{
    char query[1024];

    // 1. Kiểm tra đã là bạn chưa
    snprintf(query, sizeof(query),
             "SELECT id FROM Friends WHERE (user_id=%d AND friend_id=%d) OR (user_id=%d AND friend_id=%d)",
             sender_id, target_id, target_id, sender_id);
    if (mysql_query(conn, query))
        return 0;
    MYSQL_RES *res = mysql_store_result(conn);
    if (res && mysql_num_rows(res) > 0)
    {
        mysql_free_result(res);
        return -1;
    } // Đã là bạn
    if (res)
        mysql_free_result(res);

    // 2. Kiểm tra có request đang chờ không
    snprintf(query, sizeof(query),
             "SELECT id FROM FriendRequests WHERE sender_id=%d AND receiver_id=%d AND status='waiting'",
             sender_id, target_id);
    if (mysql_query(conn, query))
        return 0;
    res = mysql_store_result(conn);
    if (res && mysql_num_rows(res) > 0)
    {
        mysql_free_result(res);
        return -2;
    } // Đang chờ
    if (res)
        mysql_free_result(res);

    // 3. Insert Request mới
    snprintf(query, sizeof(query),
             "INSERT INTO FriendRequests (sender_id, receiver_id, status) VALUES (%d, %d, 'waiting') "
             "ON DUPLICATE KEY UPDATE status='waiting', created_at=CURRENT_TIMESTAMP",
             sender_id, target_id);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Send Friend Req Error: %s", mysql_error(conn));
        return 0;
    }
    return (int)mysql_insert_id(conn);
}

int db_get_pending_requests(int user_id, PendingReqInfo *list_out, int max_count)
{
    char query[1024];
    snprintf(query, sizeof(query),
             "SELECT fr.id, fr.sender_id, u.name "
             "FROM FriendRequests fr "
             "JOIN Users u ON fr.sender_id = u.id "
             "WHERE fr.receiver_id = %d AND fr.status = 'waiting' "
             "LIMIT %d",
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
            list_out[count].request_id = atoi(row[0]);
            list_out[count].sender_id = atoi(row[1]);
            strncpy(list_out[count].sender_name, row[2] ? row[2] : "Unknown", 63);
            count++;
        }
        mysql_free_result(result);
    }
    return count;
}

int db_respond_friend_request(int request_id, int current_user_id, int is_accepted, int *sender_id_out)
{
    char query[1024];
    int sender_id = 0, receiver_id = 0;

    // 1. Lấy thông tin request
    snprintf(query, sizeof(query), "SELECT sender_id, receiver_id FROM FriendRequests WHERE id=%d", request_id);
    if (mysql_query(conn, query))
        return 0;
    MYSQL_RES *res = mysql_store_result(conn);
    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row)
        {
            sender_id = atoi(row[0]);
            receiver_id = atoi(row[1]);
        }
        mysql_free_result(res);
    }

    if (receiver_id != current_user_id)
        return 0; // Bảo mật: Không được duyệt hộ người khác
    if (sender_id_out)
        *sender_id_out = sender_id;

    // 2. Update status
    snprintf(query, sizeof(query), "UPDATE FriendRequests SET status='%s' WHERE id=%d",
             is_accepted ? "approved" : "denied", request_id);
    if (mysql_query(conn, query))
        return 0;

    // 3. Nếu đồng ý -> Insert vào bảng Friends
    if (is_accepted)
    {
        // Insert 2 chiều
        snprintf(query, sizeof(query), "INSERT IGNORE INTO Friends (user_id, friend_id) VALUES (%d, %d), (%d, %d)",
                 sender_id, receiver_id, receiver_id, sender_id);
        mysql_query(conn, query);
    }
    return 1;
}

int db_remove_friend(int user_id, int friend_id)
{
    char query[512];
    snprintf(query, sizeof(query),
             "DELETE FROM Friends WHERE (user_id=%d AND friend_id=%d) OR (user_id=%d AND friend_id=%d)",
             user_id, friend_id, friend_id, user_id);
    if (mysql_query(conn, query))
        return 0;
    return (mysql_affected_rows(conn) > 0);
}