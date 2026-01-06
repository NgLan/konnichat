package com.example.konnichat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.model.MessageWithSender
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    var currentChatType: String = "private"
    private var isLoadingHistory = false
    // Trạng thái quan hệ bạn bè (True: Hiện chat, False: Hiện nút kết bạn)
    private val _isFriend = MutableLiveData<Boolean>(false)
    val isFriend: LiveData<Boolean> = _isFriend

    // Trạng thái gửi lời mời kết bạn (để update UI nút bấm)
    private val _friendReqStatus = MutableLiveData<Boolean>()
    val friendReqStatus: LiveData<Boolean> = _friendReqStatus

    private val _isMuted = MutableLiveData<Boolean>(false)
    val isMuted: LiveData<Boolean> = _isMuted
    // Load tin nhắn (Reactive Flow -> LiveData)
    fun getMessages(myId: Int, friendId: Int, chatType: String): LiveData<List<MessageWithSender>> {
        // 1. [QUAN TRỌNG] Gán loại chat ngay lập tức
        this.currentChatType = chatType

        // 2. Kiểm tra quan hệ bạn bè (để ẩn/hiện nút Kết bạn)
        checkFriendStatus(friendId)
        checkMuteStatus(friendId)

        // 3. Load lịch sử từ server
        // Xác định cờ isGroup dựa trên chatType
        val isGroup = (chatType == "group")

        // 4. Nếu là Group, luôn set là "bạn bè" để hiện khung chat
        if (isGroup) {
            _isFriend.postValue(true)
            viewModelScope.launch(Dispatchers.IO) {
                chatRepository.fetchGroupMembers(friendId)
            }
        }
        chatRepository.loadHistory(friendId, isGroup, 0, 20)


        // 5. Trả về LiveData từ DB Local
        return chatRepository.getMessages(myId, friendId, chatType).asLiveData()
    }
    private fun checkMuteStatus(targetId: Int) {
        // Vì SharedPreferences đọc nhanh nên có thể không cần coroutine,
        // nhưng để an toàn cứ dùng postValue
        val muted = userRepository.isUserMuted(targetId)
        _isMuted.postValue(muted)
    }

    fun toggleMute(targetId: Int) {
        val current = _isMuted.value ?: false
        val newState = !current

        // Lưu vào Prefs
        userRepository.setUserMute(targetId, newState)

        // Update UI
        _isMuted.value = newState
    }

    // Gửi tin nhắn
    fun sendMessage(myId: Int, receiverId: Int, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            // Logic gửi tin (Lưu DB local -> Gửi Socket) nằm trong Repository
            chatRepository.sendMessage(myId, receiverId, content, currentChatType)
        }
    }

    // Load thêm lịch sử (Pagination khi vuốt lên)
    fun loadMoreHistory(targetId: Int, currentCount: Int) {
        if (isLoadingHistory) return

        isLoadingHistory = true

        viewModelScope.launch {
            // Truyền đúng cờ isGroup
            val isGroup = (currentChatType == "group")
            chatRepository.loadHistory(targetId, isGroup, currentCount, 20)

            delay(2000)
            isLoadingHistory = false
        }
    }
    // Kiểm tra xem targetId có trong bảng Friend của DB không
    private fun checkFriendStatus(targetId: Int) {
        if (currentChatType == "group") {
            _isFriend.postValue(true)
            return
        }

        viewModelScope.launch {
            val friend = userRepository.getFriendById(targetId)
            _isFriend.postValue(friend != null)
        }
    }

    // Gửi lời mời kết bạn (dành cho người lạ)
    fun sendFriendRequest(targetId: Int) {
        viewModelScope.launch {
            try {
                userRepository.sendFriendRequest(targetId)
                _friendReqStatus.postValue(true)
            } catch (e: Exception) {
                _friendReqStatus.postValue(false)
            }
        }
    }
}