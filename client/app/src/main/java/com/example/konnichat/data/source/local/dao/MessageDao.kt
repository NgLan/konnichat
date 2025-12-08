package com.example.konnichat.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.source.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("""
        SELECT * FROM Messages 
        WHERE (sender_id = :myId AND receiver_id = :partnerId) 
           OR (sender_id = :partnerId AND receiver_id = 0) -- 0 là ID tạm của receiver khi nhận tin
           OR (sender_id = :partnerId AND receiver_id = :myId)
        ORDER BY created_at ASC
    """)
    fun getConversation(myId: Int, partnerId: Int): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM Messages WHERE id = :msgId")
    suspend fun deleteMessage(msgId: Int)

    @Query("UPDATE Messages SET status = :newStatus WHERE id = :msgId")
    suspend fun updateMessageStatus(msgId: Int, newStatus: String)
}
