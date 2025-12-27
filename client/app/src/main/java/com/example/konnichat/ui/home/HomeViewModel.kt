// File: client/app/src/main/java/com/example/konnichat/ui/home/HomeViewModel.kt
package com.example.konnichat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Inject UserRepository vào đây
class HomeViewModel(private val userRepository: UserRepository) : ViewModel() {

    // Data trả về là List<UserEntity>
    private val _friends = MutableStateFlow<List<UserEntity>>(emptyList())
    val friends: StateFlow<List<UserEntity>> = _friends

    init {
        loadFriends()
        fetchFriendsFromServer()
    }

    private fun fetchFriendsFromServer() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                NativeClient.getFriends(0, 100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            // Lắng nghe bảng User
            userRepository.getFriendList().collectLatest { list ->
                _friends.value = list
            }
        }
    }
}