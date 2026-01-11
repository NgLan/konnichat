# Trình biên dịch
CC = gcc

# Cờ biên dịch: Include đủ các thư mục header
CFLAGS = -I./include -I./include/utils -I./include/repo -Wall -g

# Thư viện liên kết
LIBS = -lpthread -lmysqlclient

# Danh sách thư mục
SRC_DIR = src
REPO_DIR = src/repo
UTILS_DIR = src/utils

# Danh sách file nguồn 
SRCS = $(SRC_DIR)/main.c \
       $(SRC_DIR)/client_handler.c \
       $(SRC_DIR)/database.c \
       $(SRC_DIR)/dotenv.c \
       $(SRC_DIR)/connection_manager.c \
       $(SRC_DIR)/tcp_server.c \
       $(REPO_DIR)/user_repo.c \
       $(REPO_DIR)/friend_repo.c \
       $(REPO_DIR)/message_repo.c \
       $(REPO_DIR)/group_repo.c \
       $(UTILS_DIR)/logger.c

# Tạo danh sách file object (.o) tương ứng
OBJS = $(SRCS:.c=.o)

# Tên file thực thi chính
TARGET = myserver

# --- QUY TẮC BIÊN DỊCH ---

all: $(TARGET)

# Link các file .o thành file thực thi
$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LIBS)
	@echo "Build Server successful! Run ./$(TARGET)"

# Quy tắc biên dịch từng file .c thành .o
# Sử dụng pattern rule %.o : %.c
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(SRC_DIR)/*.o $(REPO_DIR)/*.o $(UTILS_DIR)/*.o $(TARGET)

.PHONY: all clean
