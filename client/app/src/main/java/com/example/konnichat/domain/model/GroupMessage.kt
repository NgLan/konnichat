package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.MessageStatus

data class GroupMessage(
    val id: Int,
    val groupId: Int,
    val senderId: Int,
    val content: String?,
    val status: MessageStatus,
    val createdAt: String,
    val updatedAt: String
)
