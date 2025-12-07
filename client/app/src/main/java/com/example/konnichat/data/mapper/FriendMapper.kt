package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.FriendEntity
import com.example.konnichat.domain.enums.NotificationState
import com.example.konnichat.domain.model.Friend

class FriendMapper {
    fun mapToDomain(entity: FriendEntity): Friend {
        return Friend(
            id = entity.id,
            userId = entity.userId,
            friendId = entity.friendId,
            notification = try { NotificationState.valueOf(entity.notification.uppercase()) } catch (e: Exception) { NotificationState.ON },
            createdAt = entity.createdAt
        )
    }
}
