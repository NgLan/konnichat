package com.example.konnichat.domain.model

data class Reaction(
    val id: Int,
    val userId: Int,
    val iconId: Int,
    val messageId: Int?,
    val groupMessageId: Int?,
    val createdAt: String
)
