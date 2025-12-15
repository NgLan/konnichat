/**
 * @file user_repo.c
 * @brief Handles database operations related to Users (Auth, Status).
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
             "INSERT INTO Users (name, email, password) VALUES ('%s', '%s', '%s')",
             name, email, password);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Register DB Error: %s", mysql_error(conn));
        return 0;
    }
    return 1;
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
             "SELECT id, name, email FROM Users WHERE email='%s' AND password='%s'",
             email, password);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Login DB Error: %s", mysql_error(conn));
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

    return user_id;
}

/**
 * @brief Updates user online status.
 */
void db_update_user_status(int user_id, int is_online)
{
    char query[256];
    snprintf(query, sizeof(query),
             "UPDATE Users SET is_online = %d WHERE id = %d",
             is_online, user_id);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Update Status Error for User %d: %s", user_id, mysql_error(conn));
    }
    else
    {
        LOG_INFO("User %d status updated to: %s", user_id, is_online ? "online" : "offline");
    }
}
