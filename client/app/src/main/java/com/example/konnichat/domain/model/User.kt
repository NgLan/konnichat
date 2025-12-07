package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.OnlineStatus
import com.example.konnichat.domain.enums.UserStatus

data class User(
    val id: Int,
    val email: String,
    val name: String,
    val age: Int?,
    val status: UserStatus,
    val isOnline: OnlineStatus,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String
)
