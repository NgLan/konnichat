package com.example.konnichat.domain.model

import com.example.konnichat.domain.enums.RequestStatus

data class FriendRequest(
    val id: Int,
    val senderId: Int,
    val receiverId: Int,
    val status: RequestStatus,
    val createdAt: String
)
