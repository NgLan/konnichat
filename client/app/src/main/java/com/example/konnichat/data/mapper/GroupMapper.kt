package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.GroupEntity
import com.example.konnichat.domain.enums.NotificationState
import com.example.konnichat.domain.model.Group

class GroupMapper {
    fun mapToDomain(entity: GroupEntity): Group {
        return Group(
            id = entity.id,
            name = entity.name,
            avatarUrl = entity.avatarUrl,
            notification = try {
                NotificationState.valueOf(entity.notification.uppercase())
            } catch (e: Exception) {
                NotificationState.ON
            },
            createdAt = entity.createdAt
        )
    }
}
