#ifndef CLIENT_HANDLER_H
#define CLIENT_HANDLER_H

// Hàm này chạy trên thread riêng để phục vụ từng client kết nối TCP
void *handle_client(void *socket_desc);

#endif
