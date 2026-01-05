package com.example.konnichat.ui.group

import androidx.lifecycle.*
import com.example.konnichat.data.local.model.GroupMemberWithUser
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupInfoViewModel(
    private val chatRepository: ChatRepository,
    private val groupId: Int
) : ViewModel() {

    private val _members = MutableLiveData<List<GroupMemberWithUser>>()
    val members: LiveData<List<GroupMemberWithUser>> = _members

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
}

class GroupInfoViewModelFactory(
    private val chatRepo: ChatRepository,
    private val groupId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupInfoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GroupInfoViewModel(chatRepo, groupId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}