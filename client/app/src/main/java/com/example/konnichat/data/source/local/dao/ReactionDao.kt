package com.example.konnichat.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.source.local.entity.IconEntity
import com.example.konnichat.data.source.local.entity.ReactionEntity

@Dao
interface ReactionDao {
    // --- ICONS ---
    @Query("SELECT * FROM Icons")
    suspend fun getAllIcons(): List<IconEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIcons(icons: List<IconEntity>)

    // --- REACTIONS ---
    @Query("SELECT * FROM MessageReactions WHERE message_id = :messageId")
    suspend fun getReactionsForMessage(messageId: Int): List<ReactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: ReactionEntity)
}
