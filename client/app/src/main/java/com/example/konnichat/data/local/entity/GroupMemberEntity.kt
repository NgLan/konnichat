package com.example.konnichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "member_id"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["server_id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("group_id"), Index("member_id")]
)
data class GroupMemberEntity(
    @ColumnInfo(name = "server_id") val serverId: Int,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "member_id") val memberId: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "joined_at") val joinedAt: Date = Date()
) : HasCreatedAt {
    override val createdAt: Date get() = joinedAt
}
