package com.example.konnichat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.ui.search.UserSearchUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _friends = MutableStateFlow<List<UserEntity>>(emptyList())
    val friends: StateFlow<List<UserEntity>> = _friends

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            userRepository.resetLocalStatuses()
            try {
                NativeClient.getFriends(0, 100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            userRepository.getFriendList().collectLatest { list ->
                _friends.value = list
            }
        }
    }

    // --- HÀM MỚI: Hủy kết bạn ---
    fun unfriendUser(userId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Gửi lệnh lên Server
            NativeClient.unfriendUser(userId)

            // 2. Xử lý local ngay lập tức (Optimistic Update)
            // Xóa khỏi danh sách bạn bè
            userRepository.deleteFriend(userId)

            // Reset trạng thái bên trang tìm kiếm về "Kết bạn" (STATUS_NONE)
            userRepository.updateSearchStatusToNone(userId)
        }
    }
}