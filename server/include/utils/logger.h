#ifndef LOGGER_H
#define LOGGER_H

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <time.h>
#include <string.h>
#include <stdint.h>

// Màu sắc cho Console
#define COLOR_RESET "\x1b[0m"
#define COLOR_RED "\x1b[31m"
#define COLOR_GREEN "\x1b[32m"
#define COLOR_YELLOW "\x1b[33m"
#define COLOR_BLUE "\x1b[34m"

// Hàm tiện ích lấy timestamp
static inline uint64_t get_current_timestamp_ms()
{
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (uint64_t)(ts.tv_sec) * 1000 + (uint64_t)(ts.tv_nsec) / 1000000;
}

// Macro lấy tên file
#define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)

// --- KHAI BÁO HÀM ---
void init_logger(const char *file_path);
void close_logger();
void log_message_impl(const char *color, const char *level_tag, const char *filename, int line, const char *fmt, ...);

// --- ĐỊNH NGHĨA MACRO GỌI HÀM ---
#define LOG_INFO(...) log_message_impl(COLOR_GREEN, "INFO", __FILENAME__, __LINE__, __VA_ARGS__)
#define LOG_WARN(...) log_message_impl(COLOR_YELLOW, "WARN", __FILENAME__, __LINE__, __VA_ARGS__)
#define LOG_ERROR(...) log_message_impl(COLOR_RED, "ERROR", __FILENAME__, __LINE__, __VA_ARGS__)
#define LOG_DEBUG(...) log_message_impl(COLOR_BLUE, "DEBUG", __FILENAME__, __LINE__, __VA_ARGS__)

#endif // LOGGER_H
