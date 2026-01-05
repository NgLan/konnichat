#include "../../include/repo/group_repo.h"
#include "../../include/database.h"
#include "../../include/utils/logger.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/**
 * @brief Tạo nhóm mới kèm Transaction
 */
int db_create_group(const char *name, int32_t creator_id, const int32_t *member_ids, int member_count)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return -1;

    // 1. Bắt đầu Transaction
    if (mysql_query(conn, "START TRANSACTION"))
    {
        LOG_ERROR("Failed to start transaction: %s", mysql_error(conn));
        db_release_conn(conn);
        return -1;
    }

    // 2. Insert vào bảng groups
    char query[1024];
    snprintf(query, sizeof(query), "INSERT INTO `groups` (name) VALUES ('%s')", name);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("DB Group Create Fail: %s", mysql_error(conn));
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return -1;
    }

    int32_t group_id = (int32_t)mysql_insert_id(conn);

    // 3. Insert Members (Creator + List)
    char insert_members[4096];
    int offset = snprintf(insert_members, sizeof(insert_members),
                          "INSERT INTO group_members (group_id, member_id, role, status) VALUES ");

    // Thêm creator là admin
    offset += snprintf(insert_members + offset, sizeof(insert_members) - offset,
                       "(%d, %d, 'admin', 'active')", group_id, creator_id);

    // Thêm các thành viên khác
    for (int i = 0; i < member_count; i++)
    {
        if (member_ids[i] == creator_id)
            continue;
        offset += snprintf(insert_members + offset, sizeof(insert_members) - offset,
                           ", (%d, %d, 'member', 'active')", group_id, member_ids[i]);
    }

    if (mysql_query(conn, insert_members))
    {
        LOG_ERROR("DB Add Group Members Fail: %s", mysql_error(conn));
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return -1;
    }

    // 4. Hoàn tất
    if (mysql_query(conn, "COMMIT"))
    {
        LOG_ERROR("Transaction Commit Fail: %s", mysql_error(conn));
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return -1;
    }

    db_release_conn(conn);
    LOG_INFO("Group '%s' (ID: %d) created by User %d with %d others.", name, group_id, creator_id, member_count);
    return group_id;
}

/**
 * @brief Lấy danh sách ID thành viên để broadcast
 */
int db_get_group_member_ids(int32_t group_id, int32_t *out_member_ids, int max_count)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return -1;

    char query[256];
    snprintf(query, sizeof(query),
             "SELECT member_id FROM group_members WHERE group_id = %d AND status = 'active'",
             group_id);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Group Members Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return -1;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    int count = 0;
    if (res)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(res)) && count < max_count)
        {
            out_member_ids[count++] = (int32_t)atoi(row[0]);
        }
        mysql_free_result(res);
    }

    db_release_conn(conn);
    return count;
}

/**
 * @brief Kiểm tra quyền hạn trong nhóm
 */
int db_is_group_member(int32_t group_id, int32_t user_id)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return -1;

    char query[256];
    snprintf(query, sizeof(query),
             "SELECT 1 FROM group_members WHERE group_id = %d AND member_id = %d AND status = 'active' LIMIT 1",
             group_id, user_id);

    int is_member = 0;
    if (mysql_query(conn, query))
    {
        LOG_ERROR("Check Group Member Error: %s", mysql_error(conn));
    }
    else
    {
        MYSQL_RES *res = mysql_store_result(conn);
        if (res)
        {
            if (mysql_num_rows(res) > 0)
                is_member = 1;
            mysql_free_result(res);
        }
    }

    db_release_conn(conn);
    return is_member;
}

/**
 * @brief Hàm nội bộ thực thi câu lệnh SQL INSERT/UPDATE.
 * Hàm này KHÔNG quản lý connection (không get/release), chỉ thực thi trên conn được truyền vào.
 *
 * @param conn Connection đang active (có thể đang trong transaction).
 * @return 0 nếu thành công, -1 nếu thất bại.
 */
static int internal_insert_member(MYSQL *conn, int32_t group_id, int32_t user_id)
{
    char query[256];
    snprintf(query, sizeof(query),
             "INSERT INTO group_members (group_id, member_id, role, status, joined_at) "
             "VALUES (%d, %d, 'member', 'active', NOW()) "
             "ON DUPLICATE KEY UPDATE status = 'active', joined_at = NOW()",
             group_id, user_id);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Internal Insert Member Fail (G:%d, U:%d): %s", group_id, user_id, mysql_error(conn));
        return -1;
    }
    return 0;
}

// Return:
//    >= 0: Số lượng thêm thành công
//    -1: Lỗi DB
//    -2: Quá giới hạn thành viên (Full)
int db_add_group_members(int32_t group_id, const int32_t *user_ids, int count)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return -1;

    // 1. Start Transaction
    if (mysql_query(conn, "START TRANSACTION"))
    {
        LOG_ERROR("Failed to start transaction");
        db_release_conn(conn);
        return -1;
    }

    // 2. CHECK SIZE HIỆN TẠI
    // Dùng FOR UPDATE để lock, không cho transaction khác sửa danh sách thành viên lúc này
    char query_count[256];
    snprintf(query_count, sizeof(query_count),
             "SELECT COUNT(*) FROM group_members WHERE group_id = %d AND status = 'active' FOR UPDATE",
             group_id);

    if (mysql_query(conn, query_count))
    {
        LOG_ERROR("Count members failed: %s", mysql_error(conn));
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return -1;
    }

    int current_members = 0;
    MYSQL_RES *res = mysql_store_result(conn);
    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row)
            current_members = atoi(row[0]);
        mysql_free_result(res);
    }

    // 3. Validate logic
    if (current_members + count > MAX_GROUP_MEMBERS)
    {
        LOG_WARN("Group %d is full (Current: %d, Adding: %d, Max: %d)",
                 group_id, current_members, count, MAX_GROUP_MEMBERS);
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return -2; // Mã lỗi quy ước: Group Full
    }

    // 4. Loop Insert
    int success_count = 0;
    for (int i = 0; i < count; i++)
    {
        if (internal_insert_member(conn, group_id, user_ids[i]) != 0)
        {
            LOG_ERROR("Insert failed at user %d. Rollback.", user_ids[i]);
            mysql_query(conn, "ROLLBACK");
            db_release_conn(conn);
            return -1;
        }
        success_count++;
    }

    // 5. Commit
    if (mysql_query(conn, "COMMIT"))
    {
        LOG_ERROR("Commit failed");
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return -1;
    }

    db_release_conn(conn);
    return success_count;
}

/**
 * @brief Rời nhóm (Chuyển status sang 'left')
 */
int db_leave_group(int32_t group_id, int32_t user_id)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[256];
    // LOGIC: Chỉ update status thành 'left', không xóa bản ghi
    snprintf(query, sizeof(query),
             "UPDATE group_members SET status = 'left' WHERE group_id = %d AND member_id = %d",
             group_id, user_id);

    int success = 0;
    if (mysql_query(conn, query))
    {
        LOG_ERROR("Leave Group Error: %s", mysql_error(conn));
    }
    else
    {
        // Kiểm tra xem có dòng nào được update không (nếu user chưa vào nhóm thì row=0)
        if (mysql_affected_rows(conn) > 0)
        {
            success = 1;
        }
    }

    db_release_conn(conn);
    return success;
}

int db_get_joined_groups(int user_id, GroupInfoPayload *groups_out, int limit, int offset)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[1024];
    // Chọn thông tin nhóm từ bảng groups (g)
    // Join với group_members (gm) để lọc ra những nhóm user_id đang tham gia
    snprintf(query, sizeof(query),
             "SELECT g.id, g.name, g.avatar_url "
             "FROM `groups` g "
             "JOIN group_members gm ON g.id = gm.group_id "
             "WHERE gm.member_id = %d AND gm.status = 'active' "
             "ORDER BY gm.joined_at DESC, g.id DESC " // Nhóm mới vào/tạo lên đầu
             "LIMIT %d OFFSET %d",
             user_id, limit, offset);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Joined Groups Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    int count = 0;
    if (res)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(res)) && count < limit)
        {
            groups_out[count].group_id = atoi(row[0]);

            // Copy tên nhóm
            if (row[1])
                strncpy(groups_out[count].group_name, row[1], MAX_GROUP_NAME - 1);
            else
                strcpy(groups_out[count].group_name, "Unknown Group");

            // Copy avatar
            if (row[2])
                strncpy(groups_out[count].avatar_url, row[2], MAX_AVATAR_LEN - 1);
            else
                strcpy(groups_out[count].avatar_url, "");

            count++;
        }
        mysql_free_result(res);
    }

    db_release_conn(conn);
    return count;
}

char *db_get_member_role(int32_t group_id, int32_t user_id)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return NULL;

    char query[256];
    snprintf(query, sizeof(query),
             "SELECT role FROM group_members WHERE group_id = %d AND member_id = %d AND status = 'active'",
             group_id, user_id);

    if (mysql_query(conn, query))
    {
        db_release_conn(conn);
        return NULL;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    char *role = NULL;
    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row && row[0])
        {
            role = strdup(row[0]); // Copy chuỗi ra vùng nhớ mới
        }
        mysql_free_result(res);
    }
    db_release_conn(conn);
    return role;
}

int db_kick_member(int32_t group_id, int32_t target_id)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[256];
    // Chỉ update status thành 'kicked'
    snprintf(query, sizeof(query),
             "UPDATE group_members SET status = 'kicked' "
             "WHERE group_id = %d AND member_id = %d",
             group_id, target_id);

    int success = 0;
    if (mysql_query(conn, query))
    {
        LOG_ERROR("Kick Member Error: %s", mysql_error(conn));
    }
    else
    {
        if (mysql_affected_rows(conn) > 0)
            success = 1;
    }

    db_release_conn(conn);
    return success;
}

int db_get_group_name(int32_t group_id, char *group_name)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[256];
    snprintf(query, sizeof(query), "SELECT name FROM `groups` WHERE id = %d", group_id);

    if (mysql_query(conn, query))
    {
        db_release_conn(conn);
        return 0;
    }

    int found = 0;
    MYSQL_RES *res = mysql_store_result(conn);
    if (res)
    {
        MYSQL_ROW row = mysql_fetch_row(res);
        if (row && row[0])
        {
            strncpy(group_name, row[0], MAX_GROUP_NAME - 1);
            group_name[MAX_GROUP_NAME - 1] = '\0';
            found = 1;
        }
        mysql_free_result(res);
    }

    db_release_conn(conn);
    return found;
}

int db_dissolve_group(int32_t group_id, int32_t requester_id)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    // 1. Check quyền Admin
    char *role = db_get_member_role(group_id, requester_id);
    int is_admin = (role != NULL && strcmp(role, "admin") == 0);
    if (role)
        free(role);

    if (!is_admin)
    {
        LOG_WARN("User %d is NOT admin of group %d. Dissolve denied.", requester_id, group_id);
        db_release_conn(conn);
        return -1; // Không có quyền
    }

    // 2. Start Transaction xóa dữ liệu
    if (mysql_query(conn, "START TRANSACTION"))
    {
        db_release_conn(conn);
        return 0;
    }

    char query_del_msg[256];
    char query_del_group[256];

    // A. Xóa tin nhắn trước
    snprintf(query_del_msg, sizeof(query_del_msg),
             "DELETE FROM messages WHERE receiver_id = %d AND chat_type = 'group'", group_id);

    if (mysql_query(conn, query_del_msg))
    {
        LOG_ERROR("Delete Msgs Failed: %s", mysql_error(conn));
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return 0;
    }

    // B. Xóa nhóm (Cascade sẽ tự xóa trong group_members)
    snprintf(query_del_group, sizeof(query_del_group),
             "DELETE FROM `groups` WHERE id = %d", group_id);

    if (mysql_query(conn, query_del_group))
    {
        LOG_ERROR("Delete Group Failed: %s", mysql_error(conn));
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return 0;
    }

    // C. Commit
    if (mysql_query(conn, "COMMIT"))
    {
        mysql_query(conn, "ROLLBACK");
        db_release_conn(conn);
        return 0;
    }

    db_release_conn(conn);
    return 1;
}

int db_get_group_members_info(int group_id, GroupMemberInfo *members_out, int limit, int offset)
{
    MYSQL *conn = db_get_conn();
    if (!conn)
        return 0;

    char query[1024];
    // Join để lấy thông tin User + Role trong Group
    // Sắp xếp: Admin lên đầu, sau đó đến tên A-Z
    snprintf(query, sizeof(query),
             "SELECT u.id, u.name, u.email, u.is_online, gm.role "
             "FROM group_members gm "
             "JOIN users u ON gm.member_id = u.id "
             "WHERE gm.group_id = %d AND gm.status = 'active' "
             "ORDER BY CASE WHEN gm.role = 'admin' THEN 0 ELSE 1 END, u.name ASC "
             "LIMIT %d OFFSET %d",
             group_id, limit, offset);

    if (mysql_query(conn, query))
    {
        LOG_ERROR("Get Group Members Error: %s", mysql_error(conn));
        db_release_conn(conn);
        return 0;
    }

    MYSQL_RES *res = mysql_store_result(conn);
    int count = 0;
    if (res)
    {
        MYSQL_ROW row;
        while ((row = mysql_fetch_row(res)) && count < limit)
        {
            members_out[count].user_id = atoi(row[0]);

            if (row[1])
                strncpy(members_out[count].name, row[1], MAX_NAME_LEN - 1);
            else
                strcpy(members_out[count].name, "Unknown");

            if (row[2])
                strncpy(members_out[count].email, row[2], MAX_EMAIL_LEN - 1);
            else
                strcpy(members_out[count].email, "");

            members_out[count].is_online = row[3] ? (int8_t)atoi(row[3]) : 0;

            if (row[4])
                strncpy(members_out[count].role, row[4], MAX_ROLE_LEN - 1);
            else
                strcpy(members_out[count].role, "member");

            count++;
        }
        mysql_free_result(res);
    }

    db_release_conn(conn);
    return count;
}
