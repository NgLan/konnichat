#include "../include/connection_manager.h"
#include <pthread.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <time.h>
#include "../include/uthash.h"

struct UserSocketMap
{
    int user_id;               // KEY
    int socket;                // VALUE
    uint64_t last_active_time; // Thời gian hoạt động cuối cùng (ms)
    UT_hash_handle hh;         // Handle của uthash
};

static struct UserSocketMap *users_map = NULL;
static pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

// Helper lấy giờ hệ thống (ms)
static uint64_t get_tick_count()
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)(ts.tv_sec * 1000) + (ts.tv_nsec / 1000000);
}

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
    s->last_active_time = get_tick_count();

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

void update_client_activity(int user_id)
{
    if (user_id <= 0)
        return;

    pthread_mutex_lock(&clients_mutex);
    struct UserSocketMap *s;
    HASH_FIND_INT(users_map, &user_id, s);
    if (s != NULL)
    {
        s->last_active_time = get_tick_count();
    }
    pthread_mutex_unlock(&clients_mutex);
}

int disconnect_inactive_clients(uint64_t timeout_ms)
{
    uint64_t now = get_tick_count();
    int count = 0;

    struct UserSocketMap *current_user, *tmp;

    pthread_mutex_lock(&clients_mutex);

    // Duyệt qua Hashmap 
    HASH_ITER(hh, users_map, current_user, tmp)
    {
        if (now - current_user->last_active_time > timeout_ms)
        {
            // TIMEOUT!
            // Chúng ta chỉ đóng Socket.
            // Thread handle_client đang recv() sẽ bị lỗi và tự dọn dẹp logic nghiệp vụ.
            // Ta không xóa khỏi map ở đây để tránh race condition với handle_client cleanup.

            shutdown(current_user->socket, SHUT_RDWR);
            close(current_user->socket);

            // Log
            printf("Kicked inactive user %d (Socket %d)\n", current_user->user_id, current_user->socket);
            count++;
        }
    }

    pthread_mutex_unlock(&clients_mutex);
    return count;
}
