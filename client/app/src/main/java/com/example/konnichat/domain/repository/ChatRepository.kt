package com.example.konnichat.domain.repository

import com.example.konnichat.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessages(myUserId: Int, friendId: Int): Flow<List<Message>>
    suspend fun sendMessage(message: Message)
    suspend fun startReceivingMessageLoop(myUserId: Int)
    suspend fun syncOfflineMessages(myUserId: Int)
    suspend fun syncChatHistory(myUserId: Int, friendId: Int)
}
