package com.example.konnichat.data.repository

import android.util.Log
import com.example.konnichat.NativeClient
import com.example.konnichat.data.mapper.MessageMapper
import com.example.konnichat.data.source.local.dao.MessageDao
import com.example.konnichat.data.source.local.entity.MessageEntity
import com.example.konnichat.domain.enums.MessageStatus
import com.example.konnichat.domain.model.Message
import com.example.konnichat.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatRepositoryImpl(
    private val dao: MessageDao,
    private val mapper: MessageMapper
) : ChatRepository {

    override fun getMessages(myUserId: Int, friendId: Int): Flow<List<Message>> {
        return dao.getConversation(myUserId, friendId).map { entityList ->
            entityList.map { mapper.mapToDomain(it) }
        }
    }

    override suspend fun sendMessage(message: Message) {
        withContext(Dispatchers.IO) {
            // BƯỚC 1: Lưu trạng thái SENDING (Đang gửi)
            // Message ID ở đây đang là ID tạm (timestamp), không sao cả.
            var entity = mapper.mapToEntity(message).copy(
                status = MessageStatus.SENDING.name.lowercase()
            )
            dao.insertMessage(entity)

            // BƯỚC 2: Gọi Native gửi tin
            try {
                // NativeClient gửi đi. Nếu Socket lỗi sẽ ném Exception (hoặc cần check return)
                NativeClient.sendMessage(message.senderId, message.receiverId, message.content ?: "")

                // BƯỚC 3: Nếu code chạy đến đây nghĩa là không lỗi -> Update thành SENT
                dao.updateMessageStatus(entity.id, MessageStatus.SENT.name.lowercase())

                Log.d("ChatRepo", "Tin nhắn ${entity.id} đã gửi thành công (SENT)")

            } catch (e: Exception) {
                // Nếu lỗi: Giữ nguyên trạng thái SENDING.
                // Lần sau mở app, có thể viết logic quét các tin SENDING để gửi lại.
                Log.e("ChatRepo", "Gửi thất bại, tin nhắn đang treo ở trạng thái SENDING: ${e.message}")
            }
        }
    }

    override suspend fun startReceivingMessageLoop() {
        withContext(Dispatchers.IO) {
            while (true) {
                try {
                    val nativeMsg = NativeClient.receiveMessage()
                    if (nativeMsg != null) {
                        // Tin nhắn nhận được -> Status là RECEIVED
                        val entity = MessageEntity(
                            id = nativeMsg.serverMsgId,
                            senderId = nativeMsg.senderId,
                            receiverId = 0, // ID của mình (người nhận)
                            content = nativeMsg.content,
                            status = MessageStatus.RECEIVED.name.lowercase(), // Đổi thành RECEIVED
                            createdAt = nativeMsg.timestamp,
                            updatedAt = nativeMsg.timestamp
                        )
                        dao.insertMessage(entity)
                    } else {
                        delay(100)
                    }
                } catch (e: Exception) {
                    delay(3000)
                }
            }
        }
    }

    override suspend fun syncOfflineMessages(myUserId: Int) {
        withContext(Dispatchers.IO) {
            try {
                // Gọi JNI lấy list tin nhắn
                val offlineMsgs = NativeClient.fetchOfflineMessages(myUserId)

                if (offlineMsgs != null && offlineMsgs.isNotEmpty()) {
                    Log.d("ChatRepo", "Đồng bộ được ${offlineMsgs.size} tin nhắn offline.")
                    for (dto in offlineMsgs) {
                        val entity = MessageEntity(
                            id = dto.serverMsgId,
                            senderId = dto.senderId,
                            receiverId = myUserId, // Người nhận là mình
                            content = dto.content,
                            status = MessageStatus.RECEIVED.name.lowercase(),
                            createdAt = dto.timestamp,
                            updatedAt = dto.timestamp
                        )
                        dao.insertMessage(entity)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepo", "Lỗi sync offline: ${e.message}")
            }
        }
    }

    override suspend fun syncChatHistory(myUserId: Int, friendId: Int) {
        withContext(Dispatchers.IO) {
            try {
                val historyMsgs = NativeClient.getChatHistory(myUserId, friendId)
                if (historyMsgs != null) {
                    Log.d("ChatRepo", "Lấy được ${historyMsgs.size} tin nhắn lịch sử.")
                    for (dto in historyMsgs) {
                        // Xác định status: Nếu mình gửi thì là SENT/READ, nếu họ gửi thì là RECEIVED
                        // Đơn giản hóa: Cứ set là READ hoặc RECEIVED
                        val status = if (dto.senderId == myUserId)
                            MessageStatus.SENT.name.lowercase()
                        else
                            MessageStatus.RECEIVED.name.lowercase()

                        val entity = MessageEntity(
                            id = dto.serverMsgId,
                            senderId = dto.senderId,
                            receiverId = if (dto.senderId == myUserId) friendId else myUserId,
                            content = dto.content,
                            status = status,
                            createdAt = dto.timestamp,
                            updatedAt = dto.timestamp
                        )
                        // REPLACE: Nếu tin nhắn đã có trong DB thì update đè lên (không sợ trùng)
                        dao.insertMessage(entity)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
