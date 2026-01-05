package com.example.konnichat.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.konnichat.data.local.entity.UserEntity

/**
 * Class chứa thông tin chi tiết của thành viên nhóm.
 * Kết hợp từ bảng 'users' và cột 'role' của bảng 'group_members'.
 */
data class GroupMemberWithUser(
    @Embedded val user: UserEntity,

    @ColumnInfo(name = "role")
    val role: String
)