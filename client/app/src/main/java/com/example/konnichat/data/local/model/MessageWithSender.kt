package com.example.konnichat.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.konnichat.data.local.entity.MessageEntity

/**
 * Class này dùng để hiển thị lên UI, bao gồm:
 * - Toàn bộ thông tin tin nhắn (MessageEntity)
 * - Tên người gửi (lấy từ bảng users thông qua JOIN)
 */
data class MessageWithSender(
    @Embedded val message: MessageEntity,

    @ColumnInfo(name = "senderName")
    val senderName: String?, // Tên người gửi (Có thể null nếu user chưa sync về)
    @ColumnInfo(name = "senderAvatar")
    val senderAvatar: String?

)