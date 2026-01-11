package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.local.entity.ReactionEntity

@Dao
interface ReactionDao {
    // Lưu reaction mới (Nếu user đó đã react tin này rồi thì ghi đè - UPDATE)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: ReactionEntity)

    // Xóa reaction (Khi người dùng chọn gỡ cảm xúc hoặc server báo xóa)
    @Query("DELETE FROM reactions WHERE message_id = :messageId AND user_id = :userId")
    suspend fun deleteReaction(messageId: Int, userId: Int)

    // [Tuỳ chọn] Xóa tất cả reaction của 1 tin nhắn (Dùng khi xóa tin nhắn)
    @Query("DELETE FROM reactions WHERE message_id = :messageId")
    suspend fun deleteAllReactionsOfMessage(messageId: Int)
}