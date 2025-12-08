package com.example.konnichat.data.dto

// type 1 = Message, type 2 = Status
data class SocketEventDto(val type: Int, val data: Any)
