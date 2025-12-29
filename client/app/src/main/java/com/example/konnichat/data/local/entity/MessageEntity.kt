package com.example.konnichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["sender_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sender_id"),
        Index("receiver_id"),
        Index("created_at")
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "server_id") val serverId: Int,

    @ColumnInfo(name = "sender_id") val senderId: Int,
    @ColumnInfo(name = "receiver_id") val receiverId: Int, // Lưu UserID (nếu private) hoặc GroupID (nếu group)
    @ColumnInfo(name = "chat_type") val chatType: String, // 'private', 'group'

    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "status") val status: String = "sending", // 'sending', 'sent', 'delivered', 'read', 'revoked', 'deleted', 'failed'

    @ColumnInfo(name = "created_at") override val createdAt: Date = Date(),
    @ColumnInfo(name = "updated_at") override val updatedAt: Date = Date()
) : HasCreatedAt, HasUpdatedAt
