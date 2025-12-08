package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.MessageStatus

data class Message(
    val id: Int,
    val senderId: Int,
    val receiverId: Int,
    val content: String?,
    val status: MessageStatus,
    val createdAt: String,
    val updatedAt: String
)
