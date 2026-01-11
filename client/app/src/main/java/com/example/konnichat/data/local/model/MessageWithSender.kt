package com.example.konnichat.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.entity.ReactionEntity

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
    val senderAvatar: String?,

    @Relation(
    parentColumn = "server_id",  // Tên cột ID trong bảng messages (MessageEntity)
    entityColumn = "message_id"  // Tên cột ID tin nhắn trong bảng reactions (ReactionEntity)
    )
    val reactions: List<ReactionEntity>? = null
)