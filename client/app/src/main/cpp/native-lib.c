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

#define TAG "KONNI_NATIVE"
// Fix lỗi __VA_ARGS__
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Globals
int sock = -1;
int current_request_id = 1;
int my_user_id = -1;
JavaVM *g_jvm = NULL;
jobject g_mainActivityObj = NULL;

// Utils
uint64_t current_time_ms() { return 0; }

// Call back to Kotlin
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

// Send Helper
void send_packet(int cmd_type, void *payload, int payload_size) {
    if (sock < 0) return;
    PacketHeader header;
    memset(&header, 0, sizeof(PacketHeader));
    header.version = PROTOCOL_VERSION;
    header.command_type = cmd_type;
    header.payload_size = payload_size;
    header.request_id = current_request_id++;
    header.timestamp = current_time_ms();

    send(sock, &header, sizeof(PacketHeader), 0);
    if (payload_size > 0 && payload) send(sock, payload, payload_size, 0);
}

// Listener Thread
void* socket_listener(void* arg) {
    PacketHeader header;
    char logBuf[4096]; // Tăng buffer để in danh sách dài

    JNIEnv *env;
    int isAttached = 0;
    if (g_jvm) {
        int status = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
        if (status < 0) {
            (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
            isAttached = 1;
        }
    }

    while (1) {
        int n = recv(sock, &header, sizeof(PacketHeader), 0);
        if (n <= 0) {
            notifyJava("DISCONNECTED FROM SERVER");
            close(sock);
            sock = -1;
            break;
        }

        void *buffer = NULL;

        if (header.payload_size > 0) {
            buffer = malloc(header.payload_size);
            if (buffer == NULL) {
                LOGE("Malloc failed for payload size: %d", header.payload_size);
                continue;
            }

            int total = 0;
            while(total < header.payload_size) {
                int r = recv(sock, (char*)buffer + total, header.payload_size - total, 0);
                if (r <= 0) break;
                total += r;
            }
        }

        // --- HANDLING RESPONSES ---
        if (header.command_type == CMD_RESPONSE) {
            if (header.status_code != 0) {
                snprintf(logBuf, sizeof(logBuf), "Error Code: %d (ReqID: %d)", header.status_code, header.request_id);
                notifyJava(logBuf);
            } else {
                // 1. LOGIN SUCCESSS
                if (header.payload_size == sizeof(UserInfoPayload)) {
                    UserInfoPayload* u = (UserInfoPayload*)buffer;
                    my_user_id = u->user_id;
                    snprintf(logBuf, sizeof(logBuf), ">>> LOGIN OK. ID: %d | Name: %s", u->user_id, u->name);
                    notifyJava(logBuf);
                }
                    // 2. SEND MESSAGE SUCCESS
                else if (header.payload_size == sizeof(ChatPayload)) {
                    ChatPayload *msg = (ChatPayload *)buffer;
                    snprintf(logBuf, sizeof(logBuf), ">>> Message Sent OK! New ID: %d", msg->message_id);
                    notifyJava(logBuf);
                }
                    // 3. LIST DATA (Friends, Search, Offline Msgs, Pending Reqs)
                else if (header.payload_size >= 4) {
                    int32_t count;
                    memcpy(&count, buffer, sizeof(int32_t));

                    int data_len = header.payload_size - sizeof(int32_t);
                    char* list_ptr = (char*)buffer + sizeof(int32_t);

                    if (count == 0) {
                        notifyJava(">>> Query Finished: 0 items found (Empty List).");
                    }
                    else {
                        snprintf(logBuf, sizeof(logBuf), ">>> Received List: %d items", count);
                        notifyJava(logBuf);

                        if (count > 0) {
                            // A. Danh sách TÌM KIẾM USER
                            if (data_len == count * sizeof(UserSearchInfo)) {
                                notifyJava("--- SEARCH RESULTS ---");
                                for (int i = 0; i < count; i++) {
                                    UserSearchInfo *item = (UserSearchInfo *)(list_ptr + i * sizeof(UserSearchInfo));
                                    snprintf(logBuf, sizeof(logBuf), "[USER] ID: %d | Name: %s | Email: %s",
                                             item->user_id, item->name, item->email);
                                    notifyJava(logBuf);
                                }
                            }
                                // B. Danh sách BẠN BÈ
                            else if (data_len == count * sizeof(UserInfoPayload)) {
                                notifyJava("--- FRIEND LIST ---");
                                for (int i = 0; i < count; i++) {
                                    UserInfoPayload *item = (UserInfoPayload *)(list_ptr + i * sizeof(UserInfoPayload));
                                    snprintf(logBuf, sizeof(logBuf), "[FRIEND] ID: %d | Name: %s | %s",
                                             item->user_id, item->name, (item->is_online ? "ONLINE" : "OFFLINE"));
                                    notifyJava(logBuf);
                                }
                            }
                                // C. Danh sách TIN NHẮN OFFLINE
                            else if (data_len == count * sizeof(ChatPayload)) {
                                notifyJava("--- OFFLINE MESSAGES ---");
                                for (int i = 0; i < count; i++) {
                                    ChatPayload *item = (ChatPayload *)(list_ptr + i * sizeof(ChatPayload));
                                    snprintf(logBuf, sizeof(logBuf), "[MSG] From %d: %s", item->sender_id, item->content);
                                    notifyJava(logBuf);
                                }
                            }
                                // D. Danh sách LỜI MỜI KẾT BẠN
                            else if (data_len == count * sizeof(PendingReqInfo)) {
                                notifyJava("--- PENDING REQUESTS ---");
                                for (int i = 0; i < count; i++) {
                                    PendingReqInfo *item = (PendingReqInfo *)(list_ptr + i * sizeof(PendingReqInfo));
                                    snprintf(logBuf, sizeof(logBuf), "[REQ] ID: %d | From: %s (User %d)",
                                             item->request_id, item->sender_name, item->sender_id);
                                    notifyJava(logBuf);
                                }
                            }
                        } else {
                            notifyJava("(List is empty)");
                        }
                    }
                }
                else {
                    notifyJava(">>> Command Success!");
                }
            }
        }
        else if (header.command_type == CMD_RECEIVE_MESSAGE) {
            if (buffer) {
                ChatPayload *msg = (ChatPayload *)buffer;
                snprintf(logBuf, sizeof(logBuf), "[CHAT] User %d: %s", msg->sender_id, msg->content);
                notifyJava(logBuf);
            }
        }
        else if (header.command_type == CMD_NOTIFY_FRIEND_REQ) {
            if (buffer) {
                PendingReqInfo *info = (PendingReqInfo *)buffer;
                snprintf(logBuf, sizeof(logBuf), "[NOTIF] Friend Request from %s (ID: %d). ReqID: %d", info->sender_name, info->sender_id, info->request_id);
                notifyJava(logBuf);
            }
        }
        else if (header.command_type == CMD_NOTIFY_REQ_ACCEPTED) {
            notifyJava("[NOTIF] Your friend request was ACCEPTED!");
        }

        if (buffer) free(buffer);
    }
    return NULL;
}

// --- JNI EXPORTS ---

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_initNative(JNIEnv *env, jobject thiz) {
    g_mainActivityObj = (*env)->NewGlobalRef(env, thiz);
}

JNIEXPORT jboolean JNICALL Java_com_example_konnichat_MainActivity_connectServer(JNIEnv *env, jobject thiz, jstring ip, jint port) {
    // FIX LỖI: CHẶN KẾT NỐI TRÙNG (LOGIN 2 LẦN)
    if (sock != -1) {
        LOGE("Already connected! Ignoring request.");
        return JNI_TRUE;
    }

    const char *ipStr = (*env)->GetStringUTFChars(env, ip, 0);
    struct sockaddr_in serv_addr;

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) return JNI_FALSE;

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    if (inet_pton(AF_INET, ipStr, &serv_addr.sin_addr) <= 0) return JNI_FALSE;

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        // Nếu connect lỗi, reset lại sock để lần sau còn thử lại được
        close(sock);
        sock = -1;
        return JNI_FALSE;
    }

    (*env)->ReleaseStringUTFChars(env, ip, ipStr);

    pthread_t thread_id;
    if (pthread_create(&thread_id, NULL, socket_listener, NULL) != 0) return JNI_FALSE;

    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_registerUser(JNIEnv *env, jobject thiz, jstring name, jstring email, jstring pass) {
    AuthPayload p; memset(&p, 0, sizeof(p));
    const char *n = (*env)->GetStringUTFChars(env, name, 0);
    const char *e = (*env)->GetStringUTFChars(env, email, 0);
    const char *ps = (*env)->GetStringUTFChars(env, pass, 0);

    strncpy(p.name, n, 63); strncpy(p.email, e, 255); strncpy(p.password, ps, 63);
    send_packet(CMD_REGISTER, &p, sizeof(p));

    (*env)->ReleaseStringUTFChars(env, name, n);
    (*env)->ReleaseStringUTFChars(env, email, e);
    (*env)->ReleaseStringUTFChars(env, pass, ps);
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_loginUser(JNIEnv *env, jobject thiz, jstring email, jstring pass) {
    AuthPayload p; memset(&p, 0, sizeof(p));
    const char *e = (*env)->GetStringUTFChars(env, email, 0);
    const char *ps = (*env)->GetStringUTFChars(env, pass, 0);

    strncpy(p.email, e, 255); strncpy(p.password, ps, 63);
    send_packet(CMD_LOGIN, &p, sizeof(p));

    (*env)->ReleaseStringUTFChars(env, email, e);
    (*env)->ReleaseStringUTFChars(env, pass, ps);
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_getFriendList(JNIEnv *env, jobject thiz) {
    send_packet(CMD_GET_FRIEND_LIST, NULL, 0);
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_searchUser(JNIEnv *env, jobject thiz, jstring keyword) {
    SearchReqPayload p; memset(&p, 0, sizeof(p));
    const char *k = (*env)->GetStringUTFChars(env, keyword, 0);
    strncpy(p.keyword, k, 49);
    send_packet(CMD_SEARCH_USERS, &p, sizeof(p));
    (*env)->ReleaseStringUTFChars(env, keyword, k);
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_sendMessage(JNIEnv *env, jobject thiz, jint receiverId, jstring content) {
    if (my_user_id == -1) return;
    ChatPayload p; memset(&p, 0, sizeof(p));
    p.sender_id = my_user_id;
    p.receiver_id = receiverId;
    p.msg_type = 1;
    const char *c = (*env)->GetStringUTFChars(env, content, 0);
    strncpy(p.content, c, 1023);
    send_packet(CMD_SEND_MESSAGE, &p, sizeof(p));
    (*env)->ReleaseStringUTFChars(env, content, c);
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_fetchOfflineMessages(JNIEnv *env, jobject thiz) {
    send_packet(CMD_FETCH_OFFLINE_MSGS, NULL, 0);
}

// --- NEW FUNCTIONS FOR FRIENDS ---

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_sendFriendRequest(JNIEnv *env, jobject thiz, jint targetId) {
    FriendReqPayload p;
    p.target_id = targetId;
    send_packet(CMD_SEND_FRIEND_REQ, &p, sizeof(p));
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_getPendingRequests(JNIEnv *env, jobject thiz) {
    send_packet(CMD_GET_PENDING_REQS, NULL, 0);
}

JNIEXPORT void JNICALL Java_com_example_konnichat_MainActivity_respondFriendRequest(JNIEnv *env, jobject thiz, jint requestId, jboolean isAccepted) {
    FriendRespondPayload p;
    p.request_id = requestId;
    p.is_accepted = (isAccepted == JNI_TRUE) ? 1 : 0;
    send_packet(CMD_RESPOND_FRIEND_REQ, &p, sizeof(p));
}