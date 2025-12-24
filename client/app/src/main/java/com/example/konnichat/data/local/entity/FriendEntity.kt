package com.example.konnichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "friends",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["friend_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_id"), Index("friend_id")]
)
data class FriendEntity(
    @PrimaryKey
    @ColumnInfo(name = "server_id") val serverId: Int,
    @ColumnInfo(name = "user_id") val userId: Int,      // ID của mình
    @ColumnInfo(name = "friend_id") val friendId: Int,  // ID của bạn
    @ColumnInfo(name = "notification") val notification: String, // 'on', 'off'
    @ColumnInfo(name = "created_at") override val createdAt: Date = Date()
) : HasCreatedAt
