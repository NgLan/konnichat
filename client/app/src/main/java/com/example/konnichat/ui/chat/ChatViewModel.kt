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
import kotlinx.coroutines.flow.collectLatest

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

        if (chatType == "group") {
            // 1. Nếu là Group: Lắng nghe realtime từ bảng group_members
            viewModelScope.launch {
                chatRepository.checkGroupMembership(groupId = friendId, userId = myId)
                    .collectLatest { isMember ->
                        // Nếu còn là thành viên -> isMember = true -> Hiện chat
                        // Nếu bị kick (xóa khỏi DB) -> isMember = false -> Ẩn chat
                        _isFriend.postValue(isMember)
                    }
            }

            // 2. Fetch thành viên mới nhất
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                chatRepository.fetchGroupMembers(friendId)
            }
        } else {
            // 3. Nếu là Private: Logic cũ
            checkFriendStatus(friendId)
            checkMuteStatus(friendId)
        }

        // 2. Kiểm tra quan hệ bạn bè (để ẩn/hiện nút Kết bạn)
//        checkFriendStatus(friendId)
//        checkMuteStatus(friendId)

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
    fun checkMuteStatus(targetId: Int) {
        val muted = if (currentChatType == "group") {
            chatRepository.isGroupMuted(targetId)
        } else {
            userRepository.isUserMuted(targetId)
        }
        _isMuted.value = muted
    }

    fun toggleMute(targetId: Int) {
        val currentStatus = _isMuted.value ?: false
        val newStatus = !currentStatus

        if (currentChatType == "group") {
            chatRepository.setGroupMute(targetId, newStatus)
        } else {
            userRepository.setUserMute(targetId, newStatus)
        }

        _isMuted.value = newStatus
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
//            _isFriend.postValue(true)
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

    fun recallMessage(message: MessageWithSender) {
        val serverId = message.message.serverId
        // Chỉ thu hồi được tin nhắn đã gửi lên server (serverId > 0)
        if (serverId <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatRepository.recallMessage(serverId)
            } catch (e: Exception) {
                e.printStackTrace()
                // Có thể postValue lỗi ra LiveData để UI hiện Toast nếu cần
            }
        }
    }

    fun reactToMessage(message: MessageWithSender, reactionCode: Int) {
        val serverId = message.message.serverId
        if (serverId <= 0) return // Chỉ react được tin đã lên server

        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatRepository.reactToMessage(serverId, reactionCode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}