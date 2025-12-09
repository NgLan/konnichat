#ifndef USER_REPO_H
#define USER_REPO_H

#include "../../include/protocol.h"

int db_register_user(const char *email, const char *password);
int db_check_login(const char *email, const char *password, UserInfoPayload *user_out);
void db_update_user_status(int user_id, int is_online);

#endif
