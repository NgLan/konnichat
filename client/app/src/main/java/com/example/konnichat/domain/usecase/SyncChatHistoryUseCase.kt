package com.example.konnichat.domain.usecase

import com.example.konnichat.domain.repository.ChatRepository

class SyncChatHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(myUserId: Int, friendId: Int) {
        repository.syncChatHistory(myUserId, friendId)
    }
}
