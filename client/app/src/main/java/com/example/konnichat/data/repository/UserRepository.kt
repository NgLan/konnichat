// File: client/app/src/main/java/com/example/konnichat/data/repository/UserRepository.kt
package com.example.konnichat.data.repository

import android.content.SharedPreferences
import com.example.konnichat.data.local.dao.UserDao
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
import com.example.konnichat.data.remote.dto.PendingRequestDto
import com.example.konnichat.ui.search.UserSearchUiModel
import com.example.konnichat.data.local.dao.MessageDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow // Import StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import java.util.Date

//private val UserRepository.messageDao: Any

class UserRepository(
    private val userDao: UserDao,
    private val messageDao: MessageDao,
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
                relationType = 1,
                isFullData = true,
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

    private val _searchResults = MutableStateFlow<List<UserSearchUiModel>>(emptyList())
    val searchResults: StateFlow<List<UserSearchUiModel>> = _searchResults.asStateFlow()

    // Hàm nhận dữ liệu từ Server (Native)
    suspend fun processSearchResults(dtos: Array<UserSearchDto>) {
        val uiModels = dtos.map { dto ->
            val entity = UserEntity(
                serverId = dto.userId,
                name = dto.name,
                email = dto.email,
                isOnline = false, // Server Search mặc định trả về thông tin cơ bản
                status = "active",
                age = null,
                avatarUrl = null,
                relationType = dto.status, // Giữ nguyên trạng thái quan hệ từ Server
                isFullData = true // Xác thực dữ liệu
            )
            userDao.upsertVerifiedUser(entity)

            UserSearchUiModel(
                id = dto.userId,
                name = dto.name,
                email = dto.email,
                status = dto.status
            )
        }
        _searchResults.value = uiModels // Cập nhật StateFlow
    }

    // 2. Hàm mới: Cập nhật trạng thái cục bộ (Optimistic Update)
    fun updateUserStatusLocal(userId: Int, newStatus: Int) {
        val currentList = _searchResults.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == userId }

        if (index != -1) {
            // Tạo bản sao của item với status mới
            val updatedUser = currentList[index].copy(status = newStatus)
            currentList[index] = updatedUser

            // Emit list mới để UI tự cập nhật
            _searchResults.value = currentList
        }
    }

    private val _pendingRequests = MutableSharedFlow<List<PendingRequestDto>>(replay = 1)
    val pendingRequests = _pendingRequests.asSharedFlow()

    suspend fun processPendingRequests(requests: Array<PendingRequestDto>) {
        _pendingRequests.emit(requests.toList())
    }

    suspend fun addSingleRequest(request: PendingRequestDto) {
        // Lấy danh sách hiện tại (nếu null thì tạo list rỗng)
        val currentList = _pendingRequests.replayCache.firstOrNull() ?: emptyList()

        // Kiểm tra trùng lặp (nếu đã có reqId này rồi thì thôi)
        if (currentList.none { it.requestId == request.requestId }) {
            val newList = currentList + request
            _pendingRequests.emit(newList)
        }
    }

    suspend fun removePendingRequest(requestId: Int) {
        val currentList = _pendingRequests.replayCache.firstOrNull() ?: emptyList()
        // Tạo list mới loại bỏ item có requestId tương ứng
        val newList = currentList.filter { it.requestId != requestId }
        // Emit list mới để UI cập nhật ngay
        _pendingRequests.emit(newList)
    }

    suspend fun saveSingleFriend(userDto: UserDto) {
        val userEntity = UserEntity(
            serverId = userDto.id,
            email = userDto.email,
            name = userDto.name,
            isOnline = userDto.isOnline,
            age = null,
            status = "active",
            avatarUrl = null,
            relationType = 1,
            isFullData = true,
            createdAt = Date(),
            updatedAt = Date()
        )
        // Insert vào Room -> Flow sẽ tự báo cho UI vẽ lại
        userDao.insertUsers(listOf(userEntity))
    }

    fun updateSearchStatusToFriend(userId: Int) {
        // Lấy list hiện tại đang hiển thị trên màn hình Search
        val currentList = _searchResults.value.toMutableList()

        // Tìm xem user vừa đồng ý có nằm trong danh sách tìm kiếm không
        val index = currentList.indexOfFirst { it.id == userId }

        if (index != -1) {
            // Nếu tìm thấy, copy object cũ và đổi status thành STATUS_FRIEND
            val updatedItem = currentList[index].copy(status = UserSearchUiModel.STATUS_FRIEND)
            currentList[index] = updatedItem

            // Emit list mới -> SearchFragment sẽ tự vẽ lại nút thành "Bạn bè"
            _searchResults.value = currentList
        }
    }

    suspend fun unfriendUser(friendId: Int) {
        // 1. Gọi Server
        NativeClient.unfriendUser(friendId)

        // 2. [QUAN TRỌNG] Xóa tin nhắn riêng tư trước (để làm sạch lịch sử chat)
        messageDao.deletePrivateChat(friendId)

        // 3. Update trạng thái User về "Người lạ" (Thay vì Delete gây crash)
        userDao.unfriendLocalUser(friendId)

        // 4. Update UI Search
        updateSearchStatusToNone(friendId)
    }

    // Hàm xử lý khi bị người khác Unfriend (từ Socket)
    suspend fun deleteFriend(friendId: Int) {
        // Xóa tin nhắn
        messageDao.deletePrivateChat(friendId)
        // Chuyển về người lạ
        userDao.unfriendLocalUser(friendId)
        updateSearchStatusToNone(friendId)
    }

    // 1. Xóa bạn khỏi Database (khi bị Unfriend)
//    suspend fun deleteFriend(friendId: Int) {
//        userDao.deleteUserByServerId(friendId)
//    }

    // 2. Reset trạng thái tìm kiếm về STATUS_NONE (khi bị Từ chối hoặc Unfriend)
    fun updateSearchStatusToNone(userId: Int) {
        val currentList = _searchResults.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == userId }

        if (index != -1) {
            // Đổi status về 0 (None) -> Hiện nút "Kết bạn"
            val updatedItem = currentList[index].copy(status = UserSearchUiModel.STATUS_NONE)
            currentList[index] = updatedItem
            _searchResults.value = currentList
        }
    }

    // 1. Lấy thông tin User theo ID (để check xem có phải bạn bè không)
    suspend fun getFriendById(targetId: Int): UserEntity? {
        return userDao.getUserById(targetId)
    }

    // 2. Gửi lời mời kết bạn (Wrapper gọi xuống Native)
    suspend fun sendFriendRequest(targetId: Int) {
        // Gọi Native Client, hàm này sẽ gửi packet xuống server
        // Kết quả sẽ trả về qua Callback onFriendRequestSent hoặc update UI optimistic
        NativeClient.sendFriendRequest(targetId)
    }

    fun isUserMuted(userId: Int): Boolean {
        // Key format: MUTE_NOTIFY_12
        return prefs.getBoolean("MUTE_NOTIFY_$userId", false)
    }

    // [THÊM MỚI] Set trạng thái mute
    fun setUserMute(userId: Int, isMuted: Boolean) {
        prefs.edit().putBoolean("MUTE_NOTIFY_$userId", isMuted).apply()
    }

    suspend fun acceptFriendRequest(requestId: Int, senderId: Int) {
        // 1. Gọi Server báo là đã đồng ý
        NativeClient.respondFriendRequest(requestId, true)

        // 2. [QUAN TRỌNG] Cập nhật DB Local ngay lập tức
        // Chuyển người gửi (A) từ trạng thái '0' (Stranger) sang '1' (Friend)
        // Việc này sẽ làm User A xuất hiện ngay lập tức trong danh sách bạn bè của B
        userDao.makeFriendLocalUser(senderId)

        // 3. Xóa lời mời khỏi danh sách chờ (để update UI tab Notification)
        removePendingRequest(requestId)
    }
}