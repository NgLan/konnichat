package com.example.konnichat.ui.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AddMemberViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // [SỬA] Dùng MutableLiveData để update kết quả sau khi lọc
    private val _availableFriends = MutableLiveData<List<UserEntity>>()
    val friends: LiveData<List<UserEntity>> = _availableFriends

    // [THÊM MỚI] Hàm load dữ liệu và thực hiện lọc
    fun loadData(groupId: Int) {
        viewModelScope.launch {
            // 1. Lấy danh sách bạn bè (Flow Realtime)
            userRepository.getFriendList().collectLatest { allFriends ->

                // 2. Lấy danh sách thành viên hiện tại của nhóm
                val currentMemberIds = chatRepository.getGroupMemberIds(groupId)

                // 3. Lọc: Chỉ giữ lại những người KHÔNG có trong nhóm
                val filteredList = allFriends.filter { user ->
                    user.serverId !in currentMemberIds
                }

                // 4. Update UI
                _availableFriends.value = filteredList
            }
        }
    }

    fun addMembers(groupId: Int, memberIds: List<Int>) {
        viewModelScope.launch {
            chatRepository.addMembersToGroup(groupId, memberIds.toIntArray())
        }
    }
}