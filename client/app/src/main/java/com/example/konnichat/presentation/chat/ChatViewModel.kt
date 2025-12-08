package com.example.konnichat.presentation.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.konnichat.di.ChatUseCases
import com.example.konnichat.domain.enums.MessageStatus
import com.example.konnichat.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    private val chatUseCases: ChatUseCases,
    private val myUserId: Int,
    private val friendId: Int
) : ViewModel() {

    // 1. UI chỉ việc Observe LiveData này. Dữ liệu tự động chảy từ DB -> UI.
    val messages: LiveData<List<Message>> = chatUseCases.getMessages(myUserId, friendId).asLiveData()

    init {
        // 1. Loop nhận tin mới
//        viewModelScope.launch(Dispatchers.IO) {
//            chatUseCases.receiveMessageLoop()
//        }

        // 2. Sync tin cũ (Lịch sử)
        viewModelScope.launch(Dispatchers.IO) {
            chatUseCases.syncHistory(myUserId, friendId)
        }
    }

    fun sendMessage(content: String) {
        val trimmedContent = content.trim() // Cắt khoảng trắng thừa
        if (trimmedContent.isBlank()) return

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val message = Message(
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), // ID tạm
            senderId = myUserId,
            receiverId = friendId,
            content = trimmedContent,
            status = MessageStatus.SENT,
            createdAt = now,
            updatedAt = now
        )

        viewModelScope.launch {
            chatUseCases.sendMessage(message)
        }
    }
}

@Suppress("UNCHECKED_CAST")
class ChatViewModelFactory(
    private val useCases: ChatUseCases,
    private val myUserId: Int,
    private val friendId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(useCases, myUserId, friendId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
