#include "../include/utils/logger.h"
#include <stdarg.h>
#include <stdlib.h>

static FILE *log_file = NULL; // Biến static giữ con trỏ file

// Khởi tạo file log (mở file để ghi nối tiếp)
void init_logger(const char *file_path) {
    if (file_path == NULL) return;
    log_file = fopen(file_path, "a");
    if (log_file == NULL) {
        perror("Cannot open log file");
    }
}

// Đóng file log khi server tắt
void close_logger() {
    if (log_file != NULL) {
        fclose(log_file);
        log_file = NULL;
    }
}

// Hàm xử lý log trung tâm: Ghi cả màn hình và file
void log_message(const char *color, const char *level_tag, const char *filename, int line, const char *fmt, ...) {
    // 1. Lấy thời gian hiện tại
    char time_buf[32];
    get_current_time_str(time_buf);

    // 2. Xử lý danh sách tham số (variadic arguments)
    va_list args1, args2;
    va_start(args1, fmt);
    va_copy(args2, args1); // Copy để dùng cho lần ghi thứ 2 (ghi file)

    // --- GHI RA MÀN HÌNH (Có màu) ---
    fprintf(stdout, "%s[%s] [%s] %s:%d - ", color, time_buf, level_tag, filename, line);
    vfprintf(stdout, fmt, args1);
    fprintf(stdout, "%s\n", COLOR_RESET);
    fflush(stdout); // Đẩy dữ liệu ra ngay lập tức

    // --- GHI RA FILE (Không màu) ---
    if (log_file != NULL) {
        fprintf(log_file, "[%s] [%s] %s:%d - ", time_buf, level_tag, filename, line);
        vfprintf(log_file, fmt, args2);
        fprintf(log_file, "\n");
        fflush(log_file); // Đảm bảo ghi xuống đĩa ngay (an toàn khi crash)
    }

    va_end(args1);
    va_end(args2);
}