// File: test_client.c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include "../include/server.h" 

// --- HÀM MỚI: Nhận đủ dữ liệu (Fix lỗi hiển thị rác) ---
int recv_all(int sock, void *buffer, int size) {
    int total_received = 0;
    int bytes_left = size;
    char *ptr = (char *)buffer;
    while (total_received < size) {
        int received = recv(sock, ptr + total_received, bytes_left, 0);
        if (received <= 0) return received;
        total_received += received;
        bytes_left -= received;
    }
    return total_received;
}

void send_packet(int sock, int cmd_type, void *payload, int payload_size) {
    PacketHeader header = {cmd_type, payload_size};
    send(sock, &header, sizeof(PacketHeader), 0);
    if (payload_size > 0) {
        send(sock, payload, payload_size, 0);
    }
}

int recv_simple_response(int sock) {
    PacketHeader header;
    if (recv_all(sock, &header, sizeof(PacketHeader)) <= 0) return -1;
    
    int status;
    recv_all(sock, &status, sizeof(int));
    return status;
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        printf("Sử dụng: %s <email> <password>\n", argv[0]);
        return 1;
    }

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in serv_addr;
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        printf("Lỗi kết nối Server!\n");
        return 1;
    }

    // Login
    LoginPayload login = {0};
    strcpy(login.email, argv[1]);
    strcpy(login.password, argv[2]);
    send_packet(sock, CMD_LOGIN, &login, sizeof(LoginPayload));
    
    int my_id = recv_simple_response(sock);
    if (my_id <= 0) {
        printf("Đăng nhập thất bại.\n");
        return 1;
    }
    printf(">> Đăng nhập thành công. My ID: %d\n", my_id);

    while(1) {
        printf("\n--- MENU TEST (Đã Fix) ---\n");
        printf("1. Gửi lời mời kết bạn\n");
        printf("2. Xem danh sách lời mời đang chờ\n");
        printf("3. Phản hồi lời mời\n");
        printf("4. Hủy kết bạn\n");
        printf("5. Xem bạn bè\n");
        printf("6. Chờ thông báo (Realtime)\n");
        printf("0. Thoát\n");
        printf("Chọn: ");
        
        int choice;
        scanf("%d", &choice);
        if (choice == 0) break;

        switch (choice) {
            case 1: { 
                int target;
                printf("Nhập ID người muốn mời: ");
                scanf("%d", &target);
                FriendReqPayload req = {my_id, target};
                send_packet(sock, CMD_SEND_FRIEND_REQ, &req, sizeof(FriendReqPayload));
                int res = recv_simple_response(sock);
                if (res > 0) printf("=> Đã gửi. RequestID: %d\n", res);
                else if (res == -1) printf("=> Đã là bạn bè!\n");
                else if (res == -2) printf("=> Đã gửi rồi, đang chờ!\n");
                else printf("=> Thất bại (Lỗi DB hoặc Server)\n");
                break;
            }
            case 2: { 
                GetFriendListPayload req = {my_id};
                send_packet(sock, CMD_GET_PENDING_REQS, &req, sizeof(GetFriendListPayload));
                
                PacketHeader header;
                recv_all(sock, &header, sizeof(PacketHeader)); // Dùng recv_all
                
                int count;
                recv_all(sock, &count, sizeof(int)); // Dùng recv_all
                printf("=> Tìm thấy %d lời mời:\n", count);
                
                if (count > 0) {
                    PendingReqInfo *list = malloc(count * sizeof(PendingReqInfo));
                    // Quan trọng: Dùng recv_all để nhận hết mảng
                    recv_all(sock, list, count * sizeof(PendingReqInfo));
                    
                    for (int i=0; i<count; i++) {
                        printf("   [%d] Từ: %s (ID: %d) - ReqID: %d\n", 
                                i+1, list[i].sender_name, list[i].sender_id, list[i].request_id);
                    }
                    free(list);
                }
                break;
            }
            case 3: { 
                int req_id, accept;
                printf("RequestID: "); scanf("%d", &req_id);
                printf("1=Đồng ý, 0=Từ chối: "); scanf("%d", &accept);
                RespondReqPayload resp = {req_id, accept};
                send_packet(sock, CMD_RESPOND_FRIEND_REQ, &resp, sizeof(RespondReqPayload));
                int res = recv_simple_response(sock);
                printf("=> Kết quả: %s\n", res ? "OK" : "FAIL");
                break;
            }
            case 4: {
                int friend_id;
                printf("ID muốn hủy: "); scanf("%d", &friend_id);
                UnfriendPayload req = {my_id, friend_id};
                send_packet(sock, CMD_UNFRIEND, &req, sizeof(UnfriendPayload));
                int res = recv_simple_response(sock);
                printf("=> Kết quả: %s\n", res ? "OK" : "FAIL");
                break;
            }
            case 5: {
                GetFriendListPayload req = {my_id};
                send_packet(sock, CMD_GET_FRIEND_LIST, &req, sizeof(GetFriendListPayload));
                PacketHeader header;
                recv_all(sock, &header, sizeof(PacketHeader));
                int count;
                recv_all(sock, &count, sizeof(int));
                if (count > 0) {
                    FriendInfo *list = malloc(count * sizeof(FriendInfo));
                    recv_all(sock, list, count * sizeof(FriendInfo));
                    for(int i=0; i<count; i++) printf("   - %s (ID: %d)\n", list[i].name, list[i].id);
                    free(list);
                } else printf("=> Chưa có bạn bè.\n");
                break;
            }
            case 6: {
                printf("Đang chờ... (Ctrl+C thoát)\n");
                while(1) {
                    PacketHeader h;
                    if(recv_all(sock, &h, sizeof(PacketHeader)) <= 0) break;
                    if(h.command_type == CMD_NOTIFY_FRIEND_REQ) {
                        PendingReqInfo n;
                        recv_all(sock, &n, sizeof(PendingReqInfo));
                        printf("\n[TING TING] %s (ID %d) muốn kết bạn (ReqID: %d)\n", n.sender_name, n.sender_id, n.request_id);
                    } else if (h.command_type == CMD_NOTIFY_REQ_ACCEPTED) { // <--- THÊM CÁI NÀY
                        FriendInfo fr;
                        recv_all(sock, &fr, sizeof(FriendInfo));
                        printf("\n[TING TING] %s (ID %d) đã ĐỒNG Ý kết bạn với bạn!\n", fr.name, fr.id);
                    }
                    else {
                        char *tmp = malloc(h.payload_size);
                        recv_all(sock, tmp, h.payload_size);
                        free(tmp);
                    }
                }
                break;
            }
        }
    }
    close(sock);
    return 0;
}