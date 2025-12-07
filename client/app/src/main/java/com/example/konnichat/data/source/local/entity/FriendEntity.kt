package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Friends",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["friend_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["user_id", "friend_id"], unique = true)]
)
data class FriendEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "friend_id") val friendId: Int,
    val notification: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)
