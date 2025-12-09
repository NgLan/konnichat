/**
 * @file main.c
 * @brief Entry point of the Chat Server.
 */
#include "../include/database.h"
#include "../include/connection_manager.h"
#include "../include/discovery.h"       
#include "../include/tcp_server.h"      
#include "../include/utils/logger.h"
#include <pthread.h>
#include <stdlib.h>

int main()
{
    // 1. Khởi tạo các module nền tảng
    LOG_INFO("Starting KonniChat Server...");
    init_database();
    init_connection_manager();

    // 2. Khởi động dịch vụ tìm kiếm UDP (Chạy ngầm)
    pthread_t udp_thread;
    if (pthread_create(&udp_thread, NULL, udp_discovery_service, NULL) != 0) {
        LOG_ERROR("Failed to start UDP Discovery Service.");
    } else {
        pthread_detach(udp_thread);
    }

    // 3. Khởi động TCP Server (Chạy chính, block)
    start_tcp_server();

    return 0;
}
