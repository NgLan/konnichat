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
}
