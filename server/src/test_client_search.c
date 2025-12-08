#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>

#define PORT 8080

// --- COPY CÁC STRUCT TỪ server.h ---
typedef enum {
    CMD_LOGIN = 1,
    CMD_SEARCH_USERS = 11, // Lệnh mới
    CMD_RESPONSE = 99
} CommandType;

typedef struct __attribute__((packed)) {
    int command_type;
    int payload_size;
} PacketHeader;

typedef struct __attribute__((packed)) {
    char email[256];
    char password[32];
} LoginPayload;

typedef struct __attribute__((packed)) {
    char keyword[50];
    int current_user_id; // Gửi kèm cho đúng format, dù server tự lấy ID session
} SearchReqPayload;

typedef struct __attribute__((packed)) {
    int id;
    char name[50];
    char email[50];
} UserSearchInfo;

// --- HÀM TIỆN ÍCH ---
int recv_all(int sock, void *buffer, int size) {
    int total = 0;
    while (total < size) {
        int received = recv(sock, (char*)buffer + total, size - total, 0);
        if (received <= 0) return received;
        total += received;
    }
    return total;
}

// --- LOGIC TEST ---
void test_search(int sock) {
    char keyword[50];
    printf("\n--- TÌM KIẾM BẠN BÈ ---\n");
    printf("Nhập tên muốn tìm (gõ 'exit' để thoát): ");
    
    // Xóa bộ nhớ đệm
    int c; while ((c = getchar()) != '\n' && c != EOF); 
    fgets(keyword, sizeof(keyword), stdin);
    keyword[strcspn(keyword, "\n")] = 0; // Xóa ký tự xuống dòng

    if (strcmp(keyword, "exit") == 0) return;

    // 1. Gửi gói tin SEARCH
    PacketHeader header;
    header.command_type = CMD_SEARCH_USERS;
    header.payload_size = sizeof(SearchReqPayload);

    SearchReqPayload req;
    strncpy(req.keyword, keyword, 49);
    req.current_user_id = 0; // Server tự điền ID thật

    send(sock, &header, sizeof(PacketHeader), 0);
    send(sock, &req, sizeof(SearchReqPayload), 0);

    // 2. Nhận phản hồi
    PacketHeader respHeader;
    recv_all(sock, &respHeader, sizeof(PacketHeader));

    if (respHeader.command_type == CMD_RESPONSE) {
        int count = 0;
        // Nhận số lượng kết quả
        recv_all(sock, &count, sizeof(int));
        
        printf("=> Tìm thấy %d kết quả:\n", count);

        if (count > 0) {
            UserSearchInfo *users = malloc(count * sizeof(UserSearchInfo));
            recv_all(sock, users, count * sizeof(UserSearchInfo));

            // In danh sách ra màn hình
            printf("%-5s | %-20s | %-30s\n", "ID", "Tên", "Email");
            printf("----------------------------------------------------------\n");
            for (int i = 0; i < count; i++) {
                printf("%-5d | %-20s | %-30s\n", users[i].id, users[i].name, users[i].email);
            }
            free(users);
        } else {
            printf("Không tìm thấy ai tên là '%s'\n", keyword);
        }
    }
}

int main() {
    int sock;
    struct sockaddr_in serv_addr;

    // 1. Kết nối Server
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("Lỗi tạo socket");
        return -1;
    }
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);
    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) {
        perror("Địa chỉ không hợp lệ");
        return -1;
    }
    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Kết nối thất bại");
        return -1;
    }

    // 2. Đăng nhập giả lập (Cần đăng nhập để Server biết mình là ai)
    printf("Đăng nhập để test...\n");
    PacketHeader loginHeader = {CMD_LOGIN, sizeof(LoginPayload)};
    LoginPayload loginData;
    
    // !!! BẠN SỬA EMAIL/PASS NÀY CHO KHỚP VỚI DB CỦA BẠN !!!
    strcpy(loginData.email, "hung@test.com"); 
    strcpy(loginData.password, "123456");

    send(sock, &loginHeader, sizeof(PacketHeader), 0);
    send(sock, &loginData, sizeof(LoginPayload), 0);

    // Bỏ qua phần nhận phản hồi login cho nhanh (giả sử login đúng)
    PacketHeader resp;
    int loginStatus;
    recv_all(sock, &resp, sizeof(PacketHeader));
    recv_all(sock, &loginStatus, sizeof(int));

    if (loginStatus > 0) {
        printf("Đăng nhập thành công (ID: %d)\n", loginStatus);
        
        // 3. Vòng lặp test tìm kiếm
        while(1) {
            test_search(sock);
        }
    } else {
        printf("Đăng nhập thất bại. Hãy kiểm tra lại DB.\n");
    }

    close(sock);
    return 0;
}