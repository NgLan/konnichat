package com.example.konnichat.ui.group

import androidx.lifecycle.*
import com.example.konnichat.data.local.model.GroupMemberWithUser
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupInfoViewModel(
    private val chatRepository: ChatRepository,
    private val groupId: Int,
    private val myUserId: Int
) : ViewModel() {

    private val _members = MutableLiveData<List<GroupMemberWithUser>>()
    val members: LiveData<List<GroupMemberWithUser>> = _members

    private val _isAdmin = MutableLiveData<Boolean>(false)
    val isAdmin: LiveData<Boolean> = _isAdmin

    init {
        // 1. Lắng nghe dữ liệu từ DB Local
        viewModelScope.launch {
            // Lưu ý: Cần thêm hàm getMembersWithUserInfo vào Repository ở bước trước nếu chưa có
            // Tạm thời gọi trực tiếp DAO thông qua Repository (hoặc bạn thêm hàm getMembersFlow vào Repo)
            // Giả sử ta thêm hàm getGroupMembersFlow vào ChatRepository
            chatRepository.getGroupMembersFlow(groupId).collectLatest { list ->
                _members.value = list
            }
        }

        // 2. Gọi Server lấy dữ liệu mới nhất
        refreshMembers()
        checkMyRole()
    }

    private fun checkMyRole() {
        viewModelScope.launch {
            val role = chatRepository.getGroupRole(groupId, myUserId)
            _isAdmin.value = (role == "admin")
        }
    }

    fun kickMember(targetId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatRepository.kickMember(groupId, targetId)
                // Không cần update UI thủ công ở đây vì onMemberRemoved (Socket) sẽ lo việc đó
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun refreshMembers() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                NativeClient.getGroupMembers(groupId, 0, 100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Thêm vào class GroupInfoViewModel
    fun dissolveGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatRepository.dissolveGroup(groupId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class GroupInfoViewModelFactory(
    private val chatRepo: ChatRepository,
    private val groupId: Int,
    private val myUserId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupInfoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GroupInfoViewModel(chatRepo, groupId, myUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}