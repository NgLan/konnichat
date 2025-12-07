package com.example.konnichat.presentation.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.konnichat.di.ChatUseCases
import com.example.konnichat.domain.enums.MessageStatus
import com.example.konnichat.domain.model.Message
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    private val chatUseCases: ChatUseCases,
    private val myUserId: Int,
    private val friendId: Int
) : ViewModel() {

    // Chuyển đổi Flow từ Domain thành LiveData cho UI observe
    val messages: LiveData<List<Message>> = chatUseCases.getMessages(myUserId, friendId).asLiveData()

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Tạo Message Domain Model
        val message = Message(
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), // ID tạm
            senderId = myUserId,
            receiverId = friendId,
            content = content,
            status = MessageStatus.SENT,
            createdAt = now,
            updatedAt = now
        )

        viewModelScope.launch {
            chatUseCases.sendMessage(message)
        }
    }
}

class ChatViewModelFactory(
    private val useCases: ChatUseCases,
    private val myUserId: Int,
    private val friendId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(useCases, myUserId, friendId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
