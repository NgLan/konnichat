package com.example.konnichat.data.remote.dto

data class PendingRequestDto(
    val requestId: Int,
    val senderId: Int,
    val senderName: String
)