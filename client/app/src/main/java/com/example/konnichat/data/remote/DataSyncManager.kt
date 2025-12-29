package com.example.konnichat.data.remote

import android.util.Log
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.PendingRequestDto
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Class quản lý việc đồng bộ dữ liệu từ Native (C) vào Database (Kotlin).
 * Nó đứng giữa lắng nghe Server và cập nhật Local DB.
 */
class DataSyncManager(private val db: AppDatabase) : NativeEventListener {

    // Scope riêng để chạy các tác vụ ghi DB (tránh chặn luồng UI hoặc luồng Native)
    private val scope = CoroutineScope(Dispatchers.IO)

    // 1. Khi nhận được danh sách bạn bè từ Server
    override fun onFriendListReceived(friends: Array<UserDto>) {
        Log.d("DataSync", "Đã nhận ${friends.size} bạn bè từ Server. Đang lưu vào DB...")

        scope.launch {
            // Convert từ DTO (Data Transfer Object) sang Entity (Database Table)
            val userEntities = friends.map { dto ->
                UserEntity(
                    serverId = dto.id,
                    email = dto.email,
                    name = dto.name,
                    isOnline = dto.isOnline,
                    status = "active", // Mặc định
                    age = null,        // Server chưa trả về
                    avatarUrl = null   // Server chưa trả về
                )
            }
            // Lưu hàng loạt vào DB (Insert hoặc Update nếu đã có)
            db.userDao().insertUsers(userEntities)
        }
    }

    // 2. Khi trạng thái Online/Offline của bạn bè thay đổi
    override fun onFriendStatusChanged(friendId: Int, isOnline: Boolean) {
        Log.d("DataSync", "User $friendId đổi trạng thái: $isOnline")

        scope.launch {
            // Lấy user cũ ra
            val user = db.userDao().getUserById(friendId)
            user?.let {
                // Copy user cũ nhưng sửa lại trạng thái online
                val updatedUser = it.copy(isOnline = isOnline)
                db.userDao().insertUser(updatedUser)
            }
        }
    }

    override fun onFriendRequestReceived(
        requestId: Int,
        senderId: Int,
        senderName: String
    ) {
        TODO("Not yet implemented")
    }

    override fun onRequestResponse(cmd: Int, status: Int) {
        TODO("Not yet implemented")
    }

    override fun onPendingRequestsReceived(requests: Array<PendingRequestDto>) {
        // Hiện tại DataSyncManager chưa cần xử lý cái này (UI xử lý bên NativeEventListenerImpl)
        // Bạn có thể log ra hoặc để TODO
        Log.d("DataSync", "Ignored pending requests list (handled by UI)")
    }

    override fun onFriendRequestAccepted(user: UserDto) {
        TODO("Not yet implemented")
    }

    override fun onFriendRemoved(exFriendId: Int) {
        TODO("Not yet implemented")
    }

    override fun onSearchResult(results: Array<UserSearchDto>) {
        TODO("Not yet implemented")
    }

    override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
        TODO("Not yet implemented")
    }

    override fun onMessageReceived(msg: MessageDto) {
        TODO("Not yet implemented")
    }

    override fun onMessageDelivered(serverId: Int) {
        TODO("Not yet implemented")
    }

    override fun onHistoryReceived(messages: Array<MessageDto>) {
        TODO("Not yet implemented")
    }

    // 3. Khi mất kết nối (Log để debug)
    override fun onConnectionClosed(reason: String) {
        Log.e("DataSync", "Mất kết nối: $reason")
    }


}