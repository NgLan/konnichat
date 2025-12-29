/**
 * @file main.c
 * @brief Entry point of the Chat Server.
 */
#include "../include/database.h"
#include "../include/connection_manager.h"
#include "../include/tcp_server.h"      
#include "../include/utils/logger.h"
#include "../include/repo/user_repo.h"
#include <pthread.h>
#include <stdlib.h>

int main()
{
    // 1. Khởi tạo các module nền tảng
    LOG_INFO("Starting KonniChat Server...");
    init_database();
    db_reset_all_users_offline();
    
    init_connection_manager();

    // 2. Khởi động TCP Server (Chạy chính, block)
    start_tcp_server();

    return 0;
}
