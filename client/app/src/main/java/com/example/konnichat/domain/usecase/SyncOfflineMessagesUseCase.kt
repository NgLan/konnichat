package com.example.konnichat.domain.usecase

import com.example.konnichat.domain.repository.ChatRepository

class SyncOfflineMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(userId: Int) {
        repository.syncOfflineMessages(userId)
    }
}
