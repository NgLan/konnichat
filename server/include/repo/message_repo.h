#ifndef MESSAGE_REPO_H
#define MESSAGE_REPO_H

#include "../../include/protocol.h"

int db_save_message(int sender_id, int receiver_id, const char *content, uint64_t created_at, int msg_type, const char *chat_type);
void db_mark_message_delivered(int message_id);
int db_get_offline_messages(int user_id, ChatPayload *messages_out, int limit);
// int db_get_chat_history(int user1, int user2, ChatPayload *messages_out, int limit);

#endif
