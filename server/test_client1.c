/**
 * @file test_client_full.c
 * @brief Client test toàn diện các chức năng KonniChat.
 */

#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <time.h>
#include <stdint.h>
#include <pthread.h>

// --- CẤU HÌNH ---
#define SERVER_IP "127.0.0.1"
#define SERVER_PORT 8080 

// --- PROTOCOL CONSTANTS ---
#define SERVER_PROTOCOL_VERSION 1

// Command IDs (Khớp với Server)
#define CMD_REGISTER 10
#define CMD_LOGIN 11
#define CMD_SEND_MESSAGE 20
#define CMD_RECEIVE_MESSAGE 21
#define CMD_GET_FRIEND_LIST 40
#define CMD_SEND_FRIEND_REQ 41
#define CMD_RESPOND_FRIEND_REQ 42
#define CMD_UNFRIEND 43
#define CMD_SEARCH_USERS 45
#define CMD_GET_PENDING_REQS 46
#define CMD_FETCH_OFFLINE_MSGS 51
#define CMD_RESPONSE 99
#define CMD_NOTIFY_FRIEND_REQ 50
#define CMD_NOTIFY_REQ_ACCEPTED 55

#define STATUS_SUCCESS 0

// --- STRUCTS (Khớp với Server) ---
typedef struct __attribute__((packed)) {
    int32_t version;        
    int32_t command_type;   
    int32_t payload_size;   
    int32_t request_id;     
    int32_t status_code;    
    uint64_t timestamp;     
} PacketHeader;

typedef struct __attribute__((packed)) {
    char name[64];
    char email[256];
    char password[64];
} AuthPayload;

typedef struct __attribute__((packed)) {
    int32_t user_id;
    char name[64];
    char email[256];
    int8_t is_online;
} UserInfoPayload;

typedef struct __attribute__((packed)) {
    char keyword[50];
} SearchReqPayload;

typedef struct __attribute__((packed)) {
    int32_t user_id;
    char name[64];
    char email[256];
} UserSearchInfo;

typedef struct __attribute__((packed)) {
    int32_t target_id; // friend_id hoặc user_id muốn kết bạn
} FriendReqPayload; // Dùng cho SendReq và Unfriend

typedef struct __attribute__((packed)) {
    int32_t request_id;
    int32_t sender_id;
    char sender_name[64];
} PendingReqInfo;

typedef struct __attribute__((packed)) {
    int32_t request_id;
    int8_t is_accepted;
} FriendRespondPayload;

typedef struct __attribute__((packed)) {
    int32_t message_id;
    int32_t sender_id;
    int32_t receiver_id;
    int32_t msg_type;
    char content[1024];
    uint64_t created_at;
} ChatPayload;

// --- GLOBALS ---
int sock = -1;
int current_request_id = 1;
int my_user_id = -1;
char my_user_name[64];
volatile int is_chatting = 0;

// --- HELPERS ---

uint64_t current_time_ms() {
    return (uint64_t)time(NULL) * 1000;
}

void send_packet(int cmd_type, void *payload, int payload_size) {
    PacketHeader header;
    memset(&header, 0, sizeof(PacketHeader));
    
    header.version = SERVER_PROTOCOL_VERSION;
    header.command_type = cmd_type;
    header.payload_size = payload_size;
    header.request_id = current_request_id++;
    header.status_code = 0; 
    header.timestamp = current_time_ms();

    if (send(sock, &header, sizeof(PacketHeader), 0) < 0) {
        perror("Lỗi gửi Header");
        return;
    }

    if (payload_size > 0 && payload != NULL) {
        if (send(sock, payload, payload_size, 0) < 0) {
            perror("Lỗi gửi Payload");
        }
    }
}

void *chat_listener(void *arg) {
    PacketHeader header;
    while (is_chatting) {
        int n = recv(sock, &header, sizeof(PacketHeader), 0);
        if (n <= 0) {
            printf("\n[!] Mất kết nối Server.\n");
            exit(0);
        }

        // Nếu có payload, đọc payload
        void *buffer = NULL;
        if (header.payload_size > 0) {
            buffer = malloc(header.payload_size);
            int total = 0;
            while(total < header.payload_size) {
                int r = recv(sock, (char*)buffer + total, header.payload_size - total, 0);
                if (r <= 0) break;
                total += r;
            }
        }

        // XỬ LÝ TIN NHẮN ĐẾN
        if (header.command_type == CMD_RECEIVE_MESSAGE) {
            ChatPayload *msg = (ChatPayload *)buffer;
            
            // Kỹ thuật in đè dòng (UI Trick):
            // \r: Về đầu dòng
            // \033[K: Xóa nội dung dòng hiện tại (để xóa chữ 'Me: ' đang gõ dở)
            printf("\r\033[K"); 
            
            // In tin nhắn người kia
            printf("[User %d]: %s\n", msg->sender_id, msg->content);
            
            // In lại dấu nhắc nhập liệu để bạn gõ tiếp
            printf("Me: "); 
            fflush(stdout); // Đẩy buffer ra màn hình ngay
        }
        else if (header.command_type == CMD_NOTIFY_FRIEND_REQ) {
             printf("\n\r[THÔNG BÁO] Có lời mời kết bạn mới!\nMe: ");
             fflush(stdout);
        }

        if (buffer) free(buffer);
    }
    return NULL;
}

// Hàm nhận phản hồi generic, trả về buffer chứa payload
// Caller phải free buffer sau khi dùng
// Hàm nhận phản hồi thông minh: Tự động lọc Notification
void* receive_response_generic(int *out_size) {
    PacketHeader header;
    while (1) { // <--- Thêm vòng lặp vô tận để chờ đúng gói tin
        int n = recv(sock, &header, sizeof(PacketHeader), 0);
        if (n <= 0) {
            printf("\n[!] Mất kết nối Server.\n");
            exit(0);
        }

        // 1. Nếu là Notification -> In ra và TIẾP TỤC chờ (continue)
        if (header.command_type == CMD_NOTIFY_FRIEND_REQ) {
            printf("\n\n>>> [THÔNG BÁO REAL-TIME] Có lời mời kết bạn mới! (Chọn menu 6 để xem)\n");
            
            // Phải đọc hết phần payload của notification để dọn sạch socket
            if (header.payload_size > 0) {
                char garbage[4096]; 
                recv(sock, garbage, header.payload_size, 0); 
            }
            printf(">> Đang chờ phản hồi từ lệnh chính...\n");
            continue; // <--- Quan trọng: Quay lại đầu vòng lặp
        }
        
        if (header.command_type == CMD_NOTIFY_REQ_ACCEPTED) {
            printf("\n\n>>> [THÔNG BÁO REAL-TIME] Lời mời kết bạn đã được chấp nhận!\n");
             if (header.payload_size > 0) {
                char garbage[4096]; 
                recv(sock, garbage, header.payload_size, 0); 
            }
            printf(">> Đang chờ phản hồi từ lệnh chính...\n");
            continue;
        }

        // 2. Nếu là Response thật (CMD_RESPONSE) -> Thoát vòng lặp để xử lý
        if (header.command_type == CMD_RESPONSE) {
            break; 
        }
        
        // Các loại gói tin lạ khác -> Bỏ qua
        if (header.payload_size > 0) {
             char garbage[4096];
             recv(sock, garbage, header.payload_size, 0);
        }
    }

    // --- Xử lý gói tin RESPONSE thật sự ---
    if (header.payload_size > 0) {
        void *buffer = malloc(header.payload_size);
        int total = 0;
        while(total < header.payload_size) {
            int r = recv(sock, (char*)buffer + total, header.payload_size - total, 0);
            if (r <= 0) break;
            total += r;
        }
        
        if (out_size) *out_size = header.payload_size;
        
        if (header.status_code != STATUS_SUCCESS) {
            printf(">> Server trả về Lỗi (Code: %d)\n", header.status_code);
            free(buffer);
            return NULL;
        }
        return buffer;
    }
    
    if (header.status_code != STATUS_SUCCESS) {
        printf(">> Thất bại. Server Code: %d\n", header.status_code);
    } else {
        printf(">> Thành công.\n");
    }
    return NULL;
}
// --- FEATURES ---



void do_register() {
    AuthPayload auth;
    memset(&auth, 0, sizeof(AuthPayload));
    printf("\n--- ĐĂNG KÝ ---\n");
    printf("Tên hiển thị: "); scanf(" %[^\n]s", auth.name);
    printf("Email: "); scanf("%s", auth.email);
    printf("Password: "); scanf("%s", auth.password);

    send_packet(CMD_REGISTER, &auth, sizeof(AuthPayload));
    
    void* resp = receive_response_generic(NULL);
    if(resp) free(resp); 
}

void do_login() {
    AuthPayload auth;
    memset(&auth, 0, sizeof(AuthPayload));
    printf("\n--- ĐĂNG NHẬP ---\n");
    printf("Email: "); scanf("%s", auth.email);
    printf("Password: "); scanf("%s", auth.password);

    send_packet(CMD_LOGIN, &auth, sizeof(AuthPayload));

    int size = 0;
    UserInfoPayload *user = (UserInfoPayload *)receive_response_generic(&size);
    if (user) {
        my_user_id = user->user_id;
        strcpy(my_user_name, user->name);
        printf(">> Login OK! Xin chào %s (ID: %d)\n", my_user_name, my_user_id);
        free(user);
    }
}

void do_search() {
    SearchReqPayload req;
    printf("\n--- TÌM KIẾM NGƯỜI DÙNG ---\n");
    printf("Nhập từ khóa (tên): "); scanf(" %[^\n]s", req.keyword);

    send_packet(CMD_SEARCH_USERS, &req, sizeof(SearchReqPayload));

    int size = 0;
    void *buffer = receive_response_generic(&size);
    if (buffer) {
        int32_t count;
        memcpy(&count, buffer, sizeof(int32_t));
        
        printf(">> Tìm thấy %d kết quả:\n", count);
        if (count > 0) {
            UserSearchInfo *list = (UserSearchInfo *)((char*)buffer + sizeof(int32_t));
            for (int i=0; i<count; i++) {
                printf("  [%d] %s (%s)\n", list[i].user_id, list[i].name, list[i].email);
            }
        }
        free(buffer);
    }
}

void do_send_friend_req() {
    FriendReqPayload req;
    printf("\n--- GỬI LỜI MỜI KẾT BẠN ---\n");
    printf("Nhập ID người muốn kết bạn: "); scanf("%d", &req.target_id);

    send_packet(CMD_SEND_FRIEND_REQ, &req, sizeof(FriendReqPayload));
    
    void *resp = receive_response_generic(NULL); // Chỉ cần check status success
    if (resp) free(resp);
}

void do_get_pending_reqs() {
    printf("\n--- DANH SÁCH LỜI MỜI ĐANG CHỜ ---\n");
    send_packet(CMD_GET_PENDING_REQS, NULL, 0);

    int size = 0;
    void *buffer = receive_response_generic(&size);
    if (buffer) {
        int32_t count;
        memcpy(&count, buffer, sizeof(int32_t));
        
        printf(">> Bạn có %d lời mời:\n", count);
        if (count > 0) {
            PendingReqInfo *list = (PendingReqInfo *)((char*)buffer + sizeof(int32_t));
            for (int i=0; i<count; i++) {
                printf("  [ReqID: %d] Từ UserID: %d (%s)\n", list[i].request_id, list[i].sender_id, list[i].sender_name);
            }
        }
        free(buffer);
    }
}

void do_respond_req() {
    FriendRespondPayload resp;
    printf("\n--- DUYỆT LỜI MỜI ---\n");
    printf("Nhập Request ID: "); scanf("%d", &resp.request_id);
    printf("Hành động (1: Đồng ý, 0: Từ chối): "); 
    int temp; scanf("%d", &temp);
    resp.is_accepted = (int8_t)temp;

    send_packet(CMD_RESPOND_FRIEND_REQ, &resp, sizeof(FriendRespondPayload));
    
    void *buf = receive_response_generic(NULL);
    if (buf) free(buf);
}

void do_get_friends() {
    printf("\n--- DANH SÁCH BẠN BÈ ---\n");
    send_packet(CMD_GET_FRIEND_LIST, NULL, 0);

    int size = 0;
    void *buffer = receive_response_generic(&size);
    if (buffer) {
        int32_t count;
        memcpy(&count, buffer, sizeof(int32_t));
        
        printf(">> Bạn có %d bạn bè:\n", count);
        if (count > 0) {
            UserInfoPayload *list = (UserInfoPayload *)((char*)buffer + sizeof(int32_t));
            for (int i=0; i<count; i++) {
                printf("  - ID: %d | %s | %s | %s\n", 
                        list[i].user_id, list[i].name, list[i].email, 
                        list[i].is_online ? "ONLINE" : "Offline");
            }
        }
        free(buffer);
    }
}

void do_unfriend() {
    FriendReqPayload req; // Dùng struct này có chứa target_id là đủ
    printf("\n--- HỦY KẾT BẠN ---\n");
    printf("Nhập ID bạn muốn xóa: "); scanf("%d", &req.target_id);
    
    send_packet(CMD_UNFRIEND, &req, sizeof(FriendReqPayload));
    void *buf = receive_response_generic(NULL);
    if (buf) free(buf);
}

void do_chat_live() {
    if (my_user_id == -1) { printf("Chưa login!\n"); return; }

    int target_id;
    printf("\n--- LIVE CHAT MODE (Gõ '/exit' để thoát) ---\n");
    printf("Nhập ID người muốn chat: "); 
    scanf("%d", &target_id);
    getchar(); // Xóa bộ đệm

    // 1. Bật cờ chat và tạo luồng nghe
    is_chatting = 1;
    pthread_t thread_id;
    if (pthread_create(&thread_id, NULL, chat_listener, NULL) != 0) {
        printf("Lỗi tạo luồng chat!\n");
        return;
    }

    printf(">> Đã vào phòng chat với User %d. Bắt đầu chat đi!\n", target_id);

    // 2. Vòng lặp gửi tin (Main Thread)
    char buffer[1024];
    while (1) {
        printf("Me: ");
        if (fgets(buffer, sizeof(buffer), stdin) == NULL) break;
        
        // Xóa ký tự xuống dòng
        buffer[strcspn(buffer, "\n")] = 0;

        // Lệnh thoát chat
        if (strcmp(buffer, "/exit") == 0) {
            break;
        }

        if (strlen(buffer) == 0) continue;

        // Đóng gói tin nhắn
        ChatPayload msg;
        memset(&msg, 0, sizeof(ChatPayload));
        msg.sender_id = my_user_id;
        msg.receiver_id = target_id;
        msg.msg_type = 1;
        strncpy(msg.content, buffer, 1023);

        // Gửi đi (Không cần chờ nhận phản hồi ở đây nữa, vì luồng kia sẽ lo nhận)
        send_packet(CMD_SEND_MESSAGE, &msg, sizeof(ChatPayload));
    }

    // 3. Dọn dẹp khi thoát
    is_chatting = 0;
    pthread_cancel(thread_id); // Hủy luồng nghe
    pthread_join(thread_id, NULL); // Chờ luồng tắt hẳn
    printf(">> Đã thoát chế độ Chat Live.\n");
}


void do_fetch_offline() {
    printf("\n--- LẤY TIN NHẮN OFFLINE ---\n");
    send_packet(CMD_FETCH_OFFLINE_MSGS, NULL, 0);

    int size = 0;
    void *buffer = receive_response_generic(&size);
    if (buffer) {
        int32_t count;
        memcpy(&count, buffer, sizeof(int32_t));
        printf(">> Có %d tin nhắn offline:\n", count);
        if (count > 0) {
            ChatPayload *msgs = (ChatPayload *)((char*)buffer + sizeof(int32_t));
            for (int i=0; i<count; i++) {
                printf("  [User %d]: %s\n", msgs[i].sender_id, msgs[i].content);
            }
        }
        free(buffer);
    }
}

int main() {
    struct sockaddr_in serv_addr;
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) { perror("Socket error"); return -1; }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(SERVER_PORT);
    if (inet_pton(AF_INET, SERVER_IP, &serv_addr.sin_addr) <= 0) { perror("Invalid Address"); return -1; }

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        printf("Không thể kết nối Server %s:%d\n", SERVER_IP, SERVER_PORT);
        return -1;
    }
    printf("Kết nối thành công!\n");

    int choice;
    while (1) {
        printf("\n================ MENU ================\n");
        if (my_user_id == -1) {
            printf("1. Đăng ký\n2. Đăng nhập\n0. Thoát\n");
        } else {
            printf("--- User: %s (ID: %d) ---\n", my_user_name, my_user_id);
            printf("3. Tìm kiếm User\n");
            printf("4. Gửi kết bạn\n");
            printf("5. Xem bạn bè\n");
            printf("6. Xem lời mời chờ (%s)\n", "Check");
            printf("7. Duyệt lời mời\n");
            printf("8. Hủy kết bạn\n");
            printf("9. Chat (Gửi tin)\n");
            printf("10. Check Tin Offline\n");
            printf("0. Thoát\n");
        }
        printf("Chọn: ");
        if (scanf("%d", &choice) != 1) {
            while(getchar() != '\n'); // clear stdin
            continue;
        }

        switch(choice) {
            case 0: close(sock); exit(0);
            case 1: do_register(); break;
            case 2: do_login(); break;
            case 3: do_search(); break;
            case 4: do_send_friend_req(); break;
            case 5: do_get_friends(); break;
            case 6: do_get_pending_reqs(); break;
            case 7: do_respond_req(); break;
            case 8: do_unfriend(); break;
            case 9: do_chat_live(); break;
            case 10: do_fetch_offline(); break;
            default: printf("Sai lệnh!\n");
        }
    }
    return 0;
}