package com.example.konnichat.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val userRepository: UserRepository) : ViewModel() {

    // Expose luồng dữ liệu từ Repo ra UI
    val searchResults: StateFlow<List<UserSearchUiModel>> = userRepository.searchResults

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            NativeClient.searchUsers(keyword, 0, 20)
        }
    }

    fun sendFriendRequest(userId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Cập nhật UI ngay lập tức (Optimistic Update)
            // Chuyển trạng thái sang SENT (2) -> Nút sẽ đổi màu xám "Đã gửi" ngay
            userRepository.updateUserStatusLocal(userId, UserSearchUiModel.STATUS_SENT)

            // 2. Sau đó mới gửi Request lên Server
            NativeClient.sendFriendRequest(userId)
        }
    }
}