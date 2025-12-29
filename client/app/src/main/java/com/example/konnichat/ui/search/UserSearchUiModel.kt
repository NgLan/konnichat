package com.example.konnichat.ui.search

data class UserSearchUiModel(
    val id: Int,
    val name: String,
    val email: String,
    val status: Int // Biến quan trọng để quyết định hiện nút "Kết bạn" hay "Nhắn tin"
){
    companion object {
        const val STATUS_NONE = 0
        const val STATUS_FRIEND = 1
        const val STATUS_SENT = 2     // Mình gửi họ
        const val STATUS_RECEIVED = 3 // Họ gửi mình
    }
}