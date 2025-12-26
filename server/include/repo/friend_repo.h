#ifndef FRIEND_REPO_H
#define FRIEND_REPO_H

#include "../../include/protocol.h"

// Lấy danh sách bạn bè với phân trang
int db_get_friends(int user_id, int offset, int limit, UserInfoPayload *friends_out);

// Lấy danh sách ID bạn bè
int db_get_friend_ids(int user_id, int *ids_out, int limit, int offset);

// Gửi lời mời kết bạn. Trả về RequestID (>0) nếu thành công.
int db_send_friend_request(int sender_id, int target_id);

// Lấy danh sách lời mời đang chờ duyệt
int db_get_pending_requests(int user_id, PendingReqInfo *list_out, int max_count);

// Phản hồi lời mời. Trả về 1 nếu thành công. Gán sender_id_out để báo notif.
int db_respond_friend_request(int request_id, int current_user_id, int is_accepted, int *sender_id_out);

// Hủy kết bạn
int db_remove_friend(int user_id, int friend_id);

#endif
