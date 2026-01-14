package com.example.konnichat.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.local.model.ConversationItem
import com.example.konnichat.data.local.prefs.SessionManager
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.ui.search.UserSearchUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository, // [THÊM] Inject ChatRepository
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations

    private val _friends = MutableStateFlow<List<UserEntity>>(emptyList())
    val friends: StateFlow<List<UserEntity>> = _friends

    init {
        // 1. Gọi Native lấy danh sách bạn bè mới nhất (để cập nhật Avatar/Name nếu có)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            chatRepository.runDataFixer()

            userRepository.resetLocalStatuses()
            try {
                NativeClient.getFriends(0, 100)
                NativeClient.getGroupList(0, 100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Bắt đầu lắng nghe thay đổi từ DB
        loadConversations()
    }

    private fun loadConversations() {
        val myId = sessionManager.getUserId()
        if (myId == -1) return

        viewModelScope.launch {
            // Gọi hàm mới trong ChatRepository (đã gọi xuống ConversationDao)
            chatRepository.getConversationList(myId).collectLatest { list ->
                _conversations.value = list
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            userRepository.getFriendList().collectLatest { list ->
                _friends.value = list
            }
        }
    }

    // --- HÀM MỚI: Hủy kết bạn ---
    fun unfriendUser(userId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Gửi lệnh lên Server
            NativeClient.unfriendUser(userId)

            // 2. Xử lý local ngay lập tức (Optimistic Update)
            // Xóa khỏi danh sách bạn bè
            userRepository.deleteFriend(userId)

            // Reset trạng thái bên trang tìm kiếm về "Kết bạn" (STATUS_NONE)
            userRepository.updateSearchStatusToNone(userId)
        }
    }

    suspend fun getGroupRole(groupId: Int): String? {
        val myId = sessionManager.getUserId()
        // Gọi xuống Repository -> gọi xuống DAO
        return chatRepository.getGroupRole(groupId, myId)
    }

    fun leaveGroup(groupId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                chatRepository.leaveGroup(groupId)
            } catch (e: Exception) {
                e.printStackTrace()
                // Có thể post error message ra LiveData nếu cần
            }
        }
    }

    fun dissolveGroup(groupId: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                chatRepository.dissolveGroup(groupId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}