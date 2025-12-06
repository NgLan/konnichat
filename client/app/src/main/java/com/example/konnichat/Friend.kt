package com.example.konnichat

data class Friend(
    val id: Int,
    val name: String,
    val isOnline: Boolean // True nếu server gửi 1, False nếu 0
)
