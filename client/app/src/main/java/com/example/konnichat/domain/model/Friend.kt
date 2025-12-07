package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.NotificationState

data class Friend(
    val id: Int,
    val userId: Int,
    val friendId: Int,
    val notification: NotificationState,
    val createdAt: String
)
