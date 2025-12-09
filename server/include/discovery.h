#ifndef DISCOVERY_H
#define DISCOVERY_H

// Hàm này chạy trên thread riêng để lắng nghe UDP Broadcast
void *udp_discovery_service(void *arg);

#endif
