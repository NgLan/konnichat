package com.example.konnichat.presentation.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.konnichat.domain.model.User
import com.example.konnichat.domain.usecase.GetFriendsUseCase
import kotlinx.coroutines.launch

class HomeViewModel(private val getFriendsUseCase: GetFriendsUseCase) : ViewModel() {

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
class HomeViewModelFactory(private val getFriendsUseCase: GetFriendsUseCase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(getFriendsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
