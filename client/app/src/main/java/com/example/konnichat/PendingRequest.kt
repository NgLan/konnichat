package com.example.konnichat

// Class này hứng dữ liệu từ struct PendingReqInfo trong C
data class PendingRequest(
    val requestId: Int,
    val senderId: Int,
    val senderName: String
)