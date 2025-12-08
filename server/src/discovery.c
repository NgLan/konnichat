// src/discovery.c
#include "../include/server.h"

#define UDP_PORT 8888
#define BROADCAST_MSG "TIM_SERVER_KONNICHAT" // Mật khẩu phải khớp Android
#define RESPONSE_MSG "SERVER_DAY_NE"         // Câu trả lời khớp Android

void *udp_discovery_service(void *arg)
{
    int sockfd;
    struct sockaddr_in servaddr, cliaddr;
    char buffer[1024];
    socklen_t len;

    // 1. Tạo socket UDP
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
    {
        perror("[UDP] Socket creation failed");
        return NULL;
    }

    // 2. Cho phép tái sử dụng port (tránh lỗi Address already in use khi restart)
    int opt = 1;
    setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    memset(&servaddr, 0, sizeof(servaddr));
    memset(&cliaddr, 0, sizeof(cliaddr));

    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = INADDR_ANY; // Nghe mọi IP
    servaddr.sin_port = htons(UDP_PORT);

    // 3. Bind
    if (bind(sockfd, (const struct sockaddr *)&servaddr, sizeof(servaddr)) < 0)
    {
        perror("[UDP] Bind failed");
        return NULL;
    }

    printf(">>> [UDP] Dịch vụ tìm IP đang chạy port %d...\n", UDP_PORT);

    while (1)
    {
        len = sizeof(cliaddr);
        // 4. Nhận gói tin (Block cho đến khi có tin nhắn)
        int n = recvfrom(sockfd, (char *)buffer, 1024, 0, (struct sockaddr *)&cliaddr, &len);
        buffer[n] = '\0';

        // 5. Kiểm tra mật khẩu
        if (strncmp(buffer, BROADCAST_MSG, strlen(BROADCAST_MSG)) == 0)
        {
            printf(">>> [UDP] Nhận tín hiệu từ Client IP: %s\n", inet_ntoa(cliaddr.sin_addr));

            // 6. Trả lời
            sendto(sockfd, RESPONSE_MSG, strlen(RESPONSE_MSG), 0, (const struct sockaddr *)&cliaddr, len);
        }
    }

    close(sockfd);
    return NULL;
}
