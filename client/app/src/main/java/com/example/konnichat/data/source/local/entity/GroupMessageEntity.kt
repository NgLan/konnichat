package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "GroupMessages",
    foreignKeys = [
        ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["sender_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class GroupMessageEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "sender_id") val senderId: Int,
    val content: String?,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
