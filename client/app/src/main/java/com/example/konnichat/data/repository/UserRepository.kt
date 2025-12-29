// File: client/app/src/main/java/com/example/konnichat/data/repository/UserRepository.kt
package com.example.konnichat.data.repository

import android.content.SharedPreferences
import com.example.konnichat.data.local.dao.UserDao
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
import com.example.konnichat.ui.search.UserSearchUiModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import java.util.Date

class UserRepository(
    private val userDao: UserDao,
    private val prefs: SharedPreferences
) {

    // Helper: Lấy ID user hiện tại
    private fun getCurrentUserId(): Int {
        return prefs.getInt("USER_ID", -1)
    }

    // 1. Hàm hiển thị danh sách bạn bè
    fun getFriendList(): Flow<List<UserEntity>> {
        val myId = getCurrentUserId()
        return userDao.getAllFriends(myId)
    }

    // 2. Hàm lưu bạn bè từ Server
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

    // --- MỚI THÊM: Cập nhật trạng thái ---
    suspend fun updateFriendStatus(friendId: Int, isOnline: Boolean) {
        userDao.updateFriendStatus(friendId, isOnline)
    }

    // Thêm vào class UserRepository
    suspend fun resetLocalStatuses() {
        userDao.resetAllStatusOffline()
    }

    private val _searchResults = MutableSharedFlow<List<UserSearchUiModel>>(replay = 1)
    val searchResults = _searchResults.asSharedFlow()

    // Hàm này được gọi từ NativeEventListenerImpl khi có kết quả từ Server
    suspend fun processSearchResults(dtos: Array<UserSearchDto>) {
        val uiModels = dtos.map { dto ->
            // Logic Clean Code: Đối chiếu với Database Local
            val isFriend = userDao.isFriend(dto.userId)
            UserSearchUiModel(
                id = dto.userId,
                name = dto.name,
                email = dto.email,
                isFriend = isFriend
            )
        }
        // Phát tín hiệu cho ViewModel
        _searchResults.emit(uiModels)
    }
}