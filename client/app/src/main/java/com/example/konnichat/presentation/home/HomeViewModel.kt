package com.example.konnichat.presentation.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.konnichat.domain.model.User
import com.example.konnichat.domain.repository.ChatRepository
import com.example.konnichat.domain.usecase.GetFriendsUseCase
import com.example.konnichat.domain.usecase.ReceiveMessageLoopUseCase
import com.example.konnichat.domain.usecase.SyncOfflineMessagesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getFriendsUseCase: GetFriendsUseCase,
    private val syncOfflineMessagesUseCase: SyncOfflineMessagesUseCase,
    private val receiveMessageLoopUseCase: ReceiveMessageLoopUseCase
) : ViewModel() {

    private val _friends = MutableLiveData<List<User>>()
    val friends: LiveData<List<User>> = _friends

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadFriends(myUserId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = getFriendsUseCase(myUserId)
                _friends.value = list

                syncOfflineMessagesUseCase(myUserId)

                launch(Dispatchers.IO) {
                    receiveMessageLoopUseCase(myUserId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Xử lý lỗi (post livedata error nếu cần)
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// Factory để khởi tạo ViewModel có tham số
class HomeViewModelFactory(
    private val getFriendsUseCase: GetFriendsUseCase,
    private val syncOfflineMessagesUseCase: SyncOfflineMessagesUseCase,
    private val receiveMessageLoopUseCase: ReceiveMessageLoopUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                getFriendsUseCase,
                syncOfflineMessagesUseCase,
                receiveMessageLoopUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
