package com.example.konnichat.data.dto

data class NativeMessageDto(
    val serverMsgId: Int,
    val senderId: Int,
    val content: String,
    val timestamp: String
)
