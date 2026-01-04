package com.example.konnichat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // Trạng thái quan hệ bạn bè (True: Hiện chat, False: Hiện nút kết bạn)
    private val _isFriend = MutableLiveData<Boolean>(false)
    val isFriend: LiveData<Boolean> = _isFriend

    // Trạng thái gửi lời mời kết bạn (để update UI nút bấm)
    private val _friendReqStatus = MutableLiveData<Boolean>()
    val friendReqStatus: LiveData<Boolean> = _friendReqStatus

    // Load tin nhắn (Reactive Flow -> LiveData)
    fun getMessages(myId: Int, friendId: Int): LiveData<List<MessageEntity>> {
        // 1. Kiểm tra quan hệ bạn bè ngay khi vào màn hình
        checkFriendStatus(friendId)

        // 2. Load sẵn history mới nhất từ server (Offset 0, Limit 20)
        chatRepository.loadHistory(friendId, 0, 20)

        // 3. Trả về LiveData từ DB Local (tự động update khi DB thay đổi)
        return chatRepository.getMessages(myId, friendId).asLiveData()
    }

    // Gửi tin nhắn
    fun sendMessage(myId: Int, receiverId: Int, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            // Logic gửi tin (Lưu DB local -> Gửi Socket) nằm trong Repository
            chatRepository.sendMessage(myId, receiverId, content)
        }
    }

    // Load thêm lịch sử (Pagination khi vuốt lên)
    fun loadMoreHistory(targetId: Int, currentCount: Int) {
        viewModelScope.launch {
            chatRepository.loadHistory(targetId, currentCount, 20)
        }
    }

    // Kiểm tra xem targetId có trong bảng Friend của DB không
    private fun checkFriendStatus(targetId: Int) {
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