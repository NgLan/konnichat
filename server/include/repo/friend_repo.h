#ifndef FRIEND_REPO_H
#define FRIEND_REPO_H

#include "../../include/protocol.h"

int db_get_friends(int user_id, UserInfoPayload *friends_out, int max_count);

#endif
