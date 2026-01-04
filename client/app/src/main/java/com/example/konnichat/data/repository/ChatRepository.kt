package com.example.konnichat.data.repository

import android.util.Log
import com.example.konnichat.data.local.dao.ConversationDao
import com.example.konnichat.data.local.dao.MessageDao // [NEW] Thêm DAO
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.model.ConversationItem
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.MessageDto
import kotlinx.coroutines.flow.Flow
import java.util.Date
import androidx.room.Transaction

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao // [MODIFIED] Inject MessageDao
) {

    // Hàm lấy danh sách hội thoại (Realtime Local)
    fun getConversationList(myUserId: Int): Flow<List<ConversationItem>> {
        return conversationDao.getConversationList(myUserId)
    }

    // [NEW] Lấy tin nhắn Realtime từ DB
    fun getMessages(myUserId: Int, friendId: Int): Flow<List<MessageEntity>> {
        return messageDao.getMessagesBetween(myUserId, friendId)
    }

    // [NEW] Gửi tin nhắn
    suspend fun sendMessage(myUserId: Int, receiverId: Int, content: String) {
        // Tạo tempId dương để gửi qua Socket (C chỉ nhận int)
        val tempId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        // Tạo localId âm để lưu vào Room (Tránh trùng với ID thật từ Server vốn là số dương)
        val localId = -tempId

        val pendingMsg = MessageEntity(
            serverId = localId, // ID ÂM
            senderId = myUserId,
            receiverId = receiverId,
            chatType = "private",
            content = content,
            status = "sending",
            createdAt = Date()
        )
        messageDao.insertMessage(pendingMsg)

        try {
            // Gửi tempId dương xuống Native
            NativeClient.sendMessage(receiverId, content, tempId, "private")
        } catch (e: Exception) {
            Log.e("ChatRepo", "Send failed: ${e.message}")
            // Nếu lỗi, update trạng thái thành failed
            messageDao.updateMessageStatus(localId, "failed")
        }
    }

    // [MỚI] Xử lý khi tin nhắn đã gửi thành công (Server ACK)
    // Logic: Xóa tin tạm (ID âm) -> Chèn tin thật (ID dương)
    @Transaction
    suspend fun handleMessageSentAck(tempId: Int, serverId: Int, serverTime: Long) {
        val localId = -tempId // ID âm đã lưu lúc gửi

        // 1. Tìm tin nhắn tạm trong DB để lấy nội dung (content, receiverId,...)
        val tempMsg = messageDao.getMessageById(localId)

        if (tempMsg != null) {
            // 2. Xóa tin nhắn tạm (ID âm)
            messageDao.deleteMessageById(localId)

            // 3. Tạo tin nhắn mới với ID thật từ Server và Timestamp chuẩn
            val realMsg = tempMsg.copy(
                serverId = serverId,        // ID dương từ Server
                status = "sent",            // Cập nhật trạng thái
                createdAt = Date(serverTime), // Dùng thời gian server
                updatedAt = Date()
            )

            // 4. Lưu tin nhắn chính thức vào DB
            messageDao.insertMessage(realMsg)

            Log.d("ChatRepo", "Swapped TempID $localId to ServerID $serverId")
        } else {
            Log.w("ChatRepo", "Could not find temp message with ID $localId to swap")
        }
    }
    // [MỚI] Xử lý update status delivered
    suspend fun updateMessageStatus(serverId: Int, status: String) {
        messageDao.updateMessageStatus(serverId, status)
    }

    // [NEW] Load lịch sử cũ hơn
    fun loadHistory(targetId: Int, offset: Int, limit: Int) {
        // Gọi Native, dữ liệu trả về sẽ vào callback onHistoryReceived -> lưu DB -> Flow update UI
        NativeClient.getChatHistory(targetId, offset, limit)
    }

    // [NEW] Lưu tin nhắn từ Network (Socket trả về)
    suspend fun saveMessageFromNetwork(dto: MessageDto) {
        val entity = MessageEntity(
            serverId = dto.id,
            senderId = dto.senderId,
            receiverId = dto.receiverId,
            chatType = dto.chatType,
            content = dto.content,
            status = "sent", // Tin từ server về mặc định là sent
            createdAt = Date(dto.timestamp)
        )
        messageDao.insertMessage(entity)
    }
}