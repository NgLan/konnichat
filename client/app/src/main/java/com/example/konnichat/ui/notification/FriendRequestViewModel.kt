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

    fun respond(requestId: Int, senderId: Int, senderName: String, accept: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (accept) {
                    // 1. Nếu ĐỒNG Ý: Gọi hàm repository (vừa tạo ở bước trước)
                    // Hàm này sẽ: Gọi Server + Update User đó thành "Bạn bè" (relation_type=1) + Xóa request
                    userRepository.acceptFriendRequest(requestId, senderId, senderName)
                } else {
                    // 2. Nếu TỪ CHỐI:
                    // Xóa UI
                    userRepository.removePendingRequest(requestId)
                    // Gọi Server báo từ chối
                    NativeClient.respondFriendRequest(requestId, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}