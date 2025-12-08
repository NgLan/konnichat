#include "../include/server.h"
#include "../include/db_manager.h" // Để gọi hàm login/register
#include <time.h>

// Gửi thông báo trạng thái cho các bạn bè đang online
void notify_friends_status(int user_id, int is_online)
{
    // 1. Lấy danh sách bạn bè của user_id từ DB
    FriendInfo friends[100];
    int count = db_get_friends(user_id, friends, 100);

    // 2. Duyệt danh sách bạn bè
    for (int i = 0; i < count; i++)
    {
        int friend_id = friends[i].id;

        // 3. Kiểm tra xem người bạn đó có đang online không
        int sock = get_socket_by_user_id(friend_id);
        if (sock != -1)
        {
            // 4. Gửi gói tin
            PacketHeader header;
            header.command_type = CMD_NOTIFY_STATUS;
            header.payload_size = sizeof(StatusPayload);

            StatusPayload payload;
            payload.friend_id = user_id; // "Tôi" là người vừa đổi trạng thái
            payload.is_online = is_online;

            send(sock, &header, sizeof(PacketHeader), 0);
            send(sock, &payload, sizeof(StatusPayload), 0);

            printf("-> Đã báo cho User %d rằng User %d %s.\n",
                   friend_id, user_id, is_online ? "Online" : "Offline");
        }
    }
}

// Hàm lấy giờ hiện tại YYYY-MM-DD HH:MM:SS
void get_current_time_str(char *buffer)
{
    time_t rawtime;
    struct tm *timeinfo;
    time(&rawtime);
    timeinfo = localtime(&rawtime);
    strftime(buffer, 20, "%Y-%m-%d %H:%M:%S", timeinfo);
}

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
    int current_user_id = -1;

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
        // status = recv_all(sock, payload, header.payload_size);
        // if (status <= 0)
        // {
        //     free(payload);
        //     break;
        // }
        int received = 0;
        while (received < header.payload_size)
        {
            int r = recv(sock, (char *)payload + received, header.payload_size - received, 0);
            if (r <= 0)
                break;
            received += r;
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

            // Kiểm tra DB
            int userId = db_check_login(data->email, data->password, &userInfo);

            PacketHeader respHeader;
            respHeader.command_type = CMD_RESPONSE;

            if (userId > 0)
            {
                // --- TRƯỜNG HỢP THÀNH CÔNG ---
                // 1. Gửi Header báo kích thước lớn (Int + Struct)
                respHeader.payload_size = sizeof(int) + sizeof(UserInfo);
                send(sock, &respHeader, sizeof(PacketHeader), 0);

                // 2. Gửi ID
                send(sock, &userId, sizeof(int), 0);

                // 3. Gửi thông tin User
                send(sock, &userInfo, sizeof(UserInfo), 0);

                // 4. Cập nhật trạng thái Server
                current_user_id = userId;
                db_update_user_status(userId, 1);   // Online
                add_connected_client(sock, userId); // Lưu Session
                notify_friends_status(userId, 1);

                printf("-> User %d login thành công.\n", userId);
            }
            else
            {
                // --- TRƯỜNG HỢP THẤT BẠI ---
                // 1. Gửi Header báo kích thước nhỏ (Chỉ Int)
                respHeader.payload_size = sizeof(int);
                send(sock, &respHeader, sizeof(PacketHeader), 0);

                // 2. Gửi mã lỗi (0 hoặc -1)
                send(sock, &userId, sizeof(int), 0);

                printf("-> Login thất bại: %s\n", data->email);
            }
            break;
        }

        case CMD_SEND_MESSAGE:
        {
            ChatPayload *chat = (ChatPayload *)payload;
            printf("User %d gửi tin cho %d: %s\n", chat->sender_id, chat->receiver_id, chat->content);

            // 1. Lưu vào DB và lấy ID tin nhắn
            int new_msg_id = db_save_message(chat->sender_id, chat->receiver_id, chat->content);

            if (new_msg_id > 0) // Lưu thành công
            {
                // 2. LOGIC REALTIME: Kiểm tra xem người nhận có đang online không
                int receiver_sock = get_socket_by_user_id(chat->receiver_id);

                if (receiver_sock != -1)
                {
                    printf("-> Người nhận (%d) đang Online. Forwarding msgID: %d...\n", chat->receiver_id, new_msg_id);

                    MessageInfo msg;
                    // Gán ID thật vừa lấy từ DB
                    msg.message_id = new_msg_id;
                    msg.sender_id = chat->sender_id;
                    strncpy(msg.content, chat->content, 511);
                    get_current_time_str(msg.timestamp); // Lấy giờ hiện tại server gửi kèm

                    PacketHeader fwdHeader;
                    fwdHeader.command_type = CMD_RECEIVE_MESSAGE;
                    fwdHeader.payload_size = sizeof(MessageInfo);

                    // Gửi sang socket của người nhận
                    send(receiver_sock, &fwdHeader, sizeof(PacketHeader), 0);
                    send(receiver_sock, &msg, sizeof(MessageInfo), 0);

                    // 3. Cập nhật status thành 'delivered' trong DB
                    // vì đã đẩy được xuống socket người nhận
                    db_mark_message_delivered(new_msg_id);
                    printf("-> Đã đánh dấu tin nhắn %d là delivered.\n", new_msg_id);
                }
                else
                {
                    printf("-> Người nhận Offline. Tin nhắn %d lưu trạng thái 'sent' chờ sync.\n", new_msg_id);
                }
            }
            else
            {
                printf("-> Lỗi lưu tin nhắn vào DB.\n");
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

    printf("Socket %d ngắt kết nối.\n", sock);

    if (current_user_id != -1)
    {
        // 1. CẬP NHẬT OFFLINE
        db_update_user_status(current_user_id, 0);

        // 2. XÓA KHỎI SESSION
        remove_connected_client(sock);

        notify_friends_status(current_user_id, 0);
    }
    close(sock);
    return 0;
}
