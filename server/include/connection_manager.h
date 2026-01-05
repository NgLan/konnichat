#ifndef CONNECTION_MANAGER_H
#define CONNECTION_MANAGER_H

#define MAX_CLIENTS 100

void init_connection_manager();
int add_connected_client(int socket, int user_id); // Trả về 0 nếu OK, -1 nếu full
void remove_connected_client(int user_id);
int get_socket_by_user_id(int user_id);

// Cập nhật thời gian hoạt động mới nhất cho user
void update_client_activity(int user_id);

// Quét và đóng kết nối user không hoạt động
// Trả về số lượng user bị đóng kết nối
int disconnect_inactive_clients(uint64_t timeout_ms);

#endif
