package com.example.konnichat.data.remote

import android.util.Log
import com.example.konnichat.data.remote.NativeEventListenerImpl.userRepository
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NativeEventListenerImpl : NativeEventListener {

    // Biến này sẽ được HomeActivity gán vào
    var userRepository: UserRepository? = null

    override fun onFriendListReceived(friends: Array<UserDto>) {
        Log.d("KONNI_CLIENT", "Listener: Nhận ${friends.size} bạn.")

        if (userRepository != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Gọi sang UserRepository để lưu
                    userRepository?.saveFriendsFromNetwork(friends)
                    Log.d("KONNI_CLIENT", "Đã lưu user thành công!")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            Log.e("KONNI_ERROR", "UserRepository chưa được khởi tạo!")
        }
    }
    override fun onFriendStatusChanged(userId: Int, isOnline: Boolean) {
        // TODO: Update status user
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

    override fun onFriendRequestAccepted(user: UserDto) {
        TODO("Not yet implemented")
    }

    override fun onFriendRemoved(exFriendId: Int) {
        TODO("Not yet implemented")
    }

    override fun onConnectionClosed(reason: String) {
        Log.e("KONNI_CLIENT", "Socket đóng: $reason")
    }
}