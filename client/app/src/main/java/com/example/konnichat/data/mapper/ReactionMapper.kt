package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.ReactionEntity
import com.example.konnichat.domain.model.Reaction

class ReactionMapper {
    fun mapToDomain(entity: ReactionEntity): Reaction {
        return Reaction(
            id = entity.id,
            userId = entity.userId,
            iconId = entity.iconId,
            messageId = entity.messageId,
            groupMessageId = entity.groupMessageId,
            createdAt = entity.createdAt
        )
    }
}
