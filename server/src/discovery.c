/**
 * @file discovery.c
 * @brief UDP Service Discovery implementation.
 *
 * This module allows clients to automatically find the server's IP address
 * within the local network (LAN) by broadcasting a specific message.
 *
 * Mechanism:
 * 1. Listen on UDP Port 8888.
 * 2. Receive broadcast message.
 * 3. Validate the message content.
 * 4. Reply to the sender, allowing them to capture the server's IP.
 */

#include "../include/discovery.h"
#include "../include/utils/logger.h"

#include <arpa/inet.h>

// --- CONFIGURATION ---
#define UDP_PORT 8888
#define BROADCAST_MSG "DISCOVER_KONNICHAT_SERVER"   // Mật khẩu (Client gửi)
#define RESPONSE_MSG "KONNICHAT_SERVER_AVAILABLE"   // Câu trả lời (Server gửi lại)
#define BUFFER_SIZE 1024

/**
 * @brief Main loop for UDP Discovery Service.
 *
 * This function is intended to run in a detached thread.
 * It creates a UDP socket bound to INADDR_ANY (all interfaces)
 * and continuously listens for broadcast packets.
 *
 * @param arg Unused thread argument.
 * @return void* Always NULL.
 */
void *udp_discovery_service(void *arg)
{
    int sockfd;
    struct sockaddr_in servaddr, cliaddr;
    char buffer[BUFFER_SIZE];
    socklen_t len;

    // 1. Create UDP Socket
    // AF_INET: IPv4, SOCK_DGRAM: UDP
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
    {
        LOG_ERROR("[UDP] Failed to create socket.");
        return NULL;
    }

    // 2. Configure Socket Options
    // SO_REUSEADDR allows the server to bind to the port again immediately after restart
    int opt = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0)
    {
        LOG_WARN("[UDP] setsockopt(SO_REUSEADDR) failed. Continuing anyway.");
    }

    // 3. Prepare Address Structure
    memset(&servaddr, 0, sizeof(servaddr));
    memset(&cliaddr, 0, sizeof(cliaddr));

    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = INADDR_ANY; // Listen on all network interfaces (Wifi, LAN, etc.)
    servaddr.sin_port = htons(UDP_PORT);

    // 4. Bind Socket to Port
    if (bind(sockfd, (const struct sockaddr *)&servaddr, sizeof(servaddr)) < 0)
    {
        LOG_ERROR("[UDP] Bind failed on port %d. Is another app using it?", UDP_PORT);
        close(sockfd);
        return NULL;
    }

    LOG_INFO("[UDP] Discovery Service started on port %d.", UDP_PORT);

    // 5. Main Listening Loop
    while (1)
    {
        len = sizeof(cliaddr);

        // Receive packet (Blocking call)
        int n = recvfrom(sockfd, (char *)buffer, BUFFER_SIZE, 0,
                         (struct sockaddr *)&cliaddr, &len);

        if (n > 0)
        {
            buffer[n] = '\0'; // Null-terminate string

            // Validate the received message (Password check)
            if (strncmp(buffer, BROADCAST_MSG, strlen(BROADCAST_MSG)) == 0)
            {
                char client_ip[INET_ADDRSTRLEN];
                inet_ntop(AF_INET, &(cliaddr.sin_addr), client_ip, INET_ADDRSTRLEN);

                LOG_INFO("[UDP] Discovery request from IP: %s", client_ip);

                // Send reply
                sendto(sockfd, RESPONSE_MSG, strlen(RESPONSE_MSG), 0,
                       (const struct sockaddr *)&cliaddr, len);
            }
            else
            {
                LOG_WARN("[UDP] Invalid message received: %s", buffer);
            }
        }
    }

    close(sockfd);
    return NULL;
}
