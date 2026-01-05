package com.example.konnichat.data.local.model

import java.util.Date

// [SỬA] Cập nhật toàn bộ class để dùng chung cho User và Group
data class ConversationItem(
    val id: Int,              // Thay thế friendId. Là UserID hoặc GroupID
    val name: String,         // Thay thế friendName. Là Tên User hoặc Tên Group
    val avatar: String?,
    val isOnline: Boolean,    // True: User Online. False: User Offline hoặc Group
    val chatType: String,     // [MỚI] "private" hoặc "group"
    val lastMessage: String?,
    val lastMessageTime: Date?
)