#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "../include/native_core.h"
#include "../include/utils/logger_utils.h"
#include <android/log.h>

static JavaVM *g_jvm = NULL;
static jobject g_listener = NULL;

// --- Global Cache (Để tối ưu hiệu năng) ---
static jmethodID m_onFriendList;
static jmethodID m_onMessage;
static jmethodID m_onStatus;
static jmethodID m_onDisconnect;
static jmethodID m_onFriendReq;
static jmethodID m_onReqResp;
static jmethodID m_onReqAccepted;
static jmethodID m_onUnfriended;
static jmethodID m_onSearchResult;
static jmethodID m_onMsgSent;
static jmethodID m_onMsgReceived;
static jmethodID m_onMsgDelivered;
static jmethodID m_onPendingList;
static jclass c_PendingRequestDto;
static jmethodID m_PendingRequestDtoInit;

static jmethodID m_onHistoryReceived;
static jmethodID m_onGroupCreated;

static jclass c_UserDto;
static jmethodID m_UserDtoInit;
static jclass c_UserSearchDto;
static jmethodID m_UserSearchDtoInit;
static jclass c_MessageDto;
static jmethodID m_MessageDtoInit;

// Các class Exception
static jclass g_AuthException;
static jclass g_UserNotFoundException;
static jclass g_UserExistException;
static jclass g_ServerException;
static jclass g_NetworkException;
static jclass g_ProtocolException;
static jclass g_UnknownException;

// --- 1. IMPLEMENT CALLBACKS C ---
// Helper: Lấy JNIEnv cho thread hiện tại
static JNIEnv *get_jni_env() {
    JNIEnv *env = NULL;
    if (g_jvm == NULL)
        return NULL;

    // Kiểm tra xem thread này đã attach chưa
    int stat = (*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_6);
    if (stat == JNI_EDETACHED) {
        // Chưa attach -> Attach ngay
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != 0) {
            LOGE("Failed to attach thread to JVM");
            return NULL;
        }
    }
    return env;
}

// Impl: Khi nhận danh sách bạn bè
void jni_on_friend_list(int count, UserInfoPayload *friends) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener)
        return;

    // 1. Tạo mảng Java UserDto[]
    jobjectArray jArray = (*env)->NewObjectArray(env, count, c_UserDto, NULL);

    // 2. Loop convert C struct -> Java Object
    for (int i = 0; i < count; i++) {
        jstring jName = (*env)->NewStringUTF(env, friends[i].name);
        jstring jEmail = (*env)->NewStringUTF(env, friends[i].email);
        jboolean jOnline = (friends[i].is_online == 1);

        jobject jUser = (*env)->NewObject(env, c_UserDto, m_UserDtoInit,
                                          friends[i].user_id, jName, jEmail, jOnline);

        (*env)->SetObjectArrayElement(env, jArray, i, jUser);

        // Dọn dẹp local ref
        (*env)->DeleteLocalRef(env, jName);
        (*env)->DeleteLocalRef(env, jEmail);
        (*env)->DeleteLocalRef(env, jUser);
    }

    // 3. Gọi hàm Kotlin: onFriendListReceived(array)
    (*env)->CallVoidMethod(env, g_listener, m_onFriendList, jArray);

    (*env)->DeleteLocalRef(env, jArray);
}

// Impl: Khi nhận status
void jni_on_status_change(int friend_id, int is_online) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener)
        return;

    (*env)->CallVoidMethod(env, g_listener, m_onStatus, friend_id, (jboolean) (is_online == 1));
}

void jni_on_friend_req(int req_id, int sender_id, const char *sender_name) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener)
        return;

    jstring jName = (*env)->NewStringUTF(env, sender_name);

    // Gọi Kotlin: onFriendRequestReceived(reqId, senderId, name)
    (*env)->CallVoidMethod(env, g_listener, m_onFriendReq, req_id, sender_id, jName);

    (*env)->DeleteLocalRef(env, jName);
}

void jni_on_req_response(int cmd, int status) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener)
        return;
    (*env)->CallVoidMethod(env, g_listener, m_onReqResp, cmd, status);
}

void jni_on_req_accepted(UserInfoPayload *user) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    // Convert UserInfoPayload -> UserDto (Java)
    jstring jName = (*env)->NewStringUTF(env, user->name);
    jstring jEmail = (*env)->NewStringUTF(env, user->email); // Có thể rỗng
    jboolean jOnline = (user->is_online == 1);

    jobject jUser = (*env)->NewObject(env, c_UserDto, m_UserDtoInit,
                                      user->user_id, jName, jEmail, jOnline);

    // Gọi hàm Kotlin: onFriendRequestAccepted(UserDto)
    (*env)->CallVoidMethod(env, g_listener, m_onReqAccepted, jUser);

    // Cleanup
    (*env)->DeleteLocalRef(env, jName);
    (*env)->DeleteLocalRef(env, jEmail);
    (*env)->DeleteLocalRef(env, jUser);
}

void jni_on_unfriended(int ex_friend_id) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    // Gọi hàm Kotlin: onFriendRemoved(int)
    (*env)->CallVoidMethod(env, g_listener, m_onUnfriended, (jint)ex_friend_id);
}

void jni_on_search_result(int count, UserSearchInfo *results) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    jobjectArray jArray = (*env)->NewObjectArray(env, count, c_UserSearchDto, NULL);

    for (int i = 0; i < count; i++) {
        jstring jName = (*env)->NewStringUTF(env, results[i].name);
        jstring jEmail = (*env)->NewStringUTF(env, results[i].email);

        // Lấy status từ struct C
        jint jStatus = (jint)results[i].status;

        // Gọi Constructor mới có thêm tham số status
        jobject jObj = (*env)->NewObject(env, c_UserSearchDto, m_UserSearchDtoInit,
                                         results[i].user_id, jName, jEmail, jStatus);

        (*env)->SetObjectArrayElement(env, jArray, i, jObj);

        (*env)->DeleteLocalRef(env, jName);
        (*env)->DeleteLocalRef(env, jEmail);
        (*env)->DeleteLocalRef(env, jObj);
    }

    (*env)->CallVoidMethod(env, g_listener, m_onSearchResult, jArray);
    (*env)->DeleteLocalRef(env, jArray);
}

// Khi gửi thành công (ACK)
void jni_on_msg_sent(int temp_req_id, int server_msg_id, uint64_t server_time) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    // Kotlin: onMessageSent(tempId, serverId, timestamp)
    (*env)->CallVoidMethod(env, g_listener, m_onMsgSent,
                           (jint)temp_req_id, (jint)server_msg_id, (jlong)server_time);
}

// Khi nhận tin mới
void jni_on_message(ChatPayload *msg) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    // Convert ChatPayload -> MessageDto
    jstring jContent = (*env)->NewStringUTF(env, msg->content);
    jstring jChatType = (*env)->NewStringUTF(env, msg->chat_type);

    jobject jMsg = (*env)->NewObject(env, c_MessageDto, m_MessageDtoInit,
                                     msg->message_id,
                                     msg->sender_id,
                                     msg->receiver_id,
                                     jContent,
                                     (jlong)msg->created_at,
                                     msg->msg_type,
                                     jChatType);

    (*env)->CallVoidMethod(env, g_listener, m_onMsgReceived, jMsg);

    (*env)->DeleteLocalRef(env, jContent);
    (*env)->DeleteLocalRef(env, jChatType);
    (*env)->DeleteLocalRef(env, jMsg);
}

// Khi tin đã delivered
void jni_on_msg_delivered(int server_msg_id) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    (*env)->CallVoidMethod(env, g_listener, m_onMsgDelivered, (jint)server_msg_id);
}

void jni_on_history_received(int count, ChatPayload *messages) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    // Tạo mảng MessageDto[]
    jobjectArray jArray = (*env)->NewObjectArray(env, count, c_MessageDto, NULL);

    for (int i = 0; i < count; i++) {
        messages[i].content[MAX_CONTENT_LEN - 1] = '\0';
        messages[i].chat_type[15] = '\0';

        jstring jContent = (*env)->NewStringUTF(env, messages[i].content);
        jstring jChatType;
        if (strlen(messages[i].chat_type) > 0) {
            jChatType = (*env)->NewStringUTF(env, messages[i].chat_type);
        } else {
            jChatType = (*env)->NewStringUTF(env, "private"); // Fallback
        }

        jobject jMsg = (*env)->NewObject(env, c_MessageDto, m_MessageDtoInit,
                                         messages[i].message_id,
                                         messages[i].sender_id,
                                         messages[i].receiver_id,
                                         jContent,
                                         (jlong)messages[i].created_at,
                                         messages[i].msg_type,
                                         jChatType);

        (*env)->SetObjectArrayElement(env, jArray, i, jMsg);

        (*env)->DeleteLocalRef(env, jContent);
        (*env)->DeleteLocalRef(env, jChatType);
        (*env)->DeleteLocalRef(env, jMsg);
    }

    (*env)->CallVoidMethod(env, g_listener, m_onHistoryReceived, jArray);
    (*env)->DeleteLocalRef(env, jArray);
}

void jni_on_group_created(int group_id, const char* name) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    jstring jName = (*env)->NewStringUTF(env, name);
    (*env)->CallVoidMethod(env, g_listener, m_onGroupCreated, (jint)group_id, jName);
    (*env)->DeleteLocalRef(env, jName);
}

void jni_on_disconnect(const char *reason) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener)
        return;
    jstring jReason = (*env)->NewStringUTF(env, reason);
    (*env)->CallVoidMethod(env, g_listener, m_onDisconnect, jReason);
    (*env)->DeleteLocalRef(env, jReason);
}

void jni_on_pending_list(int count, PendingReqInfo *list) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_listener) return;

    jobjectArray jArray = (*env)->NewObjectArray(env, count, c_PendingRequestDto, NULL);

    for (int i = 0; i < count; i++) {
        jstring jName = (*env)->NewStringUTF(env, list[i].sender_name);

        // Tạo object PendingRequestDto(requestId, senderId, senderName)
        jobject jObj = (*env)->NewObject(env, c_PendingRequestDto, m_PendingRequestDtoInit,
                                         list[i].request_id, list[i].sender_id, jName);

        (*env)->SetObjectArrayElement(env, jArray, i, jObj);

        (*env)->DeleteLocalRef(env, jName);
        (*env)->DeleteLocalRef(env, jObj);
    }

    // Gọi hàm Kotlin: onPendingRequestsReceived(array)
    (*env)->CallVoidMethod(env, g_listener, m_onPendingList, jArray);

    (*env)->DeleteLocalRef(env, jArray);
}

// --- Helper ném lỗi ---
void throw_unified_error(JNIEnv *env, int result_code) {
    jclass exClass = g_UnknownException;
    const char *msg = "Lỗi không xác định";

    // 1. Xử lý Lỗi Client (Số Âm)
    if (result_code < 0) {
        switch (result_code) {
            case ERR_NETWORK_CONN_FAILED:
            case ERR_NETWORK_SEND_FAILED:
            case ERR_NETWORK_RECV_FAILED:
                exClass = g_NetworkException;
                msg = "Lỗi kết nối mạng hoặc Socket bị đóng";
                break;
            case ERR_PROTOCOL_MISMATCH:
                exClass = g_ProtocolException;
                msg = "Lỗi giao thức: Phản hồi từ Server không khớp lệnh";
                break;
            case ERR_PROTOCOL_SIZE_ERR:
                exClass = g_ProtocolException;
                msg = "Lỗi giao thức: Kích thước dữ liệu không hợp lệ";
                break;
            case ERR_INTERNAL_MEM:
                msg = "Lỗi bộ nhớ thiết bị (Malloc fail)";
                break;
            default:
                msg = "Lỗi nội bộ Client không xác định";
                break;
        }
    }

        // 2. Xử lý Lỗi Server (Số Dương)
    else {
        switch (result_code) {
            case STATUS_ERROR_AUTH:
                exClass = g_AuthException;
                msg = "Sai email hoặc mật khẩu";
                break;
            case STATUS_ERROR_USER_NOT_FOUND:
                exClass = g_UserNotFoundException;
                msg = "Tài khoản không tồn tại";
                break;
            case STATUS_ERROR_ALREADY_EXIST:
                exClass = g_UserExistException;
                msg = "Email này đã được đăng ký";
                break;
            case STATUS_ERROR_DB:
                exClass = g_ServerException;
                msg = "Lỗi xử lý Database phía Server";
                break;
            case STATUS_ERROR_INVALID_PARAM:
                msg = "Dữ liệu gửi lên không hợp lệ";
                break;
            case STATUS_ERROR_UNKNOWN:
            default:
                exClass = g_ServerException;
                msg = "Server trả về lỗi không xác định";
                break;
        }
    }

    (*env)->ThrowNew(env, exClass, msg);
}

// --- 2. JNI INIT & EXPORTS ---
// --- JNI_OnLoad: Chạy 1 lần khi App start ---
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    jclass lClass = (*env)->FindClass(env, "com/example/konnichat/data/remote/NativeEventListener");
    m_onFriendList = (*env)->GetMethodID(env, lClass, "onFriendListReceived",
                                         "([Lcom/example/konnichat/data/remote/dto/UserDto;)V");
    m_onStatus = (*env)->GetMethodID(env, lClass, "onFriendStatusChanged", "(IZ)V");
    m_onFriendReq = (*env)->GetMethodID(env, lClass, "onFriendRequestReceived",
                                        "(IILjava/lang/String;)V");
    m_onReqResp = (*env)->GetMethodID(env, lClass, "onRequestResponse", "(II)V");
    m_onReqAccepted = (*env)->GetMethodID(env, lClass, "onFriendRequestAccepted",
                                          "(Lcom/example/konnichat/data/remote/dto/UserDto;)V");
    m_onUnfriended = (*env)->GetMethodID(env, lClass, "onFriendRemoved", "(I)V");
    m_onSearchResult = (*env)->GetMethodID(env, lClass, "onSearchResult", "([Lcom/example/konnichat/data/remote/dto/UserSearchDto;)V");
    m_onMsgSent = (*env)->GetMethodID(env, lClass, "onMessageSent", "(IIJ)V");
    m_onMsgReceived = (*env)->GetMethodID(env, lClass, "onMessageReceived", "(Lcom/example/konnichat/data/remote/dto/MessageDto;)V");
    m_onMsgDelivered = (*env)->GetMethodID(env, lClass, "onMessageDelivered", "(I)V");
    m_onHistoryReceived = (*env)->GetMethodID(env, lClass, "onHistoryReceived", "([Lcom/example/konnichat/data/remote/dto/MessageDto;)V");
    m_onGroupCreated = (*env)->GetMethodID(env, lClass, "onGroupCreated", "(ILjava/lang/String;)V");
    m_onDisconnect = (*env)->GetMethodID(env, lClass, "onConnectionClosed",
                                         "(Ljava/lang/String;)V");
    // Cache UserDto
    jclass uClass = (*env)->FindClass(env, "com/example/konnichat/data/remote/dto/UserDto");
    c_UserDto = (jclass) (*env)->NewGlobalRef(env, uClass);
    m_UserDtoInit = (*env)->GetMethodID(env, c_UserDto, "<init>",
                                        "(ILjava/lang/String;Ljava/lang/String;Z)V");

    jclass sClass = (*env)->FindClass(env, "com/example/konnichat/data/remote/dto/UserSearchDto");
    c_UserSearchDto = (jclass) (*env)->NewGlobalRef(env, sClass);
    m_UserSearchDtoInit = (*env)->GetMethodID(env, c_UserSearchDto, "<init>", "(ILjava/lang/String;Ljava/lang/String;I)V");

    jclass mClass = (*env)->FindClass(env, "com/example/konnichat/data/remote/dto/MessageDto");
    c_MessageDto = (jclass) (*env)->NewGlobalRef(env, mClass);
    m_MessageDtoInit = (*env)->GetMethodID(env, c_MessageDto, "<init>", "(IIILjava/lang/String;JILjava/lang/String;)V");

    jclass pClass = (*env)->FindClass(env, "com/example/konnichat/data/remote/dto/PendingRequestDto");
    c_PendingRequestDto = (jclass) (*env)->NewGlobalRef(env, pClass);
    m_PendingRequestDtoInit = (*env)->GetMethodID(env, c_PendingRequestDto, "<init>", "(IILjava/lang/String;)V");

    // Cache Callback
    m_onPendingList = (*env)->GetMethodID(env, lClass, "onPendingRequestsReceived", "([Lcom/example/konnichat/data/remote/dto/PendingRequestDto;)V");

    // Cache Exceptions
    jclass c1 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/AuthenticationException");
    g_AuthException = (jclass) (*env)->NewGlobalRef(env, c1);

    jclass c2 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/UserNotFoundException");
    g_UserNotFoundException = (jclass) (*env)->NewGlobalRef(env, c2);

    jclass c3 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/UserAlreadyExistsException");
    g_UserExistException = (jclass) (*env)->NewGlobalRef(env, c3);

    jclass c4 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/ServerInternalException");
    g_ServerException = (jclass) (*env)->NewGlobalRef(env, c4);

    jclass c5 = (*env)->FindClass(env, "com/example/konnichat/core/exception/NetworkException");
    g_NetworkException = (jclass) (*env)->NewGlobalRef(env, c5);

    jclass c6 = (*env)->FindClass(env, "com/example/konnichat/core/exception/ProtocolException");
    g_ProtocolException = (jclass) (*env)->NewGlobalRef(env, c6);

    jclass c7 = (*env)->FindClass(env, "java/lang/Exception"); // Fallback
    g_UnknownException = (jclass) (*env)->NewGlobalRef(env, c7);

    return JNI_VERSION_1_6;
}




void Java_com_example_konnichat_data_remote_NativeClient_startListening(JNIEnv *env, jobject thiz,
                                                                        jobject listener) {
    // 1. Giữ Global Ref cho listener để không bị GC thu hồi
    if (g_listener != NULL)
        (*env)->DeleteGlobalRef(env, g_listener);
    g_listener = (*env)->NewGlobalRef(env, listener);

    // 2. Chuẩn bị struct callbacks
    NativeCallbacks cbs;
    cbs.on_friend_list = jni_on_friend_list;
    cbs.on_status_change = jni_on_status_change;
    cbs.on_friend_req = jni_on_friend_req;
    cbs.on_req_response = jni_on_req_response;
    cbs.on_request_accepted = jni_on_req_accepted;
    cbs.on_unfriended = jni_on_unfriended;
    cbs.on_search_result = jni_on_search_result;
    cbs.on_msg_sent = jni_on_msg_sent;
    cbs.on_message = jni_on_message;
    cbs.on_msg_delivered = jni_on_msg_delivered;
    cbs.on_history_received = jni_on_history_received;
    cbs.on_group_created = jni_on_group_created;
    cbs.on_disconnect = jni_on_disconnect;
    cbs.on_pending_list = jni_on_pending_list;
    // 3. Start C Thread
    start_reader_thread(cbs);
}

// --- Implementation ---
jint
Java_com_example_konnichat_data_remote_NativeClient_connect(JNIEnv *env, jobject thiz, jstring ip,
                                                            jint port) {
    const char *native_ip = (*env)->GetStringUTFChars(env, ip, 0);
    int result = client_init(native_ip, (int) port);
    (*env)->ReleaseStringUTFChars(env, ip, native_ip);
    return result;
}

void Java_com_example_konnichat_data_remote_NativeClient_disconnect(JNIEnv *env, jobject thiz) {
    client_close();
}

jint Java_com_example_konnichat_data_remote_NativeClient_registerUser(JNIEnv *env, jobject thiz,
                                                                      jstring name, jstring email,
                                                                      jstring password) {
    const char *n_name = (*env)->GetStringUTFChars(env, name, 0);
    const char *n_email = (*env)->GetStringUTFChars(env, email, 0);
    const char *n_pass = (*env)->GetStringUTFChars(env, password, 0);

    int status = client_register(n_name, n_email, n_pass);

    (*env)->ReleaseStringUTFChars(env, name, n_name);
    (*env)->ReleaseStringUTFChars(env, email, n_email);
    (*env)->ReleaseStringUTFChars(env, password, n_pass);

    if (status != STATUS_SUCCESS) {
        throw_unified_error(env, status);
        return status;
    }

    return status;
}

/**
 * Đăng nhập User.
 *
 * @return UserDto object nếu thành công.
 * @throws NativeException (các subclass) nếu thất bại.
 */
jobject Java_com_example_konnichat_data_remote_NativeClient_loginUser(JNIEnv *env, jobject thiz,
                                                                      jstring email,
                                                                      jstring password) {
    const char *n_email = (*env)->GetStringUTFChars(env, email, 0);
    const char *n_pass = (*env)->GetStringUTFChars(env, password, 0);

    UserInfoPayload userInfo;
    memset(&userInfo, 0, sizeof(userInfo));

    int status = client_login(n_email, n_pass, &userInfo);

    (*env)->ReleaseStringUTFChars(env, email, n_email);
    (*env)->ReleaseStringUTFChars(env, password, n_pass);

    if (status == STATUS_SUCCESS) {
        jstring jName = (*env)->NewStringUTF(env, userInfo.name);
        jstring jEmail = (*env)->NewStringUTF(env, userInfo.email);
        jboolean jIsOnline = (userInfo.is_online == 1) ? JNI_TRUE : JNI_FALSE;

        // New Object
        jobject userObj = (*env)->NewObject(env, c_UserDto, m_UserDtoInit,
                                            userInfo.user_id, jName, jEmail, jIsOnline);
        return userObj;
    } else {
        throw_unified_error(env, status);
        return NULL;
    }
}

/**
 * Gửi yêu cầu lấy danh sách bạn bè.
 * Kotlin: external fun getFriends(offset: Int, limit: Int): Int
 */
jint Java_com_example_konnichat_data_remote_NativeClient_getFriends(JNIEnv *env, jobject thiz,
                                                                    jint offset, jint limit) {
    // Gọi hàm C
    int result = client_get_friends(offset, limit);

    // Nếu gửi thất bại (mất mạng, lỗi socket), ném lỗi
    if (result < 0) {
        throw_unified_error(env, result);
    }

    return (jint) result;
}

void
Java_com_example_konnichat_data_remote_NativeClient_sendFriendRequest(JNIEnv *env, jobject thiz,
                                                                      jint targetId) {
    int res = client_send_friend_request(targetId);
    if (res < 0)
        throw_unified_error(env, res);
}

void
Java_com_example_konnichat_data_remote_NativeClient_respondFriendRequest(JNIEnv *env, jobject thiz,
                                                                         jint requestId,
                                                                         jboolean isAccepted) {
    int acceptVal = (isAccepted == JNI_TRUE) ? 1 : 0;

    // Gọi xuống native core
    int status = client_respond_friend_req(requestId, acceptVal);

    if (status != STATUS_SUCCESS) {
        throw_unified_error(env, status);
    }
}

JNIEXPORT void JNICALL
Java_com_example_konnichat_data_remote_NativeClient_unfriendUser(JNIEnv *env, jobject thiz, jint targetId) {
    int status = client_unfriend(targetId);
    if (status != CLIENT_OK) {
        throw_unified_error(env, status);
    }
}

JNIEXPORT void JNICALL
Java_com_example_konnichat_data_remote_NativeClient_searchUsers(JNIEnv *env, jobject thiz,
                                                                jstring keyword, jint offset, jint limit) {
    const char *n_keyword = (*env)->GetStringUTFChars(env, keyword, 0);
    client_search_users(n_keyword, offset, limit);
    (*env)->ReleaseStringUTFChars(env, keyword, n_keyword);
}

JNIEXPORT void JNICALL
Java_com_example_konnichat_data_remote_NativeClient_sendMessage(JNIEnv *env, jobject thiz,
                                                                jint receiverId, jstring content, jint tempId, jstring chatType) {
    const char *n_content = (*env)->GetStringUTFChars(env, content, 0);
    const char *n_chatType = (*env)->GetStringUTFChars(env, chatType, 0);

    client_send_message(receiverId, n_content, tempId, n_chatType);

    (*env)->ReleaseStringUTFChars(env, content, n_content);
    (*env)->ReleaseStringUTFChars(env, chatType, n_chatType);
}

JNIEXPORT void JNICALL
Java_com_example_konnichat_data_remote_NativeClient_fetchOfflineMessages(JNIEnv *env, jobject thiz) {
    client_fetch_offline_msgs();
}

JNIEXPORT void JNICALL
Java_com_example_konnichat_data_remote_NativeClient_getChatHistory(JNIEnv *env, jobject thiz,
                                                                   jint targetId, jint offset, jint limit) {
    client_get_history(targetId, offset, limit);
}

JNIEXPORT void JNICALL
Java_com_example_konnichat_data_remote_NativeClient_getPendingRequests(JNIEnv *env, jobject thiz) {
    // Gọi hàm Core C
    client_get_pending_requests();
}

Java_com_example_konnichat_data_remote_NativeClient_createGroup(JNIEnv *env, jobject thiz,
                                                                jstring name, jintArray members) {
    const char *n_name = (*env)->GetStringUTFChars(env, name, 0);

    // Chuyển jintArray -> C array
    jsize len = (*env)->GetArrayLength(env, members);
    jint *body = (*env)->GetIntArrayElements(env, members, 0);

    int status = client_create_group(n_name, (int32_t*)body, (int)len);

    // Giải phóng
    (*env)->ReleaseIntArrayElements(env, members, body, 0);
    (*env)->ReleaseStringUTFChars(env, name, n_name);

    if (status != CLIENT_OK) {
        throw_unified_error(env, status);
    }
}
