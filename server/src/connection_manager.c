#include "../include/connection_manager.h"
#include <pthread.h>
#include <stdlib.h>
#include "../include/uthash.h"

struct UserSocketMap
{
    int user_id;       // KEY
    int socket;        // VALUE
    UT_hash_handle hh; // Handle của uthash
};

static struct UserSocketMap *users_map = NULL;
static pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

void init_connection_manager()
{
    users_map = NULL;
}

int add_connected_client(int socket, int user_id)
{
    pthread_mutex_lock(&clients_mutex);

    // Kiểm tra giới hạn số lượng user 
    if (HASH_COUNT(users_map) >= MAX_CLIENTS)
    {
        pthread_mutex_unlock(&clients_mutex);
        return -1; // Server full
    }

    struct UserSocketMap *s;
    // Kiểm tra xem user này đã login chưa 
    HASH_FIND_INT(users_map, &user_id, s);

    if (s == NULL)
    {
        s = (struct UserSocketMap *)malloc(sizeof(struct UserSocketMap));
        s->user_id = user_id;
        HASH_ADD_INT(users_map, user_id, s);
    }
    s->socket = socket;

    pthread_mutex_unlock(&clients_mutex);
    return 0;
}

void remove_connected_client(int user_id)
{
    if (user_id <= 0)
        return; // Chưa login thì không cần xóa

    pthread_mutex_lock(&clients_mutex);

    struct UserSocketMap *s;
    HASH_FIND_INT(users_map, &user_id, s); 

    if (s != NULL)
    {
        HASH_DEL(users_map, s); // Xóa khỏi map
        free(s);                // Giải phóng bộ nhớ
    }

    pthread_mutex_unlock(&clients_mutex);
}

int get_socket_by_user_id(int user_id)
{
    int sock = -1;
    struct UserSocketMap *s;

    pthread_mutex_lock(&clients_mutex);
    HASH_FIND_INT(users_map, &user_id, s);
    if (s != NULL)
    {
        sock = s->socket;
    }
    pthread_mutex_unlock(&clients_mutex);
    return sock;
}
