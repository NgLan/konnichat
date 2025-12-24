package com.example.konnichat.data.model

import java.util.Date

data class Message(
    val id: Int,
    val senderId: Int,
    val content: String,
    val createdAt: Date,
    val isMine: Boolean, // Field để UI biết tin nhắn của mình hay bạn
    val status: MessageStatus
)

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, REVOKED, DELETED, FAILED
}
