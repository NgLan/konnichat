package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.MessageEntity
import com.example.konnichat.domain.enums.MessageStatus
import com.example.konnichat.domain.model.Message

class MessageMapper {
    fun mapToDomain(entity: MessageEntity): Message {
        return Message(
            id = entity.id,
            senderId = entity.senderId,
            receiverId = entity.receiverId,
            content = entity.content,
            status = try {
                MessageStatus.valueOf(entity.status.uppercase())
            } catch (e: Exception) {
                MessageStatus.SENT
            },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun mapToEntity(domain: Message): MessageEntity {
        return MessageEntity(
            id = domain.id,
            senderId = domain.senderId,
            receiverId = domain.receiverId,
            content = domain.content,
            status = domain.status.name.lowercase(),
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
