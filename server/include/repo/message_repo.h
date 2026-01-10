#ifndef MESSAGE_REPO_H
#define MESSAGE_REPO_H

#include "../../include/protocol.h"

int db_save_message(int sender_id, int receiver_id, const char *content, uint64_t created_at, int msg_type, const char *chat_type);
void db_mark_message_delivered(int message_id);
int db_get_offline_messages(int user_id, ChatPayload *messages_out, int limit);
int db_get_chat_history(int current_user_id, int target_id, int is_group, ChatPayload *messages_out, int limit, int offset);

/**
 * @brief Thu hồi tin nhắn.
 * @param msg_id ID tin nhắn
 * @param user_id ID người yêu cầu (để check quyền owner)
 * @param out_receiver_id Output ID người nhận (hoặc Group ID) để broadcast
 * @param out_chat_type Output loại chat ("private" / "group")
 * @return 1: Success, 0: Fail, -1: Not Owner
 */
int db_revoke_message(int msg_id, int user_id, int *out_receiver_id, char *out_chat_type);

#endif
