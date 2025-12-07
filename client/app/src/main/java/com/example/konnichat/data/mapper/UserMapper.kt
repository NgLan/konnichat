package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.UserEntity
import com.example.konnichat.domain.enums.OnlineStatus
import com.example.konnichat.domain.enums.UserStatus
import com.example.konnichat.domain.model.User

class UserMapper {
    fun mapToDomain(entity: UserEntity): User {
        return User(
            id = entity.id,
            email = entity.email,
            name = entity.name,
            age = entity.age,
            status = try {
                UserStatus.valueOf(entity.status.uppercase())
            } catch (e: Exception) {
                UserStatus.ACTIVE
            },
            isOnline = try {
                OnlineStatus.valueOf(
                    entity.isOnline.uppercase())
            } catch (e: Exception) {
                OnlineStatus.OFFLINE
            },
            avatarUrl = entity.avatarUrl,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
