// File: client/app/src/main/java/com/example/konnichat/data/local/dao/UserDao.kt
package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // Thêm hàm này: Lấy danh sách bạn bè, sắp xếp người Online lên đầu
    @Query("SELECT * FROM users WHERE server_id != :myId ORDER BY is_online DESC, name ASC")
    fun getAllFriends(myId: Int): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE server_id = :id")
    suspend fun getUserById(id: Int): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET is_online = :isOnline WHERE server_id = :friendId")
    suspend fun updateFriendStatus(friendId: Int, isOnline: Boolean)

    // Thêm vào interface UserDao
    @Query("UPDATE users SET is_online = 0")
    suspend fun resetAllStatusOffline()

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE server_id = :id)")
    suspend fun isFriend(id: Int): Boolean

    @Query("DELETE FROM users WHERE server_id = :id")
    suspend fun deleteUserByServerId(id: Int)
}