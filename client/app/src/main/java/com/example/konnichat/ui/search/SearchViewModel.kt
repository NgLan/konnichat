package com.example.konnichat.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val userRepository: UserRepository) : ViewModel() {

    // Expose luồng dữ liệu từ Repo ra UI
    val searchResults: SharedFlow<List<UserSearchUiModel>> = userRepository.searchResults

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Gọi Native C để gửi request lên Server
            // Offset = 0, Limit = 20
            NativeClient.searchUsers(keyword, 0, 20)
        }
    }

    fun sendFriendRequest(userId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            NativeClient.sendFriendRequest(userId)
        }
    }
}