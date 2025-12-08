package com.example.konnichat.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.source.local.entity.FriendEntity
import com.example.konnichat.data.source.local.entity.FriendRequestEntity
import com.example.konnichat.data.source.local.entity.UserEntity

@Dao
interface FriendDao {
    // --- FRIENDS ---
    @Query("SELECT * FROM Friends WHERE user_id = :userId")
    suspend fun getFriendsOfUser(userId: Int): List<FriendEntity>

    // Lấy thông tin chi tiết User từ bảng Friends (JOIN)
    @Query("""
        SELECT u.* FROM Users u 
        INNER JOIN Friends f ON u.id = f.friend_id 
        WHERE f.user_id = :myUserId
    """)
    suspend fun getFriendListDetails(myUserId: Int): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)

    // --- REQUESTS ---
    @Query("SELECT * FROM FriendRequests WHERE receiver_id = :myUserId AND status = 'waiting'")
    suspend fun getIncomingRequests(myUserId: Int): List<FriendRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: FriendRequestEntity)
}
