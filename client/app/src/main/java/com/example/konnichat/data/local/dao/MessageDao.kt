package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Lấy tin nhắn giữa 2 người (bất kể ai gửi)
    @Query("""
        SELECT * FROM messages 
        WHERE (sender_id = :myId AND receiver_id = :friendId) 
           OR (sender_id = :friendId AND receiver_id = :myId)
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
}
