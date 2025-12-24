package com.example.konnichat.data.model

data class User(
    val id: Int,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val status: UserStatus
)

enum class UserStatus {
    ACTIVE, BANNED, UNKNOWN
}
