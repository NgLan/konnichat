#define _GNU_SOURCE

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
#include "protocol.h"

#define TAG "KONNI_CLIENT"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

//int clientSocket = -1;
//int current_request_id = 1;
//int my_user_id = -1; // Lưu lại sau khi login thành công
//
//// --- HELPER: Gửi gói tin ---
//void send_packet_internal(int cmd, void* payload, int size) {
//    if (clientSocket == -1) return;
//
//    PacketHeader header;
//    memset(&header, 0, sizeof(PacketHeader));
//    header.version = PROTOCOL_VERSION;
//    header.command_type = cmd;
//    header.payload_size = size;
//    header.request_id = current_request_id++;
//    header.timestamp = 0; // Client tạm thời chưa cần timestamp gửi đi
//
//    // 1. Gửi Header
//    send(clientSocket, &header, sizeof(PacketHeader), 0);
//
//    // 2. Gửi Payload (nếu có)
//    if (size > 0 && payload != NULL) {
//        send(clientSocket, payload, size, 0);
//    }
//}
//
//// --- JNI FUNCTIONS ---
//
//// 1. Connect
//jboolean Java_com_example_konnichat_NativeClient_connect(JNIEnv *env, jobject thiz, jstring ip, jint port) {
//    const char *ipStr = (*env)->GetStringUTFChars(env, ip, 0);
//
//    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
//    struct sockaddr_in serverAddr;
//    serverAddr.sin_family = AF_INET;
//    serverAddr.sin_port = htons(port);
//    serverAddr.sin_addr.s_addr = inet_addr(ipStr);
//
//    int res = connect(clientSocket, (struct sockaddr *)&serverAddr, sizeof(serverAddr));
//    (*env)->ReleaseStringUTFChars(env, ip, ipStr);
//
//    if (res < 0) {
//        LOGE("Connection Failed!");
//        return JNI_FALSE;
//    }
//    LOGI("Connected to Server!");
//    return JNI_TRUE;
//}
//
//// 2. Login
//void Java_com_example_konnichat_NativeClient_login(JNIEnv *env, jobject thiz, jstring email, jstring pass) {
//    const char *c_email = (*env)->GetStringUTFChars(env, email, 0);
//    const char *c_pass = (*env)->GetStringUTFChars(env, pass, 0);
//
//    AuthPayload auth;
//    memset(&auth, 0, sizeof(AuthPayload));
//    strncpy(auth.email, c_email, 255);
//    strncpy(auth.password, c_pass, 63);
//
//    LOGI("Sending Login Packet: %s", c_email);
//    send_packet_internal(CMD_LOGIN, &auth, sizeof(AuthPayload));
//
//    (*env)->ReleaseStringUTFChars(env, email, c_email);
//    (*env)->ReleaseStringUTFChars(env, pass, c_pass);
//}
//
//// 3. Send Message
//void Java_com_example_konnichat_NativeClient_sendMessage(JNIEnv *env, jobject thiz, jint receiverId, jstring content) {
//    const char *c_content = (*env)->GetStringUTFChars(env, content, 0);
//
//    ChatPayload msg;
//    memset(&msg, 0, sizeof(ChatPayload));
//    msg.sender_id = my_user_id; // ID của mình
//    msg.receiver_id = receiverId;
//    msg.msg_type = 1;
//    strncpy(msg.content, c_content, 1023);
//
//    LOGI("Sending Msg to %d: %s", receiverId, c_content);
//    send_packet_internal(CMD_SEND_MESSAGE, &msg, sizeof(ChatPayload));
//
//    (*env)->ReleaseStringUTFChars(env, content, c_content);
//}
//
//// 4. Read Loop (Hàm này Blocking - chờ tin đến)
//// Trả về String để in ra Console log
//jstring Java_com_example_konnichat_NativeClient_readPacket(JNIEnv *env, jobject thiz) {
//    if (clientSocket == -1) return (*env)->NewStringUTF(env, "ERROR: No Connection");
//
//    PacketHeader header;
//    // Đọc Header
//    int n = recv(clientSocket, &header, sizeof(PacketHeader), 0);
//    if (n <= 0) return (*env)->NewStringUTF(env, "DISCONNECTED");
//
//    // Đọc Payload
//    char* payloadBuffer = NULL;
//    if (header.payload_size > 0) {
//        payloadBuffer = malloc(header.payload_size);
//        int total = 0;
//        while (total < header.payload_size) {
//            int r = recv(clientSocket, payloadBuffer + total, header.payload_size - total, 0);
//            if (r <= 0) break;
//            total += r;
//        }
//    }
//
//    // XỬ LÝ GÓI TIN & TẠO LOG
//    char logMsg[2048] = {0};
//
//    // Case 1: Phản hồi Login thành công
//    if (header.command_type == CMD_RESPONSE && header.status_code == 0 && header.payload_size == sizeof(UserInfoPayload)) {
//        UserInfoPayload* user = (UserInfoPayload*)payloadBuffer;
//        my_user_id = user->user_id; // LƯU LẠI ID
//        snprintf(logMsg, sizeof(logMsg), ">>> LOGIN SUCCESS! Hi %s (ID: %d)", user->name, user->user_id);
//    }
//    // Case 2: Nhận tin nhắn
//    else if (header.command_type == CMD_RECEIVE_MESSAGE) {
//        ChatPayload* msg = (ChatPayload*)payloadBuffer;
//        snprintf(logMsg, sizeof(logMsg), ">>> MSG FROM [%d]: %s", msg->sender_id, msg->content);
//    }
//    // Case 3: Các phản hồi chung khác
//    else {
//        snprintf(logMsg, sizeof(logMsg), ">>> PACKET: Cmd=%d, Status=%d", header.command_type, header.status_code);
//    }
//
//    if (payloadBuffer) free(payloadBuffer);
//    return (*env)->NewStringUTF(env, logMsg);
//}
//
//// 5. Wait Packet (Trả về Object ServerResponse để xử lý logic Header)
//jobject Java_com_example_konnichat_NativeClient_waitPacket(JNIEnv *env, jobject thiz) {
//    // 1. Tìm class ServerResponse bên Kotlin
//    // Đảm bảo package "com/example/konnichat" khớp với file Kotlin của bạn
//    jclass cls = (*env)->FindClass(env, "com/example/konnichat/ServerResponse");
//
//    if (cls == NULL) return NULL; // Safety check
//
//    // 2. Lấy hàm khởi tạo (Constructor): (Int, Int, String) -> Void
//    jmethodID constructor = (*env)->GetMethodID(env, cls, "<init>", "(IILjava/lang/String;)V");
//
//    // SỬA: Dùng biến global 'clientSocket' thay vì 'sock'
//    if (clientSocket < 0) {
//        jstring err = (*env)->NewStringUTF(env, "No Connection");
//        return (*env)->NewObject(env, cls, constructor, -1, -1, err);
//    }
//
//    // 3. Đọc Header từ Socket
//    PacketHeader h;
//    // SỬA: Dùng 'clientSocket'
//    int n = recv(clientSocket, &h, sizeof(h), 0);
//
//    if (n <= 0) {
//        jstring err = (*env)->NewStringUTF(env, "Disconnected");
//        return (*env)->NewObject(env, cls, constructor, -1, -1, err);
//    }
//
//    // 4. Xử lý Payload & Cập nhật ID
//    char buffer[1024] = {0};
//    if (h.payload_size > 0) {
//        char *payload = malloc(h.payload_size);
//        if (payload) {
//            recv(clientSocket, payload, h.payload_size, 0);
//
//            if (h.command_type == CMD_RESPONSE && h.status_code == 0) {
//
//                // CÁCH KIỂM TRA MỚI:
//                // 1. Phải đúng kích thước gói UserInfo
//                // 2. VÀ biến my_user_id phải đang là -1 (nghĩa là chưa login)
//                // -> Tránh việc nhận phản hồi tin nhắn (cũng CMD 99) mà lại tưởng là Login
//
//                if (h.payload_size == sizeof(UserInfoPayload) && my_user_id == -1) {
//                    memcpy(&my_user_id, payload, sizeof(int32_t));
//                    sprintf(buffer, "LOGIN_SUCCESS_ID:%d", my_user_id); // Đặt keyword để Kotlin dễ bắt
//                }
//                else {
//                    // Đây là phản hồi của các lệnh khác (VD: Gửi tin nhắn thành công)
//                    // Không làm gì với biến my_user_id cả!
//                    sprintf(buffer, "CMD_OK (Type: %d)", h.command_type);
//                }
//            }
//            else if (h.command_type == CMD_RECEIVE_MESSAGE) {
//                ChatPayload *msg = (ChatPayload*)payload;
//                sprintf(buffer, "From %d: %s", msg->sender_id, msg->content);
//            }
//
//            free(payload);
//        }
//    } else {
//        sprintf(buffer, "No Payload (Status: %d)", h.status_code);
//    }
//
//    // 5. Tạo Object trả về cho Kotlin
//    jstring jMsg = (*env)->NewStringUTF(env, buffer);
//    return (*env)->NewObject(env, cls, constructor, h.command_type, h.status_code, jMsg);
//}
//
//void Java_com_example_konnichat_NativeClient_disconnect(JNIEnv *env, jobject thiz) {
//    close(clientSocket);
//    clientSocket = -1;
//}
// --- GLOBALS ---
int sock = -1;
int current_request_id = 1;
int my_user_id = -1;

// JNI Globals for callback
JavaVM *g_jvm = NULL;
jobject g_mainActivityObj = NULL;

// --- UTILS ---
uint64_t current_time_ms() {
    // Simplified for demo, server ignores client timestamp usually
    return 0;
}

// Helper to call Java method
void notifyJava(const char* msg) {
    if (g_jvm == NULL || g_mainActivityObj == NULL) return;

    JNIEnv *env;
    int attachStatus = (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);

    if (attachStatus == JNI_OK) {
        jclass cls = (*env)->GetObjectClass(env, g_mainActivityObj);
        jmethodID mid = (*env)->GetMethodID(env, cls, "onNativeMessage", "(Ljava/lang/String;)V");

        if (mid != NULL) {
            jstring jStr = (*env)->NewStringUTF(env, msg);
            (*env)->CallVoidMethod(env, g_mainActivityObj, mid, jStr);
            (*env)->DeleteLocalRef(env, jStr);
        }
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
}

// --- NETWORK SEND ---
void send_packet(int cmd_type, void *payload, int payload_size) {
    if (sock < 0) return;

    PacketHeader header;
    memset(&header, 0, sizeof(PacketHeader));

    header.version = PROTOCOL_VERSION;
    header.command_type = cmd_type;
    header.payload_size = payload_size;
    header.request_id = current_request_id++;
    header.status_code = 0;
    header.timestamp = current_time_ms();

    if (send(sock, &header, sizeof(PacketHeader), 0) < 0) {
        LOGE("Failed to send header");
        return;
    }

    if (payload_size > 0 && payload != NULL) {
        if (send(sock, payload, payload_size, 0) < 0) {
            LOGE("Failed to send payload");
        }
    }
}

// --- RECEIVE THREAD ---
void* socket_listener(void* arg) {
    PacketHeader header;
    char logBuffer[2048];

    while (1) {
        int n = recv(sock, &header, sizeof(PacketHeader), 0);
        if (n <= 0) {
            notifyJava("Disconnected from server.");
            break;
        }

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

        // Process Packet
        if (header.command_type == CMD_RESPONSE) {
            if (header.status_code == STATUS_SUCCESS) {
                snprintf(logBuffer, sizeof(logBuffer), "Server Response [ReqID %d]: SUCCESS", header.request_id);
            } else {
                snprintf(logBuffer, sizeof(logBuffer), "Server Response [ReqID %d]: ERROR Code %d", header.request_id, header.status_code);
            }
            notifyJava(logBuffer);

            // Determine if payload is UserInfo (Login success)
            if (header.payload_size == sizeof(UserInfoPayload)) {
                UserInfoPayload* u = (UserInfoPayload*)buffer;
                my_user_id = u->user_id;
                snprintf(logBuffer, sizeof(logBuffer), ">> Logged in as: %s (ID: %d)", u->name, u->user_id);
                notifyJava(logBuffer);
            }
                // Handling Lists (Friends/Search) - Simplistic heuristic based on size check or previous request
                // For this demo, we just print "Received Data x bytes" unless specific struct matches
            else if (header.payload_size > sizeof(int32_t)) {
                // If it starts with a count (int32), it's a list
                int32_t count;
                memcpy(&count, buffer, sizeof(int32_t));
                snprintf(logBuffer, sizeof(logBuffer), ">> Received List Data. Count: %d", count);
                notifyJava(logBuffer);
            }

        } else if (header.command_type == CMD_RECEIVE_MESSAGE) {
            ChatPayload *msg = (ChatPayload *)buffer;
            snprintf(logBuffer, sizeof(logBuffer), "[CHAT] User %d: %s", msg->sender_id, msg->content);
            notifyJava(logBuffer);
        } else if (header.command_type == CMD_NOTIFY_FRIEND_REQ) {
            notifyJava("[NOTIF] You have a new friend request!");
        }

        if (buffer) free(buffer);
    }
    return NULL;
}

// --- JNI IMPLEMENTATION ---

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
g_jvm = vm;
return JNI_VERSION_1_6;
}

void Java_com_example_konnichat_MainActivity_initNative(JNIEnv *env, jobject thiz) {
// Create a global reference to the Activity to call it back later
g_mainActivityObj = (*env)->NewGlobalRef(env, thiz);
}

jboolean Java_com_example_konnichat_MainActivity_connectServer(JNIEnv *env, jobject thiz, jstring ip, jint port) {
    const char *ipStr = (*env)->GetStringUTFChars(env, ip, 0);

    struct sockaddr_in serv_addr;
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        LOGE("Socket setup failed");
        return JNI_FALSE;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    if (inet_pton(AF_INET, ipStr, &serv_addr.sin_addr) <= 0) {
        LOGE("Invalid Address");
        return JNI_FALSE;
    }

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        LOGE("Connection failed");
        return JNI_FALSE;
    }

    (*env)->ReleaseStringUTFChars(env, ip, ipStr);

    // Start background thread for reading
    pthread_t thread_id;
    if (pthread_create(&thread_id, NULL, socket_listener, NULL) != 0) {
        LOGE("Failed to create thread");
        return JNI_FALSE;
    }

    LOGI("Connected to server");
    return JNI_TRUE;
}

void Java_com_example_konnichat_MainActivity_registerUser(JNIEnv *env, jobject thiz, jstring name, jstring email, jstring pass) {
AuthPayload payload;
memset(&payload, 0, sizeof(AuthPayload));

const char *c_name = (*env)->GetStringUTFChars(env, name, 0);
const char *c_email = (*env)->GetStringUTFChars(env, email, 0);
const char *c_pass = (*env)->GetStringUTFChars(env, pass, 0);

strncpy(payload.name, c_name, sizeof(payload.name) - 1);
strncpy(payload.email, c_email, sizeof(payload.email) - 1);
strncpy(payload.password, c_pass, sizeof(payload.password) - 1);

send_packet(CMD_REGISTER, &payload, sizeof(AuthPayload));

(*env)->ReleaseStringUTFChars(env, name, c_name);
(*env)->ReleaseStringUTFChars(env, email, c_email);
(*env)->ReleaseStringUTFChars(env, pass, c_pass);
}

void Java_com_example_konnichat_MainActivity_loginUser(JNIEnv *env, jobject thiz, jstring email, jstring pass) {
AuthPayload payload;
memset(&payload, 0, sizeof(AuthPayload));

const char *c_email = (*env)->GetStringUTFChars(env, email, 0);
const char *c_pass = (*env)->GetStringUTFChars(env, pass, 0);

strncpy(payload.email, c_email, sizeof(payload.email) - 1);
strncpy(payload.password, c_pass, sizeof(payload.password) - 1);

send_packet(CMD_LOGIN, &payload, sizeof(AuthPayload));

(*env)->ReleaseStringUTFChars(env, email, c_email);
(*env)->ReleaseStringUTFChars(env, pass, c_pass);
}

void Java_com_example_konnichat_MainActivity_getFriendList(JNIEnv *env, jobject thiz) {
send_packet(CMD_GET_FRIEND_LIST, NULL, 0);
}

void Java_com_example_konnichat_MainActivity_searchUser(JNIEnv *env, jobject thiz, jstring keyword) {
SearchReqPayload payload;
memset(&payload, 0, sizeof(SearchReqPayload));

const char *c_key = (*env)->GetStringUTFChars(env, keyword, 0);
strncpy(payload.keyword, c_key, sizeof(payload.keyword) - 1);

send_packet(CMD_SEARCH_USERS, &payload, sizeof(SearchReqPayload));
(*env)->ReleaseStringUTFChars(env, keyword, c_key);
}

void Java_com_example_konnichat_MainActivity_sendMessage(JNIEnv *env, jobject thiz, jint receiverId, jstring content) {
if (my_user_id == -1) {
LOGE("Cannot send message: Not logged in");
return;
}

ChatPayload payload;
memset(&payload, 0, sizeof(ChatPayload));

payload.sender_id = my_user_id;
payload.receiver_id = receiverId;
payload.msg_type = 1; // Text

const char *c_content = (*env)->GetStringUTFChars(env, content, 0);
strncpy(payload.content, c_content, sizeof(payload.content) - 1);

send_packet(CMD_SEND_MESSAGE, &payload, sizeof(ChatPayload));
(*env)->ReleaseStringUTFChars(env, content, c_content);
}

void Java_com_example_konnichat_MainActivity_fetchOfflineMessages(JNIEnv *env, jobject thiz) {
send_packet(CMD_FETCH_OFFLINE_MSGS, NULL, 0);
}