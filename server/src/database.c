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

/**
 * @brief Global MySQL connection handle.
 * Used by repository files to execute queries.
 */
MYSQL *conn;

/**
 * @brief Mutex for thread-safe database operations.
 */
pthread_mutex_t db_mutex = PTHREAD_MUTEX_INITIALIZER;

/**
 * @brief Initializes the database connection.
 *
 * Steps performed:
 * 1. Loads configuration from .env file.
 * 2. Validates the presence of required variables (HOST, USER, PASS, NAME).
 * 3. Initializes the MySQL object.
 * 4. Connects to the database server.
 * 5. Sets the character set to utf8mb4 (for emoji support).
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
    int port = (port_str != NULL) ? atoi(port_str) : 3306;

    // 3. Validate configuration
    if (!host || !user || !pass || !name)
    {
        LOG_ERROR("Missing database configuration in .env file.");
        LOG_ERROR("Required: DB_HOST, DB_USER, DB_PASS, DB_NAME.");
        exit(EXIT_FAILURE);
    }

    // 4. Initialize MySQL Handler
    conn = mysql_init(NULL);
    if (conn == NULL)
    {
        LOG_ERROR("mysql_init() failed. Insufficient memory?");
        exit(EXIT_FAILURE);
    }

    // 5. Establish Connection
    if (mysql_real_connect(conn, host, user, pass, name, port, NULL, 0) == NULL)
    {
        LOG_ERROR("MySQL Connection Failed: %s", mysql_error(conn));

        mysql_close(conn);
        exit(EXIT_FAILURE);
    }

    // 6. Set Character Set (Support Unicode/Emoji)
    if (mysql_set_character_set(conn, "utf8mb4"))
    {
        LOG_WARN("Failed to set character set to utf8mb4: %s", mysql_error(conn));
    }
    else
    {
        LOG_INFO("Character set set to utf8mb4.");
    }

    LOG_INFO("Database connected successfully. Host: %s, Port: %d, DB: %s", host, port, name);
}
