package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.FriendRequestEntity
import com.example.konnichat.domain.enums.RequestStatus
import com.example.konnichat.domain.model.FriendRequest

class FriendRequestMapper {
    fun mapToDomain(entity: FriendRequestEntity): FriendRequest {
        return FriendRequest(
            id = entity.id,
            senderId = entity.senderId,
            receiverId = entity.receiverId,
            status = try { RequestStatus.valueOf(entity.status.uppercase()) } catch (e: Exception) { RequestStatus.WAITING },
            createdAt = entity.createdAt
        )
    }
}
