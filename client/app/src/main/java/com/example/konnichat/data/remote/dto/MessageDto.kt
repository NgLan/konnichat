package com.example.konnichat.data.remote.dto

/**
 * Data Transfer Object cho tin nhắn.
 * Dùng để chuyển dữ liệu từ Native C lên Kotlin.
 */
data class MessageDto(
    val id: Int,            // Message ID (Server ID)
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val timestamp: Long,    // Thời gian server
    val type: Int = 1,      // 1: Text, 2: Image...
    val chatType: String = "private",   // 1: Private, 2: Group
    val status: Int
)
