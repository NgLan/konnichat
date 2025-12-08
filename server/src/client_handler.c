#include "../include/server.h"
#include "../include/db_manager.h" // Để gọi hàm login/register

void send_pending_messages(int sock, int user_id)
{
    MessageInfo pending_msgs[50]; // Tối đa 50 tin chờ
    int count = db_get_pending_messages(user_id, pending_msgs, 50);

    if (count > 0)
    {
        printf("User %d có %d tin nhắn offline. Đang đồng bộ...\n", user_id, count);
        for (int i = 0; i < count; i++)
        {
            // 1. Chuẩn bị gói tin
            PacketHeader header;
            header.command_type = CMD_RECEIVE_MESSAGE; // Lệnh báo Client nhận tin
            header.payload_size = sizeof(MessageInfo);

            // 2. Gửi Header + Payload
            send(sock, &header, sizeof(PacketHeader), 0);
            send(sock, &pending_msgs[i], sizeof(MessageInfo), 0);

            // 3. Cập nhật DB là đã gửi
            db_mark_message_delivered(pending_msgs[i].message_id);

            // Ngủ để tránh dính gói tin (TCP Stream)
            usleep(10000);
        }
    }
}

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
            UserIdPayload *req = (UserIdPayload *)payload;
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

            UserInfo userInfo;
            memset(&userInfo, 0, sizeof(UserInfo));

            int userId = db_check_login(data->email, data->password, &userInfo);

            // Gửi phản hồi
            PacketHeader respHeader;
            respHeader.command_type = CMD_RESPONSE;

            if (userId > 0)
            {
                // Nếu thành công: Gửi kèm UserInfo
                respHeader.payload_size = sizeof(int) + sizeof(UserInfo);
                send(sock, &respHeader, sizeof(PacketHeader), 0);

                // Gửi ID (để check success > 0)
                send(sock, &userId, sizeof(int), 0);
                // Gửi trọn bộ UserInfo
                send(sock, &userInfo, sizeof(UserInfo), 0);
            }
            else
            {
                // Nếu thất bại: Chỉ gửi ID (-1)
                respHeader.payload_size = sizeof(int);
                send(sock, &respHeader, sizeof(PacketHeader), 0);
                send(sock, &userId, sizeof(int), 0);
            }
            break;
        }

        case CMD_SEND_MESSAGE:
        {
            ChatPayload *chat = (ChatPayload *)payload;
            printf("User %d gửi tin cho %d: %s\n", chat->sender_id, chat->receiver_id, chat->content);

            // Lưu vào DB
            if (db_save_message(chat->sender_id, chat->receiver_id, chat->content))
            {
                printf("-> Đã lưu tin nhắn vào DB.\n");
            }
            else
            {
                printf("-> Lỗi lưu tin nhắn.\n");
            }
            break;
        }

        case CMD_FETCH_OFFLINE_MSGS:
        {
            UserIdPayload *req = (UserIdPayload *)payload;
            printf("User %d yêu cầu sync tin nhắn offline...\n", req->user_id);

            // 1. Đếm số lượng tin
            int count = db_count_offline_messages(req->user_id);

            // 2. Lấy tin nhắn ra bộ nhớ đệm (tối đa 50 tin mỗi lần sync cho nhẹ)
            int limit = 50;
            if (count > limit)
                count = limit; // Cap lại nếu quá nhiều

            MessageInfo messages[50];
            if (count > 0)
            {
                db_get_offline_messages(req->user_id, messages, count);
            }

            // 3. Gửi Header Response
            // Payload gồm: 1 biến int (count) + mảng MessageInfo
            PacketHeader respHeader;
            respHeader.command_type = CMD_RESPONSE;
            respHeader.payload_size = sizeof(int) + (count * sizeof(MessageInfo));

            send(sock, &respHeader, sizeof(PacketHeader), 0);

            // 4. Gửi Count trước
            send(sock, &count, sizeof(int), 0);

            // 5. Gửi từng tin nhắn và đánh dấu đã gửi
            if (count > 0)
            {
                send(sock, messages, count * sizeof(MessageInfo), 0);

                // Update DB ngay sau khi gửi vào socket buffer
                for (int i = 0; i < count; i++)
                {
                    db_mark_message_delivered(messages[i].message_id);
                }
                printf("=> Đã gửi %d tin nhắn offline cho User %d.\n", count, req->user_id);
            }
            else
            {
                printf("=> User %d không có tin nhắn offline.\n", req->user_id);
            }
            break;
        }

        case CMD_GET_HISTORY:
        {
            HistoryPayload *req = (HistoryPayload *)payload;
            printf("User %d lấy lịch sử chat với %d...\n", req->user_id, req->friend_id);

            MessageInfo messages[50];
            int count = db_get_chat_history(req->user_id, req->friend_id, messages, 50);

            // Gửi Header Response
            PacketHeader respHeader;
            respHeader.command_type = CMD_RESPONSE;
            respHeader.payload_size = sizeof(int) + (count * sizeof(MessageInfo));

            send(sock, &respHeader, sizeof(PacketHeader), 0);
            send(sock, &count, sizeof(int), 0); // Gửi số lượng

            if (count > 0)
            {
                send(sock, messages, count * sizeof(MessageInfo), 0);
            }
            break;
        }

        default:
        {
            printf("Lệnh không xác định: %d\n", header.command_type);
        }
        }

        free(payload); // Giải phóng bộ nhớ sau khi xử lý xong gói tin
    }

    printf("Client (Socket %d) ngắt kết nối.\n", sock);
    close(sock);
    return 0;
}
