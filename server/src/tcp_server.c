#include "../include/protocol.h"
#include "../include/tcp_server.h"
#include "../include/client_handler.h"
#include "../include/utils/logger.h"
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <arpa/inet.h>
#include <unistd.h>

static void *monitor_thread_func(void *arg)
{
    LOG_INFO(">>> Monitor Thread Started. Timeout: %d ms", HEARTBEAT_TIMEOUT_MS);
    while (1)
    {
        sleep(5); // Cứ 5 giây dậy quét 1 lần

        int kicked = disconnect_inactive_clients(HEARTBEAT_TIMEOUT_MS);
        if (kicked > 0)
        {
            LOG_INFO("Monitor: Kicked %d inactive clients.", kicked);
        }
    }
    return NULL;
}

void start_tcp_server()
{
    int server_fd, *new_sock;
    struct sockaddr_in address;

    // 1. Tạo Socket
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0)
    {
        LOG_ERROR("TCP Socket creation failed");
        exit(EXIT_FAILURE);
    }

    // 2. Config Reuse Port
    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)))
    {
        LOG_WARN("setsockopt failed");
    }

    // 3. Bind
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(SERVER_PORT);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0)
    {
        LOG_ERROR("Bind failed on port %d", SERVER_PORT);
        exit(EXIT_FAILURE);
    }

    // 4. Listen
    if (listen(server_fd, 5) < 0)
    {
        LOG_ERROR("Listen failed");
        exit(EXIT_FAILURE);
    }

    LOG_INFO(">>> TCP Server started on port %d", SERVER_PORT);

    // Khởi động luồng Monitor
    pthread_t monitor_tid;
    if (pthread_create(&monitor_tid, NULL, monitor_thread_func, NULL) != 0)
    {
        LOG_ERROR("Failed to create Monitor Thread");
    }
    else
    {
        pthread_detach(monitor_tid);
    }

    // 5. Accept Loop
    while (1)
    {
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);

        new_sock = malloc(sizeof(int));
        if (new_sock == NULL)
        {
            LOG_ERROR("Malloc failed!");
            continue;
        }

        *new_sock = accept(server_fd, (struct sockaddr *)&client_addr, &client_len);

        if (*new_sock < 0)
        {
            LOG_WARN("Accept failed");
            free(new_sock);
            continue;
        }

        char client_ip[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &(client_addr.sin_addr), client_ip, INET_ADDRSTRLEN);
        LOG_INFO(">>> New Client Connected: %s", client_ip);

        // Tạo Thread cho Client
        pthread_t thread_id;
        if (pthread_create(&thread_id, NULL, handle_client, (void *)new_sock) < 0)
        {
            LOG_ERROR("Could not create thread for client");
            free(new_sock);
        }
        else
        {
            pthread_detach(thread_id);
        }
    }
}
