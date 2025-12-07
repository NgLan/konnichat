package com.example.konnichat.domain.enums

enum class MessageStatus {
    SENDING,   // Đang gửi (Lưu local, chưa ra khỏi máy)
    SENT,      // Đã gửi thành công lên Server
    DELIVERED, // Server báo đã chuyển tới máy người nhận
    READ,      // Người nhận đã xem
    RECEIVED,  // Tin nhắn nhận được từ người khác
    FAILED     // Gửi thất bại
}
