package com.example.konnichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "friend_requests",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["sender_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["receiver_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sender_id"), Index("receiver_id")]
)
data class FriendRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "server_id") val serverId: Int,
    @ColumnInfo(name = "sender_id") val senderId: Int,
    @ColumnInfo(name = "receiver_id") val receiverId: Int,
    @ColumnInfo(name = "status") val status: String, // 'waiting', 'approved', 'denied'
    @ColumnInfo(name = "created_at") override val createdAt: Date = Date(),
    @ColumnInfo(name = "updated_at") override val updatedAt: Date = Date()
) : HasCreatedAt, HasUpdatedAt
