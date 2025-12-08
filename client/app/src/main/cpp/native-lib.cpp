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
    strncpy(payload.email, user, 255);       // Copy username (tối đa 31 ký tự)
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
Java_com_example_konnichat_NativeClient_registerUser(
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

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_loginUser(
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

    jobject userObj = NULL;

    if (bytes > 0 && respHeader.command_type == CMD_RESPONSE) {
        int userId = -1;
        // Server trả về UserID
        recv(clientSocket, &userId, sizeof(int), 0);

        if (userId > 0) {
            // Đọc tiếp struct UserInfo
            UserInfo uInfo;
            recv(clientSocket, &uInfo, sizeof(UserInfo), 0);

            // Map sang Kotlin Object (NativeUserDto)
            jclass userClass = env->FindClass("com/example/konnichat/data/dto/NativeUserDto");
            jmethodID init = env->GetMethodID(userClass, "<init>", "(ILjava/lang/String;Ljava/lang/String;)V");

            jstring jEmail = env->NewStringUTF(uInfo.email);
            jstring jName = env->NewStringUTF(uInfo.name);

            userObj = env->NewObject(userClass, init, uInfo.id, jEmail, jName);

            env->DeleteLocalRef(jEmail);
            env->DeleteLocalRef(jName);
        }
    }

    env->ReleaseStringUTFChars(user, c_user);
    env->ReleaseStringUTFChars(pass, c_pass);

    return userObj; // Trả về UserID (>0) nếu thành công
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_konnichat_NativeClient_connectToServer(
        JNIEnv* env,
        jobject /* this */,
        jstring ipAddress,
        jint port) {


    // Chuyển đổi String của Java (jstring) sang chuỗi ký tự C (char*)
    const char *ip = env->GetStringUTFChars(ipAddress, 0);

    // 1. Tạo Socket
    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket == -1) {
        env->ReleaseStringUTFChars(ipAddress, ip);
        return env->NewStringUTF("Lỗi: Không thể tạo socket");
    }

    // 2. Cấu hình địa chỉ Server
    // LƯU Ý QUAN TRỌNG:
    // - Nếu chạy máy ảo (Emulator): Dùng "10.0.2.2"
    // - Nếu chạy máy thật: Dùng IP của máy tính (VD: 192.168.1.X) và setup Port Forwarding
    struct sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);

    // Giả sử dùng Emulator:
    inet_pton(AF_INET, ip, &serverAddr.sin_addr);

    // 3. Kết nối
    LOGI("Đang thử kết nối tới Server...");
    if (connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        LOGE("Kết nối thất bại!");
        close(clientSocket);

        env->ReleaseStringUTFChars(ipAddress, ip);
        return env->NewStringUTF("Kết nối thất bại (Check IP/Port)");
    }

    LOGI("Kết nối thành công!");
    env->ReleaseStringUTFChars(ipAddress, ip);
    return env->NewStringUTF("Đã kết nối tới Server thành công!");
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_getFriendList(
        JNIEnv* env, jobject, jint userId) {

    if (clientSocket == -1) return NULL;

    // 1. Gửi request
    UserIdPayload req;
    req.user_id = userId;

    PacketHeader header;
    header.command_type = CMD_GET_FRIEND_LIST;
    header.payload_size = sizeof(UserIdPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(UserIdPayload), 0);

    // 2. Nhận Header phản hồi
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);
    if (bytes <= 0) return NULL;

    // 3. Nhận số lượng bạn bè
    int count = 0;
    recv(clientSocket, &count, sizeof(int), 0);
    LOGI("Native received friend count: %d", count);

    // 4. Chuẩn bị Java ArrayList
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject listObject = env->NewObject(arrayListClass, arrayListInit);

    // Chuẩn bị class Friend
    jclass friendClass = env->FindClass("com/example/konnichat/data/dto/NativeFriendDto");
    if (friendClass == NULL) {
        LOGE("Không tìm thấy class NativeFriendDto!");
        return NULL;
    }

    // Constructor: NativeFriendDto(int id, String name, boolean isOnline)
    // Signature: (ILjava/lang/String;Z)V
    jmethodID friendInit = env->GetMethodID(friendClass, "<init>", "(ILjava/lang/String;Z)V");

    // 5. Nhận dữ liệu và đẩy vào List
    if (count > 0) {
        FriendInfo* friends = new FriendInfo[count];

        // Nhận toàn bộ data 1 lần cho nhanh (recv_all logic nên áp dụng ở đây nếu cần chuẩn chỉ)
        int received = 0;
        int totalSize = count * sizeof(FriendInfo);
        char* ptr = (char*)friends;
        while(received < totalSize) {
            int r = recv(clientSocket, ptr + received, totalSize - received, 0);
            if(r <= 0) break;
            received += r;
        }

        for (int i = 0; i < count; i++) {
            jstring name = env->NewStringUTF(friends[i].name);
            jboolean isOnline = (friends[i].is_online == 1);
            jobject friendObj = env->NewObject(friendClass, friendInit, friends[i].id, name, isOnline);

            env->CallBooleanMethod(listObject, arrayListAdd, friendObj);
            env->DeleteLocalRef(name);
            env->DeleteLocalRef(friendObj);
        }
        delete[] friends;
    }

    return listObject;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_konnichat_NativeClient_sendMessage(
        JNIEnv* env, jobject, jint senderId, jint receiverId, jstring content) {

    if (clientSocket == -1) return;

    const char *c_content = env->GetStringUTFChars(content, 0);

    // Chuẩn bị payload
    ChatPayload payload;
    payload.sender_id = senderId;
    payload.receiver_id = receiverId;
    strncpy(payload.content, c_content, 511);

    // Gửi gói tin
    PacketHeader header;
    header.command_type = CMD_SEND_MESSAGE;
    header.payload_size = sizeof(ChatPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &payload, sizeof(ChatPayload), 0);

    env->ReleaseStringUTFChars(content, c_content);
}

// Hàm nhận tin nhắn (Blocking) - Sẽ được gọi trong Thread riêng ở Java
// Trả về đối tượng MessageInfo hoặc null nếu lỗi/không có tin
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_receiveMessage(JNIEnv* env, jobject) {
    if (clientSocket == -1) return NULL;

    PacketHeader header;
    // Peek: Xem trước header mà không lấy ra khỏi buffer
    int bytes = recv(clientSocket, &header, sizeof(PacketHeader), MSG_PEEK);

    if (bytes > 0 && header.command_type == CMD_RECEIVE_MESSAGE) {
        // Nếu đúng là tin nhắn, đọc thật sự
        recv(clientSocket, &header, sizeof(PacketHeader), 0);

        MessageInfo msgInfo;
        recv(clientSocket, &msgInfo, sizeof(MessageInfo), 0);

        // Map sang Java Object
        jclass msgClass = env->FindClass("com/example/konnichat/data/dto/NativeMessageDto");
        if (msgClass == NULL) {
            LOGE("Không tìm thấy class NativeMessageDto!");
            return NULL;
        }

        // Constructor: NativeMessageDto(int serverMsgId, int senderId, String content, String timestamp)
        // Signature: (IILjava/lang/String;Ljava/lang/String;)V
        jmethodID init = env->GetMethodID(msgClass, "<init>", "(IILjava/lang/String;Ljava/lang/String;)V");

        jstring content = env->NewStringUTF(msgInfo.content);
        jstring time = env->NewStringUTF(msgInfo.timestamp);

        jobject msgObj = env->NewObject(msgClass, init, msgInfo.message_id, msgInfo.sender_id, content, time);

        env->DeleteLocalRef(content);
        env->DeleteLocalRef(time);
        return msgObj;
    }

    return NULL;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_fetchOfflineMessages(
        JNIEnv* env, jobject, jint userId) {

    if (clientSocket == -1) return NULL;

    // 1. Gửi Request
    UserIdPayload req;
    req.user_id = userId;

    PacketHeader header;
    header.command_type = CMD_FETCH_OFFLINE_MSGS;
    header.payload_size = sizeof(UserIdPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(UserIdPayload), 0);

    // 2. Nhận Header
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);
    if (bytes <= 0) return NULL;

    // 3. Nhận Số lượng (Count) - Server gửi 4 bytes int đầu tiên
    int count = 0;
    recv(clientSocket, &count, sizeof(int), 0);

    LOGI("FetchOfflineMsgs: Server báo có %d tin nhắn.", count);

    // 4. Chuẩn bị Java List
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject listObject = env->NewObject(arrayListClass, arrayListInit);

    jclass msgClass = env->FindClass("com/example/konnichat/data/dto/NativeMessageDto");
    jmethodID msgInit = env->GetMethodID(msgClass, "<init>", "(IILjava/lang/String;Ljava/lang/String;)V");

    // 5. Vòng lặp nhận đúng 'count' tin nhắn
    if (count > 0) {
        // Có thể nhận 1 cục (nếu struct nhỏ) hoặc nhận từng cái.
        // Để an toàn và clean, ta nhận cả mảng buffer vì Server gửi liền tù tì.
        int totalBytes = count * sizeof(MessageInfo);
        MessageInfo* msgs = new MessageInfo[count];

        char* ptr = (char*)msgs;
        int received = 0;

        // Logic Recv All đảm bảo nhận đủ byte
        while(received < totalBytes) {
            int r = recv(clientSocket, ptr + received, totalBytes - received, 0);
            if (r <= 0) break;
            received += r;
        }

        // Map sang Java Object
        for(int i=0; i < count; i++) {
            jstring content = env->NewStringUTF(msgs[i].content);
            jstring time = env->NewStringUTF(msgs[i].timestamp);

            jobject msgObj = env->NewObject(msgClass, msgInit, msgs[i].message_id, msgs[i].sender_id, content, time);
            env->CallBooleanMethod(listObject, arrayListAdd, msgObj);

            env->DeleteLocalRef(content);
            env->DeleteLocalRef(time);
            env->DeleteLocalRef(msgObj);
        }
        delete[] msgs;
    }

    return listObject;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_getChatHistory(
        JNIEnv* env, jobject, jint myId, jint friendId) {

    if (clientSocket == -1) return NULL;

    // 1. Gửi Request
    HistoryPayload req;
    req.user_id = myId;
    req.friend_id = friendId;

    PacketHeader header;
    header.command_type = CMD_GET_HISTORY;
    header.payload_size = sizeof(HistoryPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(HistoryPayload), 0);

    // 2. Nhận Response (Logic giống hệt fetchOfflineMessages)
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);
    if (bytes <= 0) return NULL;

    int count = 0;
    recv(clientSocket, &count, sizeof(int), 0);

    // 3. Tạo List
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject listObject = env->NewObject(arrayListClass, arrayListInit);

    jclass msgClass = env->FindClass("com/example/konnichat/data/dto/NativeMessageDto");
    jmethodID msgInit = env->GetMethodID(msgClass, "<init>", "(IILjava/lang/String;Ljava/lang/String;)V");

    if (count > 0) {
        int totalBytes = count * sizeof(MessageInfo);
        MessageInfo* msgs = new MessageInfo[count];

        char* ptr = (char*)msgs;
        int received = 0;
        while(received < totalBytes) {
            int r = recv(clientSocket, ptr + received, totalBytes - received, 0);
            if (r <= 0) break;
            received += r;
        }

        for(int i=0; i < count; i++) {
            jstring content = env->NewStringUTF(msgs[i].content);
            jstring time = env->NewStringUTF(msgs[i].timestamp);
            jobject msgObj = env->NewObject(msgClass, msgInit, msgs[i].message_id, msgs[i].sender_id, content, time);
            env->CallBooleanMethod(listObject, arrayListAdd, msgObj);

            env->DeleteLocalRef(content);
            env->DeleteLocalRef(time);
            env->DeleteLocalRef(msgObj);
        }
        delete[] msgs;
    }
    return listObject;
}
