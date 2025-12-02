#include <jni.h>
#include <string>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <android/log.h>
#include <string.h>
#include "protocol.h"

// Macro để ghi log vào Logcat (giúp debug dễ hơn printf)
#define LOG_TAG "NativeSocket"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Biến toàn cục lưu socket
int clientSocket = -1;

// Hàm tiện ích: Gửi gói tin (Helper function)
void send_packet(int sock, int cmd_type, const char* user, const char* pass) {
    if (sock == -1) return;

    // 1. Chuẩn bị Payload
    LoginPayload payload;
    memset(&payload, 0, sizeof(LoginPayload)); // Xóa sạch bộ nhớ rác
    strncpy(payload.username, user, 31);       // Copy username (tối đa 31 ký tự)
    strncpy(payload.password, pass, 31);       // Copy password

    // 2. Chuẩn bị Header
    PacketHeader header;
    header.command_type = cmd_type;
    header.payload_size = sizeof(LoginPayload);

    // 3. Gửi Header
    send(sock, &header, sizeof(PacketHeader), 0);

    // 4. Gửi Payload
    send(sock, &payload, sizeof(LoginPayload), 0);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_konnichat_RegisterActivity_registerUser(
        JNIEnv* env, jobject, jstring user, jstring pass) {

    if (clientSocket == -1) {
        LOGE("LỖI: Socket chưa kết nối! Hãy gọi connectToServer trước.");
        return 0;
    }

    // Chuyển jstring (Kotlin) sang char* (C)
    const char *c_user = env->GetStringUTFChars(user, 0);
    const char *c_pass = env->GetStringUTFChars(pass, 0);

    // Gửi gói tin CMD_REGISTER
    send_packet(clientSocket, CMD_REGISTER, c_user, c_pass);

    // Đợi phản hồi từ Server (Blocking read đơn giản cho login/register)
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);

    int result = 0; // Mặc định thất bại
    if (bytes > 0 && respHeader.command_type == CMD_RESPONSE) {
        // Đọc tiếp nội dung kết quả (1=OK, 0=Fail)
        recv(clientSocket, &result, sizeof(int), 0);
    }

    // Giải phóng bộ nhớ chuỗi
    env->ReleaseStringUTFChars(user, c_user);
    env->ReleaseStringUTFChars(pass, c_pass);

    return result; // Trả về 1 nếu thành công, 0 nếu thất bại
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_konnichat_LoginActivity_loginUser(
        JNIEnv* env, jobject, jstring user, jstring pass) {

    if (clientSocket == -1) {
        LOGE("LỖI: Socket chưa kết nối! Hãy gọi connectToServer trước.");
        return 0;
    }

    const char *c_user = env->GetStringUTFChars(user, 0);
    const char *c_pass = env->GetStringUTFChars(pass, 0);

    // Gửi gói tin CMD_LOGIN
    send_packet(clientSocket, CMD_LOGIN, c_user, c_pass);

    // Đợi phản hồi
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);

    int userId = -1;
    if (bytes > 0 && respHeader.command_type == CMD_RESPONSE) {
        // Server trả về UserID
        recv(clientSocket, &userId, sizeof(int), 0);
    }

    env->ReleaseStringUTFChars(user, c_user);
    env->ReleaseStringUTFChars(pass, c_pass);

    return userId; // Trả về UserID (>0) nếu thành công
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_konnichat_LoginActivity_connectToServer(
        JNIEnv* env,
        jobject /* this */) {



    // 1. Tạo Socket
    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket == -1) {
        return env->NewStringUTF("Lỗi: Không thể tạo socket");
    }

    // 2. Cấu hình địa chỉ Server
    // LƯU Ý QUAN TRỌNG:
    // - Nếu chạy máy ảo (Emulator): Dùng "10.0.2.2"
    // - Nếu chạy máy thật: Dùng IP của máy tính (VD: 192.168.1.X) và setup Port Forwarding
    struct sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(8080);

    // Giả sử dùng Emulator:
    inet_pton(AF_INET, "10.0.2.2", &serverAddr.sin_addr);

    // 3. Kết nối
    LOGI("Đang thử kết nối tới Server...");
    if (connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        LOGE("Kết nối thất bại!");
        close(clientSocket);
        return env->NewStringUTF("Kết nối thất bại (Check IP/Port)");
    }

    LOGI("Kết nối thành công!");
    return env->NewStringUTF("Đã kết nối tới Server thành công!");
}