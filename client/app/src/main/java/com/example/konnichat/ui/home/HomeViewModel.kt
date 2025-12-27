package com.example.konnichat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.model.ConversationItem
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(db: AppDatabase) : ViewModel() {

    private val repository = ChatRepository(db)

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations

    init {
        // 1. Tự động lắng nghe DB (để hiển thị)
        loadConversations()

        // 2. Gửi lệnh lên Server xin dữ liệu mới nhất (để cập nhật DB)
        // Offset 0, Limit 100 bạn bè
        fetchFriendsFromServer()
    }

    private fun fetchFriendsFromServer() {
        // Chạy trên Background Thread
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                NativeClient.getFriends(0, 100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            repository.getConversationList().collectLatest { list ->
                _conversations.value = list
            }
        }
    }
}