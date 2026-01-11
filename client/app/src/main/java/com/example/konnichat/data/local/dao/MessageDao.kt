package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.model.MessageWithSender
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Lấy tin nhắn giữa 2 người (bất kể ai gửi)
    @Query("""
        SELECT * FROM messages 
        WHERE (sender_id = :myId AND receiver_id = :friendId) 
           OR (sender_id = :friendId AND receiver_id = :myId)
           AND (chat_type = 'private' OR chat_type IS NULL OR chat_type = '')
        ORDER BY created_at ASC
    """)
    fun getMessagesBetween(myId: Int, friendId: Int): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE server_id = :id")
    suspend fun getMessageById(id: Int): MessageEntity?

    // 1. Xóa tin nhắn theo ID (Dùng để xóa tin tạm khi có ACK từ server)
    @Query("DELETE FROM messages WHERE server_id = :id")
    suspend fun deleteMessageById(id: Int)

    // 2. Cập nhật trạng thái (Dùng khi nhận sự kiện delivered)
    @Query("UPDATE messages SET status = :status WHERE server_id = :id")
    suspend fun updateMessageStatus(id: Int, status: String)
//    Hàm lấy tin nhắn nhóm (Lấy tất cả tin có receiver_id là GroupID và type là 'group')
    @Query("SELECT * FROM messages WHERE receiver_id = :groupId AND chat_type = 'group' ORDER BY created_at ASC")
    fun getGroupMessages(groupId: Int): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET chat_type = 'private' WHERE chat_type IS NULL OR chat_type = ''")
    suspend fun fixLegacyMessages()

    @Query("""
        SELECT m.*, u.name as senderName 
        FROM messages m
        LEFT JOIN users u ON m.sender_id = u.server_id
        WHERE (m.sender_id = :myId AND m.receiver_id = :friendId) 
           OR (m.sender_id = :friendId AND m.receiver_id = :myId)
           AND (m.chat_type = 'private' OR m.chat_type IS NULL OR m.chat_type = '')
        ORDER BY m.created_at ASC
    """)
    fun getMessagesBetweenWithSender(myId: Int, friendId: Int): Flow<List<MessageWithSender>>

    /**
     * Lấy tin nhắn Group kèm tên người gửi.
     */
    @Query("""
        SELECT m.*, u.name as senderName, u.avatar_url as senderAvatar
        FROM messages m
        LEFT JOIN users u ON m.sender_id = u.server_id
        WHERE m.receiver_id = :groupId AND m.chat_type = 'group'
        ORDER BY m.created_at ASC
    """)
    fun getGroupMessagesWithSender(groupId: Int): Flow<List<MessageWithSender>>

    // [THÊM] Xóa toàn bộ tin nhắn của một nhóm (Dùng khi rời nhóm/bị kick)
    @Query("DELETE FROM messages WHERE receiver_id = :groupId AND chat_type = 'group'")
    suspend fun deleteGroupMessages(groupId: Int)

    @Query("DELETE FROM messages WHERE (sender_id = :userId OR receiver_id = :userId) AND chat_type = 'private'")
    suspend fun deletePrivateChat(userId: Int)

    // Thêm vào trong interface MessageDao
    // [SỬA LẠI] serverId -> server_id
    @Query("UPDATE messages SET status = 'revoked', content = 'Tin nhắn đã bị thu hồi' WHERE server_id = :serverId")
    suspend fun markMessageAsRevoked(serverId: Int)
}
