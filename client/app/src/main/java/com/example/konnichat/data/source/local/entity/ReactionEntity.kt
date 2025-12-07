package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "MessageReactions",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["message_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GroupMessageEntity::class, parentColumns = ["id"], childColumns = ["group_message_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["user_id", "message_id"], unique = true)
    ]
)
data class ReactionEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "icon_id") val iconId: Int,
    @ColumnInfo(name = "message_id") val messageId: Int?,
    @ColumnInfo(name = "group_message_id") val groupMessageId: Int?,
    @ColumnInfo(name = "created_at") val createdAt: String
)
