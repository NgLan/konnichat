package com.example.konnichat.domain.usecase

import com.example.konnichat.domain.repository.ChatRepository

class ReceiveMessageLoopUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() {
        repository.startReceivingMessageLoop()
    }
}
