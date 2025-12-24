#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "../include/native_core.h"
#include "../../include/utils/logger_utils.h"
#include <android/log.h>

// --- Global Cache (Để tối ưu hiệu năng) ---
static jclass g_UserDtoClass;
static jmethodID g_UserDtoConstructor;

// Các class Exception
static jclass g_AuthException;
static jclass g_UserNotFoundException;
static jclass g_UserExistException;
static jclass g_ServerException;
static jclass g_NetworkException;
static jclass g_ProtocolException;
static jclass g_UnknownException;

// --- JNI_OnLoad: Chạy 1 lần khi App start ---
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    // Cache UserDto
    jclass tempClass = (*env)->FindClass(env, "com/example/konnichat/data/remote/dto/UserDto");
    g_UserDtoClass = (jclass)(*env)->NewGlobalRef(env, tempClass);

    // Constructor signature: (ILjava/lang/String;Ljava/lang/String;Z)V -> Z là boolean
    g_UserDtoConstructor = (*env)->GetMethodID(env, g_UserDtoClass, "<init>",
                                               "(ILjava/lang/String;Ljava/lang/String;Z)V");

    // Cache Exceptions
    jclass c1 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/AuthenticationException");
    g_AuthException = (jclass)(*env)->NewGlobalRef(env, c1);

    jclass c2 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/UserNotFoundException");
    g_UserNotFoundException = (jclass)(*env)->NewGlobalRef(env, c2);

    jclass c3 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/UserAlreadyExistsException");
    g_UserExistException = (jclass)(*env)->NewGlobalRef(env, c3);

    jclass c4 = (*env)->FindClass(env,
                                  "com/example/konnichat/core/exception/ServerInternalException");
    g_ServerException = (jclass)(*env)->NewGlobalRef(env, c4);

    jclass c5 = (*env)->FindClass(env, "com/example/konnichat/core/exception/NetworkException");
    g_NetworkException = (jclass)(*env)->NewGlobalRef(env, c5);

    jclass c6 = (*env)->FindClass(env, "com/example/konnichat/core/exception/ProtocolException");
    g_ProtocolException = (jclass)(*env)->NewGlobalRef(env, c6);

    jclass c7 = (*env)->FindClass(env, "java/lang/Exception"); // Fallback
    g_UnknownException = (jclass)(*env)->NewGlobalRef(env, c7);

    return JNI_VERSION_1_6;
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
        jobject userObj = (*env)->NewObject(env, g_UserDtoClass, g_UserDtoConstructor,
                                            userInfo.user_id, jName, jEmail, jIsOnline);
        return userObj;
    } else {
        throw_native_error(env, status);
        return NULL;
    }
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
