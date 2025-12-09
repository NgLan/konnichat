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
int db_get_friends(int user_id, UserInfoPayload *friends_out, int max_count)
{
    char query[1024];

    snprintf(query, sizeof(query),
             "SELECT u.id, u.name, u.email, u.is_online "
             "FROM Users u "
             "JOIN Friends f ON u.id = f.friend_id "
             "WHERE f.user_id = %d "
             "LIMIT %d",
             user_id, max_count);

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
        while ((row = mysql_fetch_row(result)) && count < max_count)
        {
            friends_out[count].user_id = atoi(row[0]);
            strncpy(friends_out[count].name, row[1], MAX_NAME_LEN - 1);
            strncpy(friends_out[count].email, row[2], MAX_EMAIL_LEN - 1);

            // Convert string status to int
            if (row[3] && strcmp(row[3], "online") == 0)
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
