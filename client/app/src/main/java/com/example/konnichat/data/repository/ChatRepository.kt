package com.example.konnichat.data.repository

import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.dao.ConversationDao
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.local.model.ConversationItem
import com.example.konnichat.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import java.util.Date

// Repository chịu trách nhiệm cung cấp dữ liệu Chat
class ChatRepository(private val conversationDao: ConversationDao) {

    // Hàm lấy danh sách hội thoại (Realtime Local)
    // Trả về Flow: Khi Database thay đổi, UI tự cập nhật
    fun getConversationList(myUserId: Int): Flow<List<ConversationItem>> {
        return conversationDao.getConversationList(myUserId)
    }

    // TODO: Sau này sẽ thêm hàm sendMessage, getMessageHistory ở đây
}