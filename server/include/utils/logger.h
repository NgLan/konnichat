#ifndef LOGGER_H
#define LOGGER_H

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <time.h>
#include <string.h>
#include <stdint.h>

#define COLOR_RESET "\x1b[0m"
#define COLOR_RED "\x1b[31m"
#define COLOR_GREEN "\x1b[32m"
#define COLOR_YELLOW "\x1b[33m"
#define COLOR_BLUE "\x1b[34m"

// 1. Hàm lấy chuỗi
static inline void get_current_time_str(char *buffer)
{
    time_t rawtime;
    struct tm *timeinfo;
    time(&rawtime);
    timeinfo = localtime(&rawtime);
    strftime(buffer, 20, "%Y-%m-%d %H:%M:%S", timeinfo);
}

// 2. Hàm lấy mili-giây
static inline uint64_t get_current_timestamp_ms()
{
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);

    // Quy đổi ra milliseconds
    return (uint64_t)(ts.tv_sec) * 1000 + (uint64_t)(ts.tv_nsec) / 1000000;
}

#define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)

/**
 * LOG_INFO: Dùng cho thông báo trạng thái bình thường (Màu xanh/trắng)
 * Format: [TIME] [INFO] filename:line - Message
 */
#define LOG_INFO(fmt, ...)                                                                  \
    do                                                                                      \
    {                                                                                       \
        char time_buf[20];                                                                  \
        get_current_time_str(time_buf);                                                     \
        fprintf(stdout, "%s[%s] [INFO]  %s:%d - " fmt "%s\n",                               \
                COLOR_GREEN, time_buf, __FILENAME__, __LINE__, ##__VA_ARGS__, COLOR_RESET); \
    } while (0)

/**
 * LOG_ERROR: Dùng cho thông báo lỗi nghiêm trọng (Màu đỏ)
 * Format: [TIME] [ERROR] filename:line - Message
 */
#define LOG_ERROR(fmt, ...)                                                               \
    do                                                                                    \
    {                                                                                     \
        char time_buf[20];                                                                \
        get_current_time_str(time_buf);                                                   \
        fprintf(stderr, "%s[%s] [ERROR] %s:%d - " fmt "%s\n",                             \
                COLOR_RED, time_buf, __FILENAME__, __LINE__, ##__VA_ARGS__, COLOR_RESET); \
    } while (0)

/**
 * LOG_WARN: Dùng cho cảnh báo (Màu vàng)
 */
#define LOG_WARN(fmt, ...)                                                                   \
    do                                                                                       \
    {                                                                                        \
        char time_buf[20];                                                                   \
        get_current_time_str(time_buf);                                                      \
        fprintf(stdout, "%s[%s] [WARN]  %s:%d - " fmt "%s\n",                                \
                COLOR_YELLOW, time_buf, __FILENAME__, __LINE__, ##__VA_ARGS__, COLOR_RESET); \
    } while (0)

#endif
