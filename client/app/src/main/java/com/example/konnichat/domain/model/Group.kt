package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.NotificationState

data class Group(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    val notification: NotificationState,
    val createdAt: String
)
