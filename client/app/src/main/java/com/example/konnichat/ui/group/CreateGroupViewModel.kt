package com.example.konnichat.ui.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.launch

class CreateGroupViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // Lấy danh sách bạn bè (Realtime từ DB)
    val friends: LiveData<List<UserEntity>> = userRepository.getFriendList().asLiveData()

    // Hàm tạo nhóm
    fun createGroup(name: String, memberIds: List<Int>) {
        viewModelScope.launch {
            // Chuyển List<Int> thành IntArray để gửi xuống Native
            chatRepository.createGroup(name, memberIds.toIntArray())
        }
    }
}