#ifndef LOGGER_UTILS_H
#define LOGGER_UTILS_H

#include <android/log.h>
#include "../protocol.h"

#define TAG "KONNI_NATIVE"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Hàm chuyển Enum thành chuỗi để log
const char *cmd_to_string(int cmd);

const char *status_to_string(int status);

#endif
