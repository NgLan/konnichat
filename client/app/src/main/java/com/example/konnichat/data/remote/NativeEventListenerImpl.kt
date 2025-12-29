// File: client/app/src/main/java/com/example/konnichat/data/remote/NativeEventListenerImpl.kt
package com.example.konnichat.data.remote

import android.util.Log
import com.example.konnichat.data.remote.NativeEventListenerImpl.userRepository
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NativeEventListenerImpl : NativeEventListener {

    // Biến này được gán từ App.kt (thông qua HomeActivity hoặc khởi tạo ban đầu)
    var userRepository: UserRepository? = null

    override fun onFriendListReceived(friends: Array<UserDto>) {
        Log.d("KONNI_CLIENT", "Listener: Nhận ${friends.size} bạn.")
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo.saveFriendsFromNetwork(friends)
                    Log.d("KONNI_CLIENT", "Đã lưu danh sách bạn bè.")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // --- ĐÃ SỬA: Xử lý sự kiện Online/Offline ---
    override fun onFriendStatusChanged(friendId: Int, isOnline: Boolean) {
        Log.d("KONNI_CLIENT", "Status Update: User $friendId is now ${if (isOnline) "Online" else "Offline"}")

        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Cập nhật Database -> UI sẽ tự nhảy nhờ Flow/LiveData
                    repo.updateFriendStatus(friendId, isOnline)
                } catch (e: Exception) {
                    Log.e("KONNI_ERROR", "Lỗi cập nhật status: ${e.message}")
                }
            }
        }
    }

    // Các hàm khác giữ nguyên TODO hoặc implement sau
    override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {
        // TODO implementation
    }

    override fun onRequestResponse(cmd: Int, status: Int) {
        // TODO implementation
    }

    override fun onFriendRequestAccepted(user: UserDto) {
        // TODO implementation
    }

    override fun onFriendRemoved(exFriendId: Int) {
        // TODO implementation
    }

    override fun onSearchResult(results: Array<UserSearchDto>) {
        // TODO implementation
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

    override fun onConnectionClosed(reason: String) {
        Log.e("KONNI_CLIENT", "Socket đóng: $reason")
    }
}