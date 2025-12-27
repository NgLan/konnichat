package com.example.konnichat.data.repository

import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.model.ConversationItem
import kotlinx.coroutines.flow.Flow

// Repository chịu trách nhiệm cung cấp dữ liệu Chat
class ChatRepository(private val db: AppDatabase) {

    // Hàm lấy danh sách hội thoại (Realtime Local)
    // Trả về Flow: Khi Database thay đổi, UI tự cập nhật
    fun getConversationList(): Flow<List<ConversationItem>> {
        return db.conversationDao().getConversationList()
    }

    // TODO: Sau này sẽ thêm hàm sendMessage, getMessageHistory ở đây
}