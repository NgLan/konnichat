package com.example.konnichat.ui.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.launch

class AddMemberViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // Lấy danh sách bạn bè để hiển thị
    val friends: LiveData<List<UserEntity>> = userRepository.getFriendList().asLiveData()

    fun addMembers(groupId: Int, memberIds: List<Int>) {
        viewModelScope.launch {
            // Gọi Repository thêm người
            chatRepository.addMembersToGroup(groupId, memberIds.toIntArray())
        }
    }
}