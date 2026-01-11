#include "../../include/utils/logger.h"
#include <stdarg.h>
#include <stdlib.h>
#include <pthread.h>

static FILE *log_file = NULL;
static pthread_mutex_t log_mutex = PTHREAD_MUTEX_INITIALIZER;

// Hàm lấy chuỗi thời gian nội bộ
static void get_time_str(char *buffer, size_t size)
{
    time_t rawtime;
    struct tm *timeinfo;
    time(&rawtime);
    timeinfo = localtime(&rawtime);
    strftime(buffer, size, "%Y-%m-%d %H:%M:%S", timeinfo);
}

void init_logger(const char *file_path)
{
    if (file_path == NULL)
        return;

    pthread_mutex_lock(&log_mutex);
    log_file = fopen(file_path, "a");
    if (log_file == NULL)
    {
        fprintf(stderr, "-----------------------------------------------------\n");
        perror("Cannot open log file");
        fprintf(stderr, "-----------------------------------------------------\n");
    } else {
        // Lấy thời gian hiện tại
        char time_buf[32];
        get_time_str(time_buf, sizeof(time_buf));
        
        fprintf(log_file, "\n--- SESSION START: %s ---\n", time_buf);
        fflush(log_file);
    }
    pthread_mutex_unlock(&log_mutex);
}

void close_logger()
{
    pthread_mutex_lock(&log_mutex);
    if (log_file != NULL)
    {
        fclose(log_file);
        log_file = NULL;
    }
    pthread_mutex_unlock(&log_mutex);
}

// Hàm xử lý log 
void log_message_impl(const char *color, const char *level_tag, const char *filename, int line, const char *fmt, ...)
{
    char time_buf[32];
    get_time_str(time_buf, sizeof(time_buf));

    va_list args_console, args_file;
    va_start(args_console, fmt);
    va_copy(args_file, args_console); // Copy arg list cho ghi file

    // BẮT ĐẦU KHÓA (Để đảm bảo 1 dòng log được ghi trọn vẹn)
    pthread_mutex_lock(&log_mutex);

    // 1. GHI RA MÀN HÌNH (Có màu)
    // Format: [TIME] [LEVEL] File:Line - Message
    fprintf(stdout, "%s[%s] [%s] %s:%d - ", color, time_buf, level_tag, filename, line);
    vfprintf(stdout, fmt, args_console);
    fprintf(stdout, "%s\n", COLOR_RESET);
    fflush(stdout);

    // 2. GHI RA FILE (Không màu)
    if (log_file != NULL)
    {
        fprintf(log_file, "[%s] [%s] %s:%d - ", time_buf, level_tag, filename, line);
        vfprintf(log_file, fmt, args_file);
        fprintf(log_file, "\n");
        fflush(log_file); // Ghi xuống đĩa ngay lập tức
    }

    pthread_mutex_unlock(&log_mutex);
    // KẾT THÚC KHÓA

    va_end(args_console);
    va_end(args_file);
}
