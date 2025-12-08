CC = gcc
CFLAGS = -I./include -Wall
LIBS = -lpthread -lmysqlclient

# 1. Danh sách các file nguồn của Server (Đã thêm client_manager.c)
SRCS = src/main.c src/client_handler.c src/db_manager.c src/dotenv.c src/client_manager.c
OBJS = $(SRCS:.c=.o)

# Tên file thực thi
TARGET_SERVER = myserver
TARGET_CLIENT = client_test

# Mặc định gõ 'make' sẽ build cả server và test client
all: $(TARGET_SERVER) $(TARGET_CLIENT)

# Build Server
$(TARGET_SERVER): $(OBJS)
	$(CC) -o $@ $^ $(LIBS)

# Build Test Client (File test_client.c nằm ở thư mục gốc)
$(TARGET_CLIENT): src/test_client.c
	$(CC) $(CFLAGS) -o $@ test_client.c

# Quy tắc biên dịch file .c thành .o
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f src/*.o $(TARGET_SERVER) $(TARGET_CLIENT)

.PHONY: all clean