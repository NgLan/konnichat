package com.example.konnichat.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.source.local.entity.GroupEntity
import com.example.konnichat.data.source.local.entity.GroupMemberEntity
import com.example.konnichat.data.source.local.entity.GroupMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    // --- GROUPS ---
    @Query("SELECT * FROM `Groups` WHERE id = :groupId")
    suspend fun getGroupById(groupId: Int): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    // --- MEMBERS ---
    @Query("SELECT * FROM GroupMembers WHERE group_id = :groupId")
    suspend fun getMembers(groupId: Int): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    // --- MESSAGES ---
    @Query("SELECT * FROM GroupMessages WHERE group_id = :groupId ORDER BY created_at ASC")
    fun getGroupMessages(groupId: Int): Flow<List<GroupMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(msg: GroupMessageEntity)
}
