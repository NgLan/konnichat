#include "../include/protocol.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <arpa/inet.h>

// Hàm gửi gói tin đầy đủ (Header + Payload)
void send_packet(int sock, int cmd_type, void *payload, int payload_size) {
    PacketHeader header;
    header.command_type = cmd_type;
    header.payload_size = payload_size;

    // 1. Gửi Header
    if(send(sock, &header, sizeof(PacketHeader), 0) < 0) {
        perror("Gửi Header thất bại");
        return;
    }

    // 2. Gửi Payload (nếu có)
    if (payload_size > 0) {
        if(send(sock, payload, payload_size, 0) < 0) {
            perror("Gửi Payload thất bại");
            return;
        }
    }
}

int main() {
    int sock;
    struct sockaddr_in serv_addr;

    // 1. Tạo socket
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("\n Socket creation error \n");
        return -1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);

    // Kết nối tới Localhost (127.0.0.1) vì đang chạy chung trên WSL
    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) {
        perror("\nInvalid address/ Address not supported \n");
        return -1;
    }

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("\nConnection Failed \n");
        return -1;
    }

    printf("=> Đã kết nối tới Server!\n");

    // --- KỊCH BẢN TEST 1: ĐĂNG KÝ ---
    printf("\n[TEST 1] Thử Đăng ký tài khoản 'testuser'...\n");
    LoginPayload regData;
    strcpy(regData.email, "testuser");
    strcpy(regData.password, "123456");

    send_packet(sock, CMD_REGISTER, &regData, sizeof(LoginPayload));

    // Nhận phản hồi
    PacketHeader respHeader;
    int respCode;
    read(sock, &respHeader, sizeof(PacketHeader)); // Đọc Header phản hồi
    read(sock, &respCode, sizeof(int));            // Đọc kết quả (1=OK, 0=Fail)

    if (respCode == 1) printf("=> Đăng ký THÀNH CÔNG!\n");
    else printf("=> Đăng ký THẤT BẠI (Có thể user đã tồn tại)\n");

    // --- KỊCH BẢN TEST 2: ĐĂNG NHẬP ---
    printf("\n[TEST 2] Thử Đăng nhập lại...\n");
    
    // Gửi lại gói tin Login (Vẫn dùng struct LoginPayload)
    send_packet(sock, CMD_LOGIN, &regData, sizeof(LoginPayload));

    // Nhận phản hồi
    read(sock, &respHeader, sizeof(PacketHeader));
    int userId;
    read(sock, &userId, sizeof(int)); // Server trả về UserID

    if (userId > 0) printf("=> Đăng nhập THÀNH CÔNG! UserID của bạn là: %d\n", userId);
    else printf("=> Đăng nhập THẤT BẠI (Sai user/pass)\n");

    close(sock);
    return 0;
}