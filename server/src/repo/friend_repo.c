/**
 * @file friend_repo.c
 * @brief Handles database operations related to friends.
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
             "FROM users u "
             "JOIN friends f ON ( "
             "   (f.user_id = %d AND f.friend_id = u.id) " // Trường hợp mình là cột trái -> lấy cột phải
             "   OR "
             "   (f.friend_id = %d AND f.user_id = u.id) " // Trường hợp mình là cột phải -> lấy cột trái
             ") "
             "LIMIT %d OFFSET %d",
             user_id, user_id, limit, offset);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get friends DB Error: %s", mysql_error(conn));
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
            friends_out[count].user_id = atoi(row[0]);
            strncpy(friends_out[count].name, row[1], MAX_NAME_LEN - 1);
            strncpy(friends_out[count].email, row[2], MAX_EMAIL_LEN - 1);
            friends_out[count].is_online = (row[3] && atoi(row[3]) == 1) ? 1 : 0;

            count++;
        }
        mysql_free_result(result);
    }

    db_release_conn(conn);
    return count;
}

int db_get_friend_ids(int user_id, int *ids_out, int limit, int offset)
{
    char query[256];
    snprintf(query, sizeof(query),
             "SELECT CASE "
             "  WHEN user_id = %d THEN friend_id "
             "  ELSE user_id "
             "END as friend_id "
             "FROM friends "
             "WHERE user_id = %d OR friend_id = %d "
             "LIMIT %d OFFSET %d",
             user_id, user_id, user_id, limit, offset);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Friend IDs Error: %s", mysql_error(conn));
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
            if (row[0])
            {
                ids_out[count] = atoi(row[0]);
                count++;
            }
        }
        mysql_free_result(result);
    }

    db_release_conn(conn);
    return count;
}

int db_send_friend_request(int sender_id, int target_id)
{
    char query[1024];

    LOG_INFO("=== db_send_friend_request: Sender %d -> Target %d ===", sender_id, target_id);

    // 1. Không tự kết bạn với chính mình
    if (sender_id == target_id)
    {
        LOG_WARN("Self-friend request rejected");
        return -3;
    }

    // [LOCK] Bắt đầu Transaction
    MYSQL *conn = db_get_conn();
    if (!conn)
    {
        LOG_ERROR("Failed to get DB connection");
        return 0;
    }

    // 2. Kiểm tra đã là bạn chưa
    snprintf(query, sizeof(query),
             "SELECT id FROM friends WHERE (user_id=%d AND friend_id=%d) OR (user_id=%d AND friend_id=%d)",
             sender_id, target_id, target_id, sender_id);
    if (mysql_query(conn, query))
    {
        LOG_ERROR("Query friends table failed: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }
    MYSQL_RES *res = mysql_store_result(conn);
    if (res)
    {
        int exists = (mysql_num_rows(res) > 0);
        mysql_free_result(res);
        if (exists)
        {
            LOG_WARN("Already friends: %d <-> %d", sender_id, target_id);
            db_release_conn(conn);
            return -1; // Đã là bạn
        }
    }

    // 3. Kiểm tra có request đang chờ không
    snprintf(query, sizeof(query),
             "SELECT id FROM friend_requests WHERE sender_id=%d AND receiver_id=%d AND status='waiting'",
             sender_id, target_id);
    if (mysql_query(conn, query))
    {
        LOG_ERROR("Query friend_requests failed: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }
    res = mysql_store_result(conn);
    if (res)
    {
        int exists = (mysql_num_rows(res) > 0);
        mysql_free_result(res);
        if (exists)
        {
            LOG_WARN("Friend request already pending: %d -> %d", sender_id, target_id);
            db_release_conn(conn);
            return -2; // Đang chờ duyệt
        }
    }

    // 4. Insert Request mới
    snprintf(query, sizeof(query),
             "INSERT INTO friend_requests (sender_id, receiver_id, status) VALUES (%d, %d, 'waiting')",
             sender_id, target_id);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Insert Friend Req Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    int req_id = (int)mysql_insert_id(conn);
    LOG_INFO("mysql_insert_id returned: %d", req_id);

    db_release_conn(conn);
    LOG_INFO("Friend request created with ID: %d", req_id);
    return req_id;
}

int db_get_pending_requests(int user_id, PendingReqInfo *list_out, int max_count)
{
    char query[1024];
    snprintf(query, sizeof(query),
             "SELECT fr.id, fr.sender_id, u.name "
             "FROM friend_requests fr "
             "JOIN users u ON fr.sender_id = u.id "
             "WHERE fr.receiver_id = %d AND fr.status = 'waiting' "
             "LIMIT %d",
             user_id, max_count);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Pending Reqs Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

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

    db_release_conn(conn);
    return count;
}

int db_respond_friend_request(int request_id, int current_user_id, int is_accepted, int *sender_id_out)
{
    char query[1024];
    int sender_id = 0, receiver_id = 0;

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    // 1. Lấy thông tin request
    snprintf(query, sizeof(query), "SELECT sender_id, receiver_id FROM friend_requests WHERE id=%d", request_id);
    if (mysql_query(conn, query))
    {
        db_release_conn(conn);
        return 0;
    }
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
    {
        db_release_conn(conn);
        return 0; // Bảo mật: Không được duyệt hộ người khác
    }
    if (sender_id_out)
        *sender_id_out = sender_id;

    // 2. Update status
    snprintf(query, sizeof(query), "UPDATE friend_requests SET status='%s' WHERE id=%d",
             is_accepted ? "approved" : "denied", request_id);
    if (mysql_query(conn, query))
    {
        db_release_conn(conn);
        return 0;
    }

    // 3. Nếu đồng ý -> Insert vào bảng friends
    if (is_accepted)
    {
        snprintf(query, sizeof(query), "INSERT IGNORE INTO friends (user_id, friend_id) VALUES (%d, %d)",
                 sender_id, receiver_id);
        mysql_query(conn, query);
    }

    db_release_conn(conn);
    return 1;
}

int db_remove_friend(int user_id, int friend_id)
{
    char query[512];
    snprintf(query, sizeof(query),
             "DELETE FROM friends WHERE (user_id=%d AND friend_id=%d) OR (user_id=%d AND friend_id=%d)",
             user_id, friend_id, friend_id, user_id);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        db_release_conn(conn);
        return 0;
    }

    int rows = mysql_affected_rows(conn);

    db_release_conn(conn);
    return (rows > 0);
}
