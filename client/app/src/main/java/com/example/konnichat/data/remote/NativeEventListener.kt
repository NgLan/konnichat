package com.example.konnichat.data.remote

import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto

interface NativeEventListener {
    // Khi nhận được danh sách bạn bè (Response của getFriends)
    fun onFriendListReceived(friends: Array<UserDto>)
    // Khi có tin nhắn mới (Push từ Server)
    // fun onMessageReceived(message: MessageDto)

    // Khi bạn bè online/offline (Push từ Server)
    fun onFriendStatusChanged(friendId: Int, isOnline: Boolean)
    // Khi nhận được lời mời kết bạn real-time
    fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String)
    fun onRequestResponse(cmd: Int, status: Int)
    fun onFriendRequestAccepted(user: UserDto)
    fun onFriendRemoved(exFriendId: Int)
    fun onSearchResult(results: Array<UserSearchDto>)
    // Khi bị ngắt kết nối
    fun onConnectionClosed(reason: String)
}
