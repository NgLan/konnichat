package com.example.konnichat.data.local.model

import java.util.Date

// Class này không tạo bảng mới, chỉ dùng để hứng dữ liệu từ câu lệnh SQL
data class ConversationItem(
    val friendId: Int,          // ID của bạn bè
    val friendName: String,     // Tên bạn bè
    val avatar: String?,        // Link ảnh (có thể null)
    val isOnline: Boolean,      // Trạng thái Online
    val lastMessage: String?,   // Nội dung tin nhắn cuối cùng (có thể null nếu chưa chat)
    val lastMessageTime: Date?  // Thời gian nhắn cuối
)