package com.example.konnichat.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.PendingRequestDto
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class FriendRequestViewModel(private val userRepository: UserRepository) : ViewModel() {

    val requests: SharedFlow<List<PendingRequestDto>> = userRepository.pendingRequests

    fun loadRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            NativeClient.getPendingRequests()
        }
    }

    fun respond(requestId: Int, accept: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. OPTIMISTIC UPDATE: Xóa ngay khỏi UI để người dùng thấy phản hồi tức thì
            userRepository.removePendingRequest(requestId)

            // 2. Gửi lệnh lên Server (chạy ngầm)
            NativeClient.respondFriendRequest(requestId, accept)

            // Không cần gọi loadRequests() nữa vì ta đã xóa tay ở bước 1 rồi.
            // Nếu muốn chắc chắn đồng bộ, có thể gọi lại sau 1 khoảng delay, nhưng không cần thiết.
        }
    }
}