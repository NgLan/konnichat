package com.example.konnichat.data.remote.dto

// Dùng để hứng dữ liệu JSON/Struct từ Native C gửi lên
data class UserSearchDto(
    val userId: Int,
    val name: String,
    val email: String
)