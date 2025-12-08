package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "GroupMembers",
    foreignKeys = [
        ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["member_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["group_id", "member_id"], unique = true)]
)
data class GroupMemberEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "member_id") val memberId: Int,
    val status: String,
    val role: String,
    @ColumnInfo(name = "joined_at") val joinedAt: String
)
