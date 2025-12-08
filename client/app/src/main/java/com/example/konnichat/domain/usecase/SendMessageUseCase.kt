package com.example.konnichat.domain.usecase

import com.example.konnichat.domain.model.Message
import com.example.konnichat.domain.repository.ChatRepository

class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(message: Message) {
        repository.sendMessage(message)
    }
}
