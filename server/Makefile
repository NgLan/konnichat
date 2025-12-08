# Trình biên dịch
CC = gcc

# Cờ biên dịch (Compiler Flags)
# -I./include: Tìm file header .h trong thư mục include
# -Wall: Hiển thị tất cả cảnh báo (Warning) để dễ fix lỗi
# -g: Thêm thông tin debug (để dùng gdb nếu crash)
CFLAGS = -I./include -Wall -g

# Thư viện liên kết (Linker Flags)
# -lpthread: Cho đa luồng
# -lmysqlclient: Cho MySQL Server
LIBS = -lpthread -lmysqlclient

# Danh sách file nguồn (Source files)
# Đã thêm src/discovery.c vào đây
SRCS = src/main.c src/client_handler.c src/db_manager.c src/dotenv.c src/discovery.c

# Danh sách file object (Tự động đổi đuôi .c thành .o)
OBJS = $(SRCS:.c=.o)

# Tên file thực thi đầu ra
TARGET = myserver

# --- QUY TẮC BIÊN DỊCH (TARGETS) ---

# Mục tiêu mặc định khi gõ 'make'
all: $(TARGET)

# Quy tắc tạo file thực thi
$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LIBS)
	@echo "Build thanh cong! Chay ./$(TARGET) de khoi dong server."

# Quy tắc tạo file object từ file .c
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# Dọn dẹp các file rác (.o) và file thực thi cũ
clean:
	rm -f src/*.o $(TARGET)

# Khai báo các mục tiêu giả (không phải tên file)
.PHONY: all clean
