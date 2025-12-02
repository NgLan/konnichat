#include "../include/dotenv.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Hàm cắt khoảng trắng thừa
static void trim(char *str) {
    // Cắt cuối
    size_t len = strlen(str);
    while(len > 0 && (str[len-1] == '\n' || str[len-1] == '\r' || str[len-1] == ' ')) {
        str[len-1] = '\0';
        len--;
    }
}

void env_load(const char *path) {
    FILE *file = fopen(path, "r");
    if (!file) {
        fprintf(stderr, "[WARN] Không tìm thấy file %s. Sử dụng biến môi trường hệ thống.\n", path);
        return;
    }

    char line[1024];
    while (fgets(line, sizeof(line), file)) {
        // Bỏ qua comment và dòng trống
        if (line[0] == '#' || line[0] == '\n' || line[0] == '\r') continue;

        char *key = strtok(line, "=");
        char *val = strtok(NULL, "\n"); // Lấy phần còn lại

        if (key && val) {
            trim(key);
            trim(val);
            // setenv là hàm chuẩn POSIX để thiết lập biến môi trường
            // 1 nghĩa là ghi đè nếu đã tồn tại
            setenv(key, val, 1);
        }
    }
    fclose(file);
}
