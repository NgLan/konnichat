#include "../include/server.h"
#include "../include/db_manager.h"

int main()
{
    init_database();

    // --- PHẦN 1: KHỞI ĐỘNG LUỒNG UDP (DISCOVERY) ---
    pthread_t udp_thread;
    if (pthread_create(&udp_thread, NULL, udp_discovery_service, NULL) != 0)
    {
        perror("Không thể tạo luồng UDP");
    }
    // Detach để nó tự chạy ngầm, không ảnh hưởng main thread
    pthread_detach(udp_thread);

    // --- PHẦN 2: KHỞI ĐỘNG TCP SERVER (CHAT CHÍNH) ---
    int server_fd, *new_sock;
    struct sockaddr_in address;
    int addrlen = sizeof(address);

    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0)
    {
        perror("Socket TCP thất bại");
        exit(EXIT_FAILURE);
    }

    // Set option để dùng lại port ngay khi tắt server (tránh lỗi Address already in use)
    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT); // Port 8080

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0)
    {
        perror("Lỗi Bind TCP");
        exit(EXIT_FAILURE);
    }

    if (listen(server_fd, 5) < 0)
    {
        perror("Lỗi Listen TCP");
        exit(EXIT_FAILURE);
    }

    printf(">>> Chat Server (TCP) đang chạy trên cổng %d...\n", PORT);

    // Vòng lặp chấp nhận kết nối TCP
    while (1)
    {
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);

        new_sock = malloc(sizeof(int));
        *new_sock = accept(server_fd, (struct sockaddr *)&client_addr, &client_len);

        if (*new_sock < 0)
        {
            perror("Lỗi Accept");
            free(new_sock);
            continue;
        }

        printf(">>> [TCP] Client mới kết nối: %s\n", inet_ntoa(client_addr.sin_addr));

        pthread_t thread_id;
        if (pthread_create(&thread_id, NULL, handle_client, (void *)new_sock) < 0)
        {
            perror("Không thể tạo luồng Client");
            free(new_sock);
        }
        else
        {
            pthread_detach(thread_id);
        }
    }

    return 0;
}
