package com.example.konnichat.domain.usecase

import com.example.konnichat.domain.model.Message
import com.example.konnichat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetMessagesUseCase(private val repository: ChatRepository) {
    operator fun invoke(myUserId: Int, friendId: Int): Flow<List<Message>> {
        return repository.getMessages(myUserId, friendId)
    }
}
