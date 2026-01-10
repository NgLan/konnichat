// File: client/app/src/main/java/com/example/konnichat/data/local/dao/UserDao.kt
package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.konnichat.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // Thêm hàm này: Lấy danh sách bạn bè, sắp xếp người Online lên đầu
    @Query("SELECT * FROM users WHERE server_id != :myId AND relation_type = 1 ORDER BY is_online DESC, name ASC")
    abstract fun getAllFriends(myId: Int): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE server_id = :id LIMIT 1")
    suspend fun getUserById(id: Int): UserEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertUserIgnore(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertUsersIgnore(users: List<UserEntity>): List<Long>
    @Update
    abstract suspend fun updateUser(user: UserEntity)

    @Update
    abstract suspend fun updateUsers(users: List<UserEntity>)

    @Transaction
    open suspend fun insertUser(user: UserEntity) {
        val id = insertUserIgnore(user)
        if (id == -1L) {
            // Nếu insert thất bại (do đã tồn tại), thực hiện update
            updateUser(user)
        }
    }

    // 2. Upsert danh sách User (Dùng cho sync danh sách bạn bè/thành viên)
    @Transaction
    open suspend fun insertUsers(users: List<UserEntity>) {
        val insertResults = insertUsersIgnore(users)
        val updateList = mutableListOf<UserEntity>()

        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                // Item này đã tồn tại, đưa vào danh sách cần update
                updateList.add(users[i])
            }
        }

        if (updateList.isNotEmpty()) {
            updateUsers(updateList)
        }
    }

    @Query("UPDATE users SET is_online = :isOnline WHERE server_id = :friendId")
    suspend fun updateFriendStatus(friendId: Int, isOnline: Boolean)

    // Thêm vào interface UserDao
    @Query("UPDATE users SET is_online = 0")
    suspend fun resetAllStatusOffline()

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE server_id = :id)")
    suspend fun isFriend(id: Int): Boolean

    @Query("UPDATE users SET relation_type = 0 WHERE server_id = :id")
    abstract suspend fun unfriendLocalUser(id: Int)

    @Query("UPDATE users SET relation_type = 1 WHERE server_id = :id")
    abstract suspend fun makeFriendLocalUser(id: Int)

    @Query("DELETE FROM users WHERE server_id = :id")
    suspend fun deleteUserByServerId(id: Int)

    @Query("UPDATE users SET name = :name, email = :email, is_online = :isOnline, avatar_url = :avatarUrl WHERE server_id = :id")
    abstract suspend fun updateUserInfoOnly(id: Int, name: String, email: String, isOnline: Boolean, avatarUrl: String?)

    @Query("""
        UPDATE users 
        SET name = :name, email = :email, is_online = :isOnline, is_full_data = 1, updated_at = :updateTime 
        WHERE server_id = :id
    """)
    abstract suspend fun updateAndVerifyUser(id: Int, name: String, email: String, isOnline: Boolean, updateTime: java.util.Date = java.util.Date())

    // [THÊM MỚI] Hàm Upsert dành riêng cho các nguồn dữ liệu tin cậy (Search/GroupInfo)
    @Transaction
    open suspend fun upsertVerifiedUser(user: UserEntity) {
        val rowId = insertUserIgnore(user.copy(isFullData = true))
        if (rowId == -1L) {
            // Nếu User đã tồn tại (có thể là User tạm), ta nâng cấp họ lên chính thức
            updateAndVerifyUser(user.serverId, user.name, user.email, user.isOnline)
        }
    }

    // Sửa lại hàm update để chỉ cập nhật các trường thông tin, giữ nguyên flag nếu đã verify
    @Query("""
        UPDATE users 
        SET name = :name, email = :email, is_online = :isOnline, 
            is_full_data = CASE WHEN is_full_data = 1 THEN 1 ELSE :isFullData END 
        WHERE server_id = :id
    """)
    abstract suspend fun updateKeepVerifyStatus(id: Int, name: String, email: String, isOnline: Boolean, isFullData: Int)

}