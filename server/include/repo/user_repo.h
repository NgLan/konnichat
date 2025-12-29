#ifndef USER_REPO_H
#define USER_REPO_H

#include "../../include/protocol.h"

int db_register_user(const char *name, const char *email, const char *password);
int db_check_login(const char *email, const char *password, UserInfoPayload *user_out);
void db_update_user_status(int user_id, int is_online);
int db_search_users(const char *keyword, int current_id, UserSearchInfo *out_list, int limit, int offset);
void get_user_name_by_id(int user_id, char *name_buf, int buf_len);
int db_get_user_info_by_id(int user_id, UserInfoPayload *out_info);
void db_reset_all_users_offline();

#endif
