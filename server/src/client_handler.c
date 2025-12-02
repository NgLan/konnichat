#include "../include/server.h"
#include "../include/db_manager.h" // Để gọi hàm login/register

// Hàm tiện ích: Đảm bảo nhận đủ N bytes từ stream (Task 2 Core)
int recv_all(int sock, void *buffer, int size)
{
    int total_received = 0;
    int bytes_left = size;
    char *ptr = (char *)buffer;

    while (total_received < size)
    {
        int received = recv(sock, ptr + total_received, bytes_left, 0);
        if (received <= 0)
            return received; // Lỗi hoặc ngắt kết nối
        total_received += received;
        bytes_left -= received;
    }
    return total_received;
}

void *handle_client(void *socket_desc)
{
    int sock = *(int *)socket_desc;
    free(socket_desc);

    PacketHeader header;

    // Vòng lặp chính xử lý từng gói tin
    while (1)
    {
        // 1. Đọc Header trước (Cố định 8 bytes)
        int status = recv_all(sock, &header, sizeof(PacketHeader));
        if (status <= 0)
            break; // Client ngắt kết nối

        // 2. Cấp phát bộ nhớ cho Payload dựa trên size trong Header
        void *payload = malloc(header.payload_size);
        if (payload == NULL)
            break; // Hết RAM? Thoát.

        // 3. Đọc tiếp Payload
        status = recv_all(sock, payload, header.payload_size);
        if (status <= 0)
        {
            free(payload);
            break;
        }

        // 4. Xử lý Logic theo loại lệnh (CommandType)
        switch (header.command_type)
        {
        case CMD_REGISTER:
        {
            LoginPayload *data = (LoginPayload *)payload;
            printf("Yêu cầu Đăng ký: %s\n", data->email);

            int success = db_register_user(data->email, data->password);

            // Gửi phản hồi về Client
            PacketHeader respHeader = {CMD_RESPONSE, sizeof(int)};
            int respCode = success ? 1 : 0; // 1: OK, 0: Fail
            send(sock, &respHeader, sizeof(PacketHeader), 0);
            send(sock, &respCode, sizeof(int), 0);
            break;
        }

        case CMD_GET_FRIEND_LIST:
        {
            GetFriendListPayload *req = (GetFriendListPayload *)payload;
            printf("User %d đang lấy danh sách bạn bè...\n", req->user_id);

            // 1. Tạo mảng tạm để chứa dữ liệu
            FriendInfo friends[100]; // Tối đa 100 bạn

            // 2. Query DB
            int count = db_get_friends(req->user_id, friends, 100);

            // 3. Chuẩn bị Header trả về
            PacketHeader respHeader;
            respHeader.command_type = CMD_RESPONSE;
            // Size = (4 byte chứa số lượng) + (Dữ liệu mảng struct)
            respHeader.payload_size = sizeof(int) + (count * sizeof(FriendInfo));

            send(sock, &respHeader, sizeof(PacketHeader), 0);

            // 4. Gửi số lượng trước
            send(sock, &count, sizeof(int), 0);

            // 5. Gửi mảng danh sách (nếu có bạn bè)
            if (count > 0)
            {
                send(sock, friends, count * sizeof(FriendInfo), 0);
            }

            printf("=> Đã gửi %d bạn bè.\n", count);
            break;
        }

        case CMD_LOGIN:
        {
            LoginPayload *data = (LoginPayload *)payload;
            printf("Yêu cầu Đăng nhập: %s\n", data->email);

            int userId = db_check_login(data->email, data->password);

            // Gửi phản hồi
            PacketHeader respHeader = {CMD_RESPONSE, sizeof(int)};
            send(sock, &respHeader, sizeof(PacketHeader), 0);
            send(sock, &userId, sizeof(int), 0); // Trả về ID user hoặc -1
            break;
        }

        case CMD_CHAT_SINGLE:
            // TODO: Xử lý chat sau
            printf("Tin nhắn chat nhận được (chưa xử lý forwarding)\n");
            break;

        default:
            printf("Lệnh không xác định: %d\n", header.command_type);
        }

        free(payload); // Quan trọng: Giải phóng bộ nhớ sau khi xử lý xong gói tin
    }

    printf("Client (Socket %d) ngắt kết nối.\n", sock);
    close(sock);
    return 0;
}