#include "../include/server.h"
#include "../include/db_manager.h" // Để gọi hàm login/register
#include "../include/client_manager.h" // Để quản lý user online

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

    int current_user_id = 0;
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

                // ... đoạn lấy userId từ DB ...
                
                // Gửi phản hồi
                PacketHeader respHeader = {CMD_RESPONSE, sizeof(int)};
                send(sock, &respHeader, sizeof(PacketHeader), 0);
                send(sock, &userId, sizeof(int), 0); // Trả về ID user hoặc -1

                if (userId > 0) {
                    current_user_id = userId;
                    add_online_user(userId, sock); // <--- THÊM DÒNG NÀY
                    printf("User %d đã online tại socket %d\n", userId, sock);
                }

                break;
            }

            case CMD_CHAT_SINGLE:
                // TODO: Xử lý chat sau
                printf("Tin nhắn chat nhận được (chưa xử lý forwarding)\n");
                break;

            case CMD_SEND_FRIEND_REQ:
            {
                FriendReqPayload *req = (FriendReqPayload *)payload;
                printf("[FRIEND] User %d muốn kết bạn với User %d\n", req->sender_id, req->receiver_id);

                // 1. Gọi Database
                // Kết quả trả về: >0 (Request ID), -1 (Đã là bạn), -2 (Đã gửi trước đó), 0 (Lỗi)
                int result_code = db_send_friend_request(req->sender_id, req->receiver_id);

                // 2. Phản hồi ngay cho Người Gửi (Sender) biết thành công hay thất bại
                PacketHeader respHeader = {CMD_RESPONSE, sizeof(int)};
                send(sock, &respHeader, sizeof(PacketHeader), 0);
                send(sock, &result_code, sizeof(int), 0);

                // 3. XỬ LÝ REAL-TIME: Báo ngay cho Người Nhận (Receiver) nếu thành công
                if (result_code > 0) {
                    // Tìm xem User B có đang online không
                    int receiver_sock = get_socket_by_user_id(req->receiver_id);
                    
                    if (receiver_sock > 0) {
                        // -- NGƯỜI NHẬN ĐANG ONLINE --
                        
                        // Chuẩn bị gói tin thông báo
                        PendingReqInfo notif;
                        notif.request_id = result_code; // ID lời mời để sau này accept/deny
                        notif.sender_id = req->sender_id;
                        
                        // Lấy tên người gửi để hiện thông báo cho đẹp (VD: "Manh Hung đã gửi lời mời...")
                        db_get_user_name(req->sender_id, notif.sender_name); 

                        // Gửi Header: CMD_NOTIFY_FRIEND_REQ (Server chủ động gửi)
                        PacketHeader notifHeader = {CMD_NOTIFY_FRIEND_REQ, sizeof(PendingReqInfo)};
                        send(receiver_sock, &notifHeader, sizeof(PacketHeader), 0);
                        
                        // Gửi Payload
                        send(receiver_sock, &notif, sizeof(PendingReqInfo), 0);
                        
                        printf("=> [REAL-TIME] Đã bắn thông báo kết bạn tới User %d (Socket %d)\n", req->receiver_id, receiver_sock);
                    } else {
                        printf("=> User %d đang Offline. Lời mời đã lưu DB, họ sẽ thấy khi mở danh sách chờ.\n", req->receiver_id);
                    }
                } else {
                    printf("=> Gửi kết bạn thất bại (Code: %d)\n", result_code);
                }
                break;
            }            

            // --- TASK 9: Lấy danh sách lời mời đang chờ ---
            case CMD_GET_PENDING_REQS:
            {
                // Payload gửi lên chỉ có user_id (Dùng lại struct GetFriendListPayload cho tiện vì giống nhau)
                GetFriendListPayload *req = (GetFriendListPayload *)payload;
                
                // 1. Chuẩn bị mảng đệm
                PendingReqInfo pending_list[50]; 
                
                // 2. Lấy dữ liệu từ DB
                int count = db_get_pending_requests(req->user_id, pending_list, 50);
                
                printf("User %d lấy danh sách lời mời chờ: tìm thấy %d yêu cầu.\n", req->user_id, count);

                // 3. Gửi Header trả về
                PacketHeader respHeader = {CMD_RESPONSE, sizeof(int) + (count * sizeof(PendingReqInfo))};
                send(sock, &respHeader, sizeof(PacketHeader), 0);

                // 4. Gửi số lượng
                send(sock, &count, sizeof(int), 0);

                // 5. Gửi danh sách chi tiết (nếu có)
                if (count > 0) {
                    send(sock, pending_list, count * sizeof(PendingReqInfo), 0);
                }
                break;
            }

            // --- TASK 9: Phản hồi lời mời (Chấp nhận / Từ chối) ---
            case CMD_RESPOND_FRIEND_REQ:
            {
                RespondReqPayload *resp = (RespondReqPayload *)payload;
                //id người gửi để gửi lại thông báo
                int sender_id_of_req = 0;

                // Gọi DB cập nhật trạng thái (và insert vào Friends nếu đồng ý)
               int success = db_respond_friend_request(resp->request_id, current_user_id, resp->is_accepted, &sender_id_of_req);
                
                printf("Xử lý phản hồi Request ID %d: %s -> Kết quả: %d\n", 
                        resp->request_id, 
                        resp->is_accepted ? "CHẤP NHẬN" : "TỪ CHỐI", 
                        success);

                // Phản hồi cho Client biết đã xử lý xong chưa
                PacketHeader respHeader = {CMD_RESPONSE, sizeof(int)};
                send(sock, &respHeader, sizeof(PacketHeader), 0);
                send(sock, &success, sizeof(int), 0);
                
                // (Nâng cao: Ở đây có thể bắn thông báo ngược lại cho người mời là "A đã chấp nhận", nhưng tạm thời làm cơ bản trước)
                if (success == 1 && resp->is_accepted == 1 && sender_id_of_req > 0) {
                    
                    int sender_sock = get_socket_by_user_id(sender_id_of_req);
                    if (sender_sock > 0) {
                        // Lấy thông tin người vừa chấp nhận (là current_user)
                        FriendInfo info;
                        info.id = current_user_id;
                        info.is_online = 1;
                        db_get_user_name(current_user_id, info.name);

                        // Gửi gói tin CMD_NOTIFY_REQ_ACCEPTED (51)
                        PacketHeader notifHeader = {CMD_NOTIFY_REQ_ACCEPTED, sizeof(FriendInfo)};
                        send(sender_sock, &notifHeader, sizeof(PacketHeader), 0);
                        send(sender_sock, &info, sizeof(FriendInfo), 0);
                        
                        printf("=> [NOTIF] Đã báo cho User %d biết User %d đã đồng ý.\n", sender_id_of_req, current_user_id);
                    }
                }

                break;
            }

            // --- TASK 10: Hủy kết bạn ---
            case CMD_UNFRIEND:
            {
                UnfriendPayload *req = (UnfriendPayload *)payload;
                
                // Gọi DB xóa 2 chiều
                int success = db_remove_friend(req->user_id, req->friend_id);
                
                printf("User %d hủy kết bạn với User %d -> Kết quả: %d\n", req->user_id, req->friend_id, success);

                PacketHeader respHeader = {CMD_RESPONSE, sizeof(int)};
                send(sock, &respHeader, sizeof(PacketHeader), 0);
                send(sock, &success, sizeof(int), 0);
                break;
            }

            case CMD_SEARCH_USERS:
            {
                SearchReqPayload *req = (SearchReqPayload *)payload;
                printf("User %d tìm kiếm với từ khóa: '%s'\n", req->current_user_id, req->keyword);

                // 1. Chuẩn bị mảng đệm để chứa kết quả (tối đa 20 kết quả cho nhẹ)
                UserSearchInfo results[20];

                // 2. Gọi DB để tìm
                // Lưu ý: Đảm bảo req->current_user_id là đúng người đang login
                // (Tốt nhất nên dùng biến current_user_id cục bộ của hàm handle_client để bảo mật hơn tin vào payload)
                int count = db_search_users(req->keyword, current_user_id, results, 20);

                printf("=> Tìm thấy %d người phù hợp.\n", count);

                // 3. Gửi Header trả về
                PacketHeader respHeader;
                respHeader.command_type = CMD_RESPONSE;
                respHeader.payload_size = sizeof(int) + (count * sizeof(UserSearchInfo));
                
                send(sock, &respHeader, sizeof(PacketHeader), 0);

                // 4. Gửi số lượng kết quả
                send(sock, &count, sizeof(int), 0);

                // 5. Gửi danh sách chi tiết
                if (count > 0) {
                    send(sock, results, count * sizeof(UserSearchInfo), 0);
                }
                break;
            }

            default:
                printf("Lệnh không xác định: %d\n", header.command_type);
        }

        free(payload); // Quan trọng: Giải phóng bộ nhớ sau khi xử lý xong gói tin
    }

    remove_online_user(sock); // <--- THÊM DÒNG NÀY để dọn dẹp khi client thoát
    printf("Client (Socket %d) ngắt kết nối.\n", sock);
    close(sock);
    return 0;
}