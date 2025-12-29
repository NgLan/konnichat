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
            NativeClient.respondFriendRequest(requestId, accept)
            // Sau khi phản hồi, load lại danh sách để cập nhật UI
            loadRequests()
        }
    }
}