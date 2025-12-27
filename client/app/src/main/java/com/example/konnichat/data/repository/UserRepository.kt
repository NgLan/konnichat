// File: client/app/src/main/java/com/example/konnichat/data/repository/UserRepository.kt
package com.example.konnichat.data.repository

import com.example.konnichat.data.local.dao.UserDao
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import java.util.Date

class UserRepository(private val userDao: UserDao) {

    // 1. Hàm mới: Lấy danh sách bạn bè để hiển thị UI
    fun getFriendList(): Flow<List<UserEntity>> {
        // Tạm thời truyền ID = 0 (hoặc lấy từ SharePref) để tránh hiện chính mình
        return userDao.getAllFriends(0)
    }

    // 2. Hàm cũ: Lưu user từ server về
    suspend fun saveFriendsFromNetwork(userDtos: Array<UserDto>) {
        val userEntities = userDtos.map { dto ->
            UserEntity(
                serverId = dto.id,
                email = dto.email,
                name = dto.name,
                isOnline = dto.isOnline,
                age = null,
                status = "active",
                avatarUrl = null,
                createdAt = Date(),
                updatedAt = Date()
            )
        }
        userDao.insertUsers(userEntities)
    }
}