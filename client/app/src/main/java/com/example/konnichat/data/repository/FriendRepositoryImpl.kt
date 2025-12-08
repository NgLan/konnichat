package com.example.konnichat.data.repository

import android.util.Log
import com.example.konnichat.NativeClient
import com.example.konnichat.data.mapper.UserMapper
import com.example.konnichat.data.source.local.dao.FriendDao
import com.example.konnichat.data.source.local.dao.UserDao
import com.example.konnichat.data.source.local.entity.FriendEntity
import com.example.konnichat.data.source.local.entity.UserEntity
import com.example.konnichat.domain.enums.NotificationState
import com.example.konnichat.domain.enums.OnlineStatus
import com.example.konnichat.domain.enums.UserStatus
import com.example.konnichat.domain.model.User
import com.example.konnichat.domain.repository.FriendRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FriendRepositoryImpl(
    private val friendDao: FriendDao,
    private val userDao: UserDao,
    private val mapper: UserMapper
) : FriendRepository {

    override suspend fun getListFriends(myUserId: Int): List<User> {
        Log.d("FriendRepo", "Bắt đầu lấy danh sách bạn bè cho UserID: $myUserId")
        // 1. Gọi xuống Native (C++) để lấy dữ liệu mới nhất từ Server
        try {
            val nativeFriends = NativeClient.getFriendList(myUserId)
            Log.d("FriendRepo", "Native trả về số lượng: ${nativeFriends?.size ?: 0}")

            if (nativeFriends != null && nativeFriends.isNotEmpty()) {
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // 2. Map dữ liệu từ NativeDTO sang Room Entity
                val userEntities = ArrayList<UserEntity>()
                val friendEntities = ArrayList<FriendEntity>()

                for (dto in nativeFriends) {
                    // Lưu thông tin User (Bạn bè)
                    userEntities.add(
                        UserEntity(
                            id = dto.id,
                            email = "", // Server không trả về email bạn bè, để trống
                            password = "", // Không cần password của bạn
                            name = dto.name,
                            age = 0,
                            status = UserStatus.ACTIVE.name.lowercase(),
                            isOnline = if (dto.isOnline) OnlineStatus.ONLINE.name else OnlineStatus.OFFLINE.name,
                            avatarUrl = null,
                            createdAt = currentTime,
                            updatedAt = currentTime
                        )
                    )

                    // Lưu quan hệ bạn bè
                    friendEntities.add(
                        FriendEntity(
                            id = (myUserId.toString() + dto.id.toString()).hashCode(), // Tạo ID giả định
                            userId = myUserId,
                            friendId = dto.id,
                            notification = NotificationState.ON.name,
                            createdAt = currentTime
                        )
                    )
                }

                // 3. Lưu vào Local Database (Room)
                // Dùng transaction hoặc insert từng cái
                if (userEntities.isNotEmpty()) {
                    Log.d("FriendRepo", "Đang lưu ${userEntities.size} users và friends vào Room...")
                    userDao.insertUsers(userEntities)
                    for (friend in friendEntities) {
                        try {
                            friendDao.insertFriend(friend)
                        } catch (e: Exception) {
                            Log.e("FriendRepo", "Lỗi lưu friend relation (User: ${friend.userId} - Friend: ${friend.friendId}): ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FriendRepo", "Lỗi khi lấy friend từ server (Có thể đang offline): ${e.message}")
            // Nếu lỗi mạng, code sẽ chạy tiếp xuống dưới để lấy dữ liệu cũ trong Cache (Offline mode)
            e.printStackTrace()
        }

        // 4. Luôn trả về dữ liệu từ Local Database (Single Source of Truth)
        val entities = friendDao.getFriendListDetails(myUserId)
        Log.d("FriendRepo", "Dữ liệu cuối cùng lấy từ Room: ${entities.size} bạn bè")

        return entities.map { mapper.mapToDomain(it) }
    }
}
