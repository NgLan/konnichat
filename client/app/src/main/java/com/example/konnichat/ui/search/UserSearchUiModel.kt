package com.example.konnichat.ui.search

data class UserSearchUiModel(
    val id: Int,
    val name: String,
    val email: String,
    val isFriend: Boolean // Biến quan trọng để quyết định hiện nút "Kết bạn" hay "Nhắn tin"
)