package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.konnichat.data.local.entity.GroupEntity
import com.example.konnichat.data.local.entity.GroupMemberEntity

@Dao
interface GroupDao {

    // --- GROUP ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Query("SELECT * FROM `groups` WHERE server_id = :groupId")
    suspend fun getGroupById(groupId: Int): GroupEntity?

    // --- MEMBERS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getMembersByGroupId(groupId: Int): List<GroupMemberEntity>

    // Xóa thành viên (dùng khi ai đó rời nhóm hoặc bị kick)
    @Query("DELETE FROM group_members WHERE group_id = :groupId AND member_id = :memberId")
    suspend fun deleteMember(groupId: Int, memberId: Int)

    @Query("SELECT role FROM group_members WHERE group_id = :groupId AND member_id = :userId")
    suspend fun getMemberRole(groupId: Int, userId: Int): String?

    // 2. Xóa nhóm khỏi DB (Khi rời hoặc giải tán)
    // Lưu ý: Các bảng Message và Member nếu đã setup Foreign Key CASCADE thì sẽ tự xóa theo.
    @Query("DELETE FROM `groups` WHERE server_id = :groupId")
    suspend fun deleteGroup(groupId: Int)
}