/**
 * @file user_repo.c
 * @brief Handles database operations related to users (Auth, Status).
 */

#include "../../include/repo/user_repo.h"
#include "../../include/database.h"
#include "../../include/utils/logger.h"
#include <stdio.h>
#include <string.h>

/**
 * @brief Registers a new user.
 * @return 1 if success, 0 if failure (e.g., duplicate email).
 */
int db_register_user(const char *name, const char *email, const char *password)
{
    char query[1024];
    snprintf(query, sizeof(query),
             "INSERT INTO users (name, email, password) VALUES ('%s', '%s', '%s')",
             name, email, password);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        unsigned int err_no = mysql_errno(conn);
        LOG_ERROR("Register DB Error (%u): %s", err_no, mysql_error(conn));

        db_release_conn(conn);
        if (err_no == 1062)
        {              // ER_DUP_ENTRY
            return -1; // Lỗi trùng email
        }
        return 0; // Lỗi DB khác
    }
    db_release_conn(conn);
    return 1; // Thành công
}

/**
 * @brief Verifies login credentials.
 * @param user_out Pointer to UserInfoPayload to fill data if successful.
 * @return UserID (> 0) if success, -1 if failure.
 */
int db_check_login(const char *email, const char *password, UserInfoPayload *user_out)
{
    char query[1024];
    snprintf(query, sizeof(query),
             "SELECT id, name, email FROM users WHERE email='%s' AND password='%s'",
             email, password);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;
    if (mysql_query(conn, query))
    {
        LOG_ERROR("Login DB Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return -1;
    }

    MYSQL_RES *result = mysql_store_result(conn);

    int user_id = -1;

    if (result)
    {
        if (mysql_num_rows(result) > 0)
        {
            MYSQL_ROW row = mysql_fetch_row(result);
            if (row && row[0])
            {
                user_id = atoi(row[0]);

                // Fill payload data
                user_out->user_id = user_id;
                strncpy(user_out->name, row[1] ? row[1] : "No Name", MAX_NAME_LEN - 1);
                strncpy(user_out->email, row[2] ? row[2] : "", MAX_EMAIL_LEN - 1);
                user_out->is_online = 1; // Mặc định vừa login xong là online
            }
        }
        mysql_free_result(result);
    }

    db_release_conn(conn);
    return user_id;
}

/**
 * @brief Updates user online status.
 */
void db_update_user_status(int user_id, int is_online)
{
    char query[256];
    snprintf(query, sizeof(query),
             "UPDATE users SET is_online = %d WHERE id = %d",
             is_online, user_id);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Update Status Error for User %d: %s", user_id, mysql_error(conn));
    }
    else
    {
        LOG_INFO("User %d status updated to: %s", user_id, is_online ? "online" : "offline");
    }
    db_release_conn(conn);
}

/**
 * @brief Tìm kiếm người dùng theo từ khóa (Partial match).
 * 
 * @param keyword Từ khóa tìm kiếm (Tên hoặc Email).
 * @param current_id ID của người đang thực hiện tìm kiếm (để loại trừ khỏi kết quả).
 * @param out_list Mảng UserSearchInfo để lưu kết quả.
 * @param limit Số lượng kết quả tối đa.
 * @return int Số lượng bản ghi tìm thấy (0 nếu lỗi hoặc không có).
 */
int db_search_users(const char *keyword, int current_id, UserSearchInfo *out_list, int limit, int offset)
{
    // Validate input
    if (!keyword || strlen(keyword) == 0) return 0;

    MYSQL *conn = db_get_conn();
    if (!conn) {
        LOG_ERROR("Search User: Failed to connect to DB.");
        return 0;
    }

    char query[1024];
    // Sử dụng LIKE %keyword% để tìm kiếm gần đúng
    // Loại trừ bản thân (id != current_id)
    snprintf(query, sizeof(query),
             "SELECT id, name, email FROM users "
             "WHERE (name LIKE '%%%s%%' OR email LIKE '%%%s%%') "
             "AND id != %d "
             "LIMIT %d OFFSET %d",
             keyword, keyword, current_id, limit, offset);

    if (mysql_query(conn, query)) {
        LOG_ERROR("Search DB Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    int count = 0;

    if (res) {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(res)) && count < limit) {
            out_list[count].user_id = atoi(row[0]);
            
            // Xử lý an toàn chuỗi
            if (row[1]) strncpy(out_list[count].name, row[1], MAX_NAME_LEN - 1);
            else strcpy(out_list[count].name, "Unknown");
            
            if (row[2]) strncpy(out_list[count].email, row[2], MAX_EMAIL_LEN - 1);
            else strcpy(out_list[count].email, "");

            count++;
        }
        mysql_free_result(res);
    }

    db_release_conn(conn);
    LOG_INFO("User %d searched '%s'. Found %d results.", current_id, keyword, count);    
    return count;
}

/**
 * @brief Lấy tên user theo ID.
 * 
 * @param user_id ID của user cần lấy tên.
 * @param name_buf Bộ đệm để lưu tên.
 * @param buf_len Độ dài bộ đệm.
 */
void get_user_name_by_id(int user_id, char *name_buf, int buf_len)
{
    char query[256];
    snprintf(query, sizeof(query), "SELECT name FROM users WHERE id=%d", user_id);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return;

    if (mysql_query(conn, query))
    {
        db_release_conn(conn);
        return;
    }

    MYSQL_RES *res = mysql_store_result(conn);

    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row && row[0])
            strncpy(name_buf, row[0], buf_len - 1);
        else
            strncpy(name_buf, "Unknown", buf_len - 1);
        mysql_free_result(res);
    }

    db_release_conn(conn);
}

/**
 * @brief Lấy thông tin chi tiết user (ID, Name, Email, Online Status)
 * @param user_id ID user cần lấy
 * @param out_info Pointer để lưu kết quả
 * @return 1 nếu tìm thấy, 0 nếu lỗi hoặc không tìm thấy
 */
int db_get_user_info_by_id(int user_id, UserInfoPayload *out_info)
{
    char query[512];
    snprintf(query, sizeof(query),
             "SELECT id, name, email, is_online FROM users WHERE id=%d",
             user_id);

    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    if (mysql_query(conn, query))
    {
        LOG_ERROR("db_get_user_info_by_id Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    int found = 0;

    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row)
        {
            memset(out_info, 0, sizeof(UserInfoPayload));

            out_info->user_id = atoi(row[0]);

            if (row[1])
                strncpy(out_info->name, row[1], MAX_NAME_LEN - 1);
            if (row[2])
                strncpy(out_info->email, row[2], MAX_EMAIL_LEN - 1);
            if (row[3])
            {
                out_info->is_online = (int8_t)atoi(row[3]);
            }
            else
            {
                out_info->is_online = 0;
            }

            found = 1;
        }
        mysql_free_result(res);
    }

    db_release_conn(conn);
    return found;
}

void db_reset_all_users_offline()
{
    MYSQL *conn = db_get_conn();
    if (!conn) {
        LOG_ERROR("Cannot connect to DB to reset user status.");
        return;
    }

    const char *query = "UPDATE users SET is_online = 0";

    if (mysql_query(conn, query)) {
        LOG_ERROR("Failed to reset users offline: %s", mysql_error(conn));
    } else {
        // Lấy số lượng hàng bị ảnh hưởng (số user vừa được reset)
        my_ulonglong affected_rows = mysql_affected_rows(conn);
        LOG_INFO("Server Start: Reset %lu users to OFFLINE status.", affected_rows);
    }

    db_release_conn(conn);
}
