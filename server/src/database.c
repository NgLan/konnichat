/**
 * @file database.c
 * @brief Handles MySQL database connection and initialization.
 *
 * This file is responsible for loading environment variables,
 * establishing a connection to the MySQL server, and handling
 * connection errors using a standardized logging mechanism.
 */

#include "../include/database.h"
#include "../include/dotenv.h"
#include "../include/utils/logger.h"
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>

typedef struct
{
    MYSQL *conn;
    int is_busy; // 0: Free, 1: Busy
} DBConnection;

static DBConnection pool[POOL_SIZE];
static pthread_mutex_t pool_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t pool_cond = PTHREAD_COND_INITIALIZER;

static char g_host[128];
static char g_user[128];
static char g_pass[128];
static char g_name[128];
static int g_port = 3306;

/**
 * @brief Creates a new MySQL connection.
 * @return Pointer to MYSQL connection or NULL on failure.
 */
static MYSQL *create_db_connection()
{
    MYSQL *new_conn = mysql_init(NULL);
    if (new_conn == NULL)
    {
        LOG_ERROR("mysql_init failed (Out of memory?)");
        return NULL;
    }

    // Thiết lập Timeout (Ví dụ: 3s) để tránh treo server khi mất mạng
    unsigned int timeout = 3;
    mysql_options(new_conn, MYSQL_OPT_CONNECT_TIMEOUT, &timeout);

    if (mysql_real_connect(new_conn, g_host, g_user, g_pass, g_name, g_port, NULL, 0) == NULL)
    {
        LOG_ERROR("Failed to connect to MySQL: %s", mysql_error(new_conn));
        mysql_close(new_conn);
        return NULL;
    }

    mysql_set_character_set(new_conn, "utf8mb4");
    return new_conn;
}

/**
 * @brief Cleans up any pending results on the given connection.
 * This is useful to ensure the connection is in a clean state
 * before returning it to the pool.
 */
static void db_clean_connection(MYSQL *conn)
{
    MYSQL_RES *res;
    int status;

    // Vòng lặp đọc hết các result set còn treo
    do
    {
        res = mysql_store_result(conn);
        if (res)
        {
            mysql_free_result(res);
        }
    } while ((status = mysql_next_result(conn)) == 0);
}

/**
 * @brief Initializes the database connection.
 *
 * Steps performed:
 * 1. Loads configuration from .env file.
 * 2. Validates the presence of required variables (HOST, USER, PASS, NAME).
 * 3. Establishes a pool of connections to the MySQL database.
 *
 * If any step fails, the program logs the error and exits with status 1.
 */
void init_database()
{
    // 1. Load environment variables
    env_load(".env");

    // 2. Retrieve configuration
    char *host = getenv("DB_HOST");
    char *user = getenv("DB_USER");
    char *pass = getenv("DB_PASS");
    char *name = getenv("DB_NAME");
    char *port_str = getenv("DB_PORT");

    // 3. Validate configuration
    if (!host || !user || !pass || !name)
    {
        LOG_ERROR("Missing database configuration in .env file.");
        LOG_ERROR("Required: DB_HOST, DB_USER, DB_PASS, DB_NAME.");
        exit(EXIT_FAILURE);
    }

    strncpy(g_host, host, sizeof(g_host) - 1);
    strncpy(g_user, user, sizeof(g_user) - 1);
    strncpy(g_pass, pass, sizeof(g_pass) - 1);
    strncpy(g_name, name, sizeof(g_name) - 1);
    if (port_str)
        g_port = atoi(port_str);
    else
        g_port = 3306;

    // 4. Init Pool Loops
    int success_count = 0;
    for (int i = 0; i < POOL_SIZE; i++)
    {
        pool[i].conn = create_db_connection();
        pool[i].is_busy = 0;

        if (pool[i].conn)
            success_count++;
    }

    if (success_count == 0)
    {
        LOG_ERROR("Failed to initialize any DB connection. Server stopping.");
        exit(EXIT_FAILURE);
    }

    LOG_INFO("Database Pool initialized. Active connections: %d/%d", success_count, POOL_SIZE);
}

/**
 * @brief Closes all database connections in the pool.
 *
 * This function should be called when the server is shutting down
 * to properly release all database resources.
 */
void close_database()
{
    pthread_mutex_lock(&pool_mutex);
    for (int i = 0; i < POOL_SIZE; i++)
    {
        if (pool[i].conn)
        {
            mysql_close(pool[i].conn);
            pool[i].conn = NULL;
        }
    }
    pthread_mutex_unlock(&pool_mutex);
}

/**
 * @brief Retrieves a free database connection from the pool.
 *
 * This function blocks if no connections are available until one is released.
 *
 * @return A pointer to a MYSQL connection object.
 */
MYSQL *db_get_conn()
{
    pthread_mutex_lock(&pool_mutex);

    while (1)
    {
        // Tìm connection rảnh
        for (int i = 0; i < POOL_SIZE; i++)
        {
            if (!pool[i].is_busy)
            {
                pool[i].is_busy = 1;

                // --- RECONNECT LOGIC ---
                // 1. Kiểm tra xem connection còn sống không?
                if (pool[i].conn == NULL || mysql_ping(pool[i].conn) != 0)
                {
                    LOG_WARN("Connection %d is dead or null. Reconnecting...", i);

                    // Đóng cái cũ (nếu có)
                    if (pool[i].conn)
                    {
                        mysql_close(pool[i].conn);
                    }

                    // Tạo cái mới
                    pool[i].conn = create_db_connection();

                    // Nếu tái kết nối thất bại
                    if (pool[i].conn == NULL)
                    {
                        LOG_ERROR("Reconnection failed for slot %d", i);
                        pool[i].is_busy = 0; // Trả lại trạng thái rảnh (nhưng null)
                        // Tiếp tục vòng lặp để tìm slot khác hy vọng còn sống
                        continue;
                    }

                    LOG_INFO("Reconnection successful for slot %d", i);
                }

                // 2. Nếu kết nối còn sống -> Dọn dẹp rác
                else {
                    db_clean_connection(pool[i].conn);
                }
                // ------------------------------

                pthread_mutex_unlock(&pool_mutex);
                return pool[i].conn;
            }
        }

        // Nếu full, chờ tín hiệu
        pthread_cond_wait(&pool_cond, &pool_mutex);
    }
}

/**
 * @brief Releases a database connection back to the pool.
 *
 * @param conn The MYSQL connection object to be released.
 */
void db_release_conn(MYSQL *conn)
{
    if (!conn)
        return;

    pthread_mutex_lock(&pool_mutex);
    for (int i = 0; i < POOL_SIZE; i++)
    {
        if (pool[i].conn == conn)
        {
            pool[i].is_busy = 0;
            pthread_cond_signal(&pool_cond); // Đánh thức thread đang chờ
            break;
        }
    }
    pthread_mutex_unlock(&pool_mutex);
}
