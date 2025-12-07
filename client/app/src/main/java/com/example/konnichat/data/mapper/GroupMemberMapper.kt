package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.GroupMemberEntity
import com.example.konnichat.domain.enums.GroupRole
import com.example.konnichat.domain.enums.MemberStatus
import com.example.konnichat.domain.model.GroupMember

class GroupMemberMapper {
    fun mapToDomain(entity: GroupMemberEntity): GroupMember {
        return GroupMember(
            id = entity.id,
            groupId = entity.groupId,
            memberId = entity.memberId,
            status = try { MemberStatus.valueOf(entity.status.uppercase()) } catch (e: Exception) { MemberStatus.ACTIVE },
            role = try { GroupRole.valueOf(entity.role.uppercase()) } catch (e: Exception) { GroupRole.MEMBER },
            joinedAt = entity.joinedAt
        )
    }
}
