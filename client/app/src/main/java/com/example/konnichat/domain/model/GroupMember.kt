package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.GroupRole
import com.example.konnichat.domain.enums.MemberStatus

data class GroupMember(
    val id: Int,
    val groupId: Int,
    val memberId: Int,
    val status: MemberStatus,
    val role: GroupRole,
    val joinedAt: String
)
