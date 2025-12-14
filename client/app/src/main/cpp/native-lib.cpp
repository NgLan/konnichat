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
JavaVM* gJvm = nullptr; // Biến toàn cục lưu máy ảo Java

jobject gNativeClientObj = nullptr;

pthread_mutex_t socketMutex = PTHREAD_MUTEX_INITIALIZER; // Cái khóa
bool isWaitingForResponse = false; // Cờ hiệu: True = Luồng chính đang bận

// --- 1. Hàm khởi tạo để lấy JavaVM (Bắt buộc cho đa luồng) ---
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJvm = vm;
    return JNI_VERSION_1_6;
}

// --- 2. Luồng lắng nghe chạy ngầm (Task 2) ---
void* listening_task(void* arg) {
    JNIEnv *env;
    // Gắn thread C++ này vào JVM để có thể gọi hàm Kotlin
    if (gJvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
        LOGE("Lỗi: Không thể Attach Thread vào JVM");
        return NULL;
    }

    PacketHeader header;
    while (1) {
        if (clientSocket == -1) break;

        if (isWaitingForResponse) {
            usleep(10000); // Ngủ 10ms rồi quay lại kiểm tra sau
            continue; // Bỏ qua lượt này
        }

        pthread_mutex_lock(&socketMutex);
        // Peek (nhìn trộm) 8 bytes header trước
        // Dùng MSG_PEEK để không lấy hẳn dữ liệu ra, tránh xung đột với các hàm sync khác
        // (Lưu ý: Để giải pháp đơn giản cho người mới học, ta sẽ chỉ chặn bắt các gói NOTIFY ở đây)

        int bytes = recv(clientSocket, &header, sizeof(PacketHeader), MSG_PEEK | MSG_DONTWAIT);
        if (bytes <= 0) {
            LOGE("Server ngắt kết nối hoặc lỗi mạng!");
            pthread_mutex_unlock(&socketMutex);
            usleep(100000); // Ngủ 100ms
            continue;
        }

        // Nếu là gói tin THÔNG BÁO (Server chủ động gửi) -> Ta xử lý ngay tại đây
        if (header.command_type == CMD_NOTIFY_FRIEND_REQ) {

            // 1. Đọc thật sự Header ra khỏi socket (xóa khỏi buffer)
            recv(clientSocket, &header, sizeof(PacketHeader), 0);

            // 2. Đọc Payload
            PendingReqInfo info;
            recv(clientSocket, &info, sizeof(PendingReqInfo), 0);

            pthread_mutex_unlock(&socketMutex);
            LOGI("Realtime: Nhận lời mời từ %s", info.sender_name);

            // 3. Gọi ngược về Kotlin (Callback)
            // Tìm class NativeClient
            jclass clazz = env->GetObjectClass(gNativeClientObj);
            // Tìm hàm onFriendRequestReceived(int, String)
            jmethodID methodId = env->GetMethodID(clazz, "onFriendRequestReceived", "(ILjava/lang/String;)V");

            if (methodId != NULL) {
                jstring sName = env->NewStringUTF(info.sender_name);
                env->CallVoidMethod(gNativeClientObj, methodId, info.sender_id, sName);
                env->DeleteLocalRef(sName);
            }
        }
            // Nếu là CMD_RESPONSE (Kết quả trả về cho lệnh User gọi), ta KHÔNG đọc ở đây
            // để cho hàm JNI tương ứng (ví dụ searchUsers) tự đọc.
        else {
            // Ngủ một chút để nhường CPU cho luồng chính đọc socket
            pthread_mutex_unlock(&socketMutex);
            usleep(50000); // 100ms
        }
    }

    // Gỡ thread ra khỏi JVM trước khi hủy
    gJvm->DetachCurrentThread();
    return NULL;
}

// --- 3. Hàm JNI để khởi động luồng ---
extern "C" JNIEXPORT void JNICALL
Java_com_example_konnichat_NativeClient_startListening(JNIEnv* env, jobject instance) {

    static bool isRunning = false;
    if (isRunning) {
        LOGI("Luồng lắng nghe đang chạy rồi, không khởi tạo lại.");
        return;
    }

    // Tạo Global Reference để giữ object này không bị Garbage Collector xóa mất
    if (gNativeClientObj == nullptr) {
        gNativeClientObj = env->NewGlobalRef(instance);
    }

    isRunning = true;

    pthread_t thread_id;
    // Tạo luồng POSIX chuẩn (chạy hàm listening_task)
    pthread_create(&thread_id, NULL, listening_task, NULL);
    pthread_detach(thread_id); // Chạy độc lập

    LOGI("Đã khởi động luồng lắng nghe (Listening Thread)!");
}

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

extern "C" JNIEXPORT jint JNICALL
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
Java_com_example_konnichat_NativeClient_connectToServer(
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

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_getFriendList(
        JNIEnv* env, jobject, jint userId) {

    if (clientSocket == -1) return NULL;

    pthread_mutex_lock(&socketMutex); // 1. Khóa lại
    isWaitingForResponse = true;

    // 1. Gửi request
    GetFriendListPayload req;
    req.user_id = userId;

    PacketHeader header;
    header.command_type = CMD_GET_FRIEND_LIST;
    header.payload_size = sizeof(GetFriendListPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(GetFriendListPayload), 0);

    // 2. Nhận Header phản hồi
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);
    // Xử lý lỗi kết nối
    if (bytes <= 0) {
        isWaitingForResponse = false;     // Hạ biển
        pthread_mutex_unlock(&socketMutex); // Mở khóa
        return NULL;
    }

    // 3. Nhận số lượng bạn bè
    int count = 0;
    recv(clientSocket, &count, sizeof(int), 0);

    // --- [SỬA Ở ĐÂY: THÊM KIỂM TRA AN TOÀN] ---
    // Giới hạn ví dụ: tối đa 10,000 bạn bè. Nếu lớn hơn -> Dữ liệu rác -> Bỏ qua
    if (count < 0 || count > 10000) {
        LOGE("Lỗi: Số lượng phần tử không hợp lệ (count = %d). Có thể do lỗi protocol.", count);
        // Cần đọc xả rác socket hoặc đóng kết nối để tránh lỗi dây chuyền, tạm thời return NULL
        return NULL;
    }

    // 4. Chuẩn bị Java ArrayList
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject listObject = env->NewObject(arrayListClass, arrayListInit);

    // Chuẩn bị class Friend
    jclass friendClass = env->FindClass("com/example/konnichat/Friend");
    // Constructor: Friend(int id, String name, boolean isOnline)
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

    isWaitingForResponse = false;       // 3. Hạ biển "Đã xong"
    pthread_mutex_unlock(&socketMutex);

    return listObject;
}


// --- TASK 8: Gửi lời mời kết bạn ---
extern "C" JNIEXPORT jint JNICALL
Java_com_example_konnichat_NativeClient_sendFriendRequest(
        JNIEnv* env, jobject, jint senderId, jint receiverId) {

    if (clientSocket == -1) return 0; // Lỗi

    pthread_mutex_lock(&socketMutex); // 1. Khóa lại
    isWaitingForResponse = true;

    // 1. Chuẩn bị Payload
    FriendReqPayload req;
    req.sender_id = senderId;
    req.receiver_id = receiverId;

    // 2. Gửi Header + Payload
    PacketHeader header;
    header.command_type = CMD_SEND_FRIEND_REQ;
    header.payload_size = sizeof(FriendReqPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(FriendReqPayload), 0);

    // 3. Nhận phản hồi
    PacketHeader respHeader;
    if (recv(clientSocket, &respHeader, sizeof(PacketHeader), 0) <= 0) {
        isWaitingForResponse = false;     // Hạ biển
        pthread_mutex_unlock(&socketMutex); // Mở khóa
        return NULL;
    }

    int resultCode = 0;
    if (respHeader.command_type == CMD_RESPONSE) {
        recv(clientSocket, &resultCode, sizeof(int), 0);
    }

    isWaitingForResponse = false;       // 3. Hạ biển "Đã xong"
    pthread_mutex_unlock(&socketMutex); // 4. Mở khóa cho Luồng ngầm chạy

    // resultCode có thể là RequestID (>0) hoặc mã lỗi
    return resultCode;
}

// --- TASK 9A: Lấy danh sách lời mời đang chờ ---
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_getPendingRequests(
        JNIEnv* env, jobject, jint userId) {

    if (clientSocket == -1) return NULL;

    // --- BẮT ĐẦU VÙNG AN TOÀN ---
    pthread_mutex_lock(&socketMutex); // 1. Khóa lại
    isWaitingForResponse = true;      // 2. Treo biển "Đang bận"

    // 1. Gửi request (Payload dùng chung ID giống GetFriendList)
    GetFriendListPayload req;
    req.user_id = userId;

    PacketHeader header;
    header.command_type = CMD_GET_PENDING_REQS;
    header.payload_size = sizeof(GetFriendListPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(GetFriendListPayload), 0);

    // 2. Nhận Header
    PacketHeader respHeader;
    if (recv(clientSocket, &respHeader, sizeof(PacketHeader), 0) <= 0) {
        isWaitingForResponse = false;     // Hạ biển
        pthread_mutex_unlock(&socketMutex); // Mở khóa
        return NULL;
    }

    // 3. Nhận số lượng
    int count = 0;
    recv(clientSocket, &count, sizeof(int), 0);

    // --- [SỬA Ở ĐÂY: THÊM KIỂM TRA AN TOÀN] ---
    // Giới hạn ví dụ: tối đa 10,000 bạn bè. Nếu lớn hơn -> Dữ liệu rác -> Bỏ qua
    if (count < 0 || count > 10000) {
        LOGE("Lỗi: Số lượng phần tử không hợp lệ (count = %d). Có thể do lỗi protocol.", count);
        // Cần đọc xả rác socket hoặc đóng kết nối để tránh lỗi dây chuyền, tạm thời return NULL
        return NULL;
    }

    // 4. Chuẩn bị ArrayList để trả về Kotlin
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject listObject = env->NewObject(arrayListClass, arrayListInit);

    // Chuẩn bị class PendingRequest (Kotlin)
    jclass reqClass = env->FindClass("com/example/konnichat/PendingRequest");
    // Constructor: PendingRequest(int requestId, int senderId, String senderName)
    jmethodID reqInit = env->GetMethodID(reqClass, "<init>", "(IILjava/lang/String;)V");

    // 5. Nhận dữ liệu và đẩy vào List
    if (count > 0) {
        PendingReqInfo* list = new PendingReqInfo[count];

        // Recv All loop
        int totalSize = count * sizeof(PendingReqInfo);
        int received = 0;
        char* ptr = (char*)list;
        while(received < totalSize) {
            int r = recv(clientSocket, ptr + received, totalSize - received, 0);
            if(r <= 0) break;
            received += r;
        }

        for (int i = 0; i < count; i++) {
            jstring sName = env->NewStringUTF(list[i].sender_name);

            // Tạo object Kotlin
            jobject reqObj = env->NewObject(reqClass, reqInit,
                                            list[i].request_id,
                                            list[i].sender_id,
                                            sName);

            env->CallBooleanMethod(listObject, arrayListAdd, reqObj);

            env->DeleteLocalRef(sName);
            env->DeleteLocalRef(reqObj);
        }
        delete[] list;
    }

    isWaitingForResponse = false;       // 3. Hạ biển "Đã xong"
    pthread_mutex_unlock(&socketMutex);

    return listObject;
}

// --- TASK 9B: Phản hồi lời mời (Đồng ý/Từ chối) ---
extern "C" JNIEXPORT jint JNICALL
Java_com_example_konnichat_NativeClient_respondFriendRequest(
        JNIEnv* env, jobject, jint requestId, jint isAccepted) {

    if (clientSocket == -1) return 0;
    pthread_mutex_lock(&socketMutex); // 1. Khóa lại
    isWaitingForResponse = true;

    RespondReqPayload resp;
    resp.request_id = requestId;
    resp.is_accepted = isAccepted;

    PacketHeader header;
    header.command_type = CMD_RESPOND_FRIEND_REQ;
    header.payload_size = sizeof(RespondReqPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &resp, sizeof(RespondReqPayload), 0);

    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);

    // Xử lý lỗi kết nối
    if (bytes <= 0) {
        isWaitingForResponse = false;     // Hạ biển
        pthread_mutex_unlock(&socketMutex); // Mở khóa
        return NULL;
    }

    int success = 0;
    if (respHeader.command_type == CMD_RESPONSE) {
        recv(clientSocket, &success, sizeof(int), 0);
    }

    // --- KẾT THÚC VÙNG AN TOÀN ---
    isWaitingForResponse = false;       // 3. Hạ biển "Đã xong"
    pthread_mutex_unlock(&socketMutex);

    return success;
}

// --- TASK 10: Hủy kết bạn ---
extern "C" JNIEXPORT jint JNICALL
Java_com_example_konnichat_NativeClient_unfriend(
        JNIEnv* env, jobject, jint userId, jint friendId) {

    if (clientSocket == -1) return 0;
    pthread_mutex_lock(&socketMutex); // 1. Khóa lại
    isWaitingForResponse = true;

    UnfriendPayload req;
    req.user_id = userId;
    req.friend_id = friendId;

    PacketHeader header;
    header.command_type = CMD_UNFRIEND;
    header.payload_size = sizeof(UnfriendPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(UnfriendPayload), 0);

    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);

    if (bytes <= 0) {
        isWaitingForResponse = false;     // Hạ biển
        pthread_mutex_unlock(&socketMutex); // Mở khóa
        return NULL;
    }

    int success = 0;
    if (respHeader.command_type == CMD_RESPONSE) {
        recv(clientSocket, &success, sizeof(int), 0);
    }

    // --- KẾT THÚC VÙNG AN TOÀN ---
    isWaitingForResponse = false;       // 3. Hạ biển "Đã xong"
    pthread_mutex_unlock(&socketMutex);

    return success;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_konnichat_NativeClient_searchUsers(
        JNIEnv* env, jobject, jstring keyword, jint currentUserId) {

    if (clientSocket == -1) return NULL;
    pthread_mutex_lock(&socketMutex); // 1. Khóa lại
    isWaitingForResponse = true;

    // 1. Chuẩn bị dữ liệu gửi đi
    const char *c_keyword = env->GetStringUTFChars(keyword, 0);

    SearchReqPayload req;
    memset(&req, 0, sizeof(SearchReqPayload));
    req.current_user_id = currentUserId;
    strncpy(req.keyword, c_keyword, 49); // Copy tối đa 49 ký tự để chừa null

    env->ReleaseStringUTFChars(keyword, c_keyword); // Giải phóng chuỗi Java ngay sau khi copy

    // 2. Gửi Header + Payload
    PacketHeader header;
    header.command_type = CMD_SEARCH_USERS;
    header.payload_size = sizeof(SearchReqPayload);

    send(clientSocket, &header, sizeof(PacketHeader), 0);
    send(clientSocket, &req, sizeof(SearchReqPayload), 0);

    // 3. Nhận phản hồi từ Server
    PacketHeader respHeader;
    int bytes = recv(clientSocket, &respHeader, sizeof(PacketHeader), 0);
    if (bytes <= 0) {
        isWaitingForResponse = false;     // Hạ biển
        pthread_mutex_unlock(&socketMutex); // Mở khóa
        return NULL;
    }

    // 4. Nhận số lượng kết quả tìm thấy
    int count = 0;
    recv(clientSocket, &count, sizeof(int), 0);

    // --- [SỬA Ở ĐÂY: THÊM KIỂM TRA AN TOÀN] ---
    // Giới hạn ví dụ: tối đa 10,000 bạn bè. Nếu lớn hơn -> Dữ liệu rác -> Bỏ qua
    if (count < 0 || count > 10000) {
        LOGE("Lỗi: Số lượng phần tử không hợp lệ (count = %d). Có thể do lỗi protocol.", count);
        // Cần đọc xả rác socket hoặc đóng kết nối để tránh lỗi dây chuyền, tạm thời return NULL
        return NULL;
    }

    // 5. Chuẩn bị ArrayList để trả về Kotlin
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject listObject = env->NewObject(arrayListClass, arrayListInit);

    // Chuẩn bị class UserSearchInfo (Kotlin)
    jclass infoClass = env->FindClass("com/example/konnichat/UserSearchInfo");
    // Constructor: UserSearchInfo(int id, String name, String email)
    jmethodID infoInit = env->GetMethodID(infoClass, "<init>", "(ILjava/lang/String;Ljava/lang/String;)V");

    // 6. Nhận dữ liệu và đẩy vào List
    if (count > 0) {
        UserSearchInfo* results = new UserSearchInfo[count];

        // Nhận toàn bộ data (Recv All logic đơn giản)
        int totalSize = count * sizeof(UserSearchInfo);
        int received = 0;
        char* ptr = (char*)results;
        while(received < totalSize) {
            int r = recv(clientSocket, ptr + received, totalSize - received, 0);
            if(r <= 0) break;
            received += r;
        }

        for (int i = 0; i < count; i++) {
            jstring sName = env->NewStringUTF(results[i].name);
            jstring sEmail = env->NewStringUTF(results[i].email);

            // Tạo object Kotlin
            jobject infoObj = env->NewObject(infoClass, infoInit,
                                             results[i].id,
                                             sName,
                                             sEmail);

            env->CallBooleanMethod(listObject, arrayListAdd, infoObj);

            // Dọn dẹp tham chiếu cục bộ
            env->DeleteLocalRef(sName);
            env->DeleteLocalRef(sEmail);
            env->DeleteLocalRef(infoObj);
        }
        delete[] results; // Giải phóng bộ nhớ C++
    }
    isWaitingForResponse = false;       // 3. Hạ biển "Đã xong"
    pthread_mutex_unlock(&socketMutex); // 4. Mở khóa cho Luồng ngầm chạy

    return listObject;
}