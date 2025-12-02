CC = gcc
CFLAGS = -I./include -Wall
LIBS = -lpthread -lmysqlclient

SRCS = src/main.c src/client_handler.c src/db_manager.c src/dotenv.c

OBJS = $(SRCS:.c=.o)
TARGET = myserver

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) -o $@ $^ $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f src/*.o $(TARGET)
