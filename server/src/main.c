#include "../include/server.h"
#include "../include/db_manager.h"

int main() {

    init_database();
    
    int server_fd, *new_sock;
    struct sockaddr_in address;
    int addrlen = sizeof(address);

    

    // 1. Tạo Socket (socket)
    // AF_INET: IPv4, SOCK_STREAM: TCP
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        perror("Khởi tạo socket thất bại");
        exit(EXIT_FAILURE);
    }

    // Cấu hình địa chỉ server
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY; // Chấp nhận kết nối từ mọi IP
    address.sin_port = htons(PORT);       // Gán cổng 8080

    // 2. Gán IP và Port vào Socket (bind)
    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("Lỗi Bind (Cổng có thể đang bận)");
        exit(EXIT_FAILURE);
    }

    // 3. Chờ kết nối (listen)
    // Số 3 là hàng đợi tối đa (backlog)
    if (listen(server_fd, 3) < 0) {
        perror("Lỗi Listen");
        exit(EXIT_FAILURE);
    }

    printf("Server đang chạy trên cổng %d...\n", PORT);

    // 4. Vòng lặp vô tận để chấp nhận Client (accept)
    while (1) {
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);
        
        // Cấp phát bộ nhớ cho socket mới để tránh xung đột dữ liệu giữa các luồng
        new_sock = malloc(sizeof(int));
        
        // accept() sẽ CHẶN tại đây cho đến khi có Client kết nối
        *new_sock = accept(server_fd, (struct sockaddr *)&client_addr, &client_len);
        
        if (*new_sock < 0) {
            perror("Lỗi Accept");
            free(new_sock);
            continue;
        }

        printf("Kết nối mới từ IP: %s\n", inet_ntoa(client_addr.sin_addr));

        // 5. Tạo luồng mới (Task 2 applied here)
        pthread_t thread_id;
        if (pthread_create(&thread_id, NULL, handle_client, (void*)new_sock) < 0) {
            perror("Không thể tạo luồng");
            free(new_sock);
            return 1;
        }

        // Detach để luồng tự giải phóng tài nguyên khi chạy xong, không cần main chờ (join)
        pthread_detach(thread_id);
    }

    return 0;
}