#ifndef CONNECTION_MANAGER_H
#define CONNECTION_MANAGER_H

#define MAX_CLIENTS 100

void init_connection_manager();
void add_connected_client(int socket, int user_id); // Trả về 0 nếu OK, -1 nếu full
void remove_connected_client(int user_id);
int get_socket_by_user_id(int user_id);

#endif
