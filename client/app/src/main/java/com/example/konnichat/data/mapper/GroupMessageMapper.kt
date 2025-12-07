package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.GroupMessageEntity
import com.example.konnichat.domain.enums.MessageStatus
import com.example.konnichat.domain.model.GroupMessage

class GroupMessageMapper {
    fun mapToDomain(entity: GroupMessageEntity): GroupMessage {
        return GroupMessage(
            id = entity.id,
            groupId = entity.groupId,
            senderId = entity.senderId,
            content = entity.content,
            status = try { MessageStatus.valueOf(entity.status.uppercase()) } catch (e: Exception) { MessageStatus.SENT },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
