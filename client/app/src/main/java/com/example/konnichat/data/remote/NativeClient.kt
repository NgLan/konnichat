package com.example.konnichat.data.remote

import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.PendingRequestDto

object NativeClient {
    init {
        System.loadLibrary("konnichat")
    }

    external fun startListening(listener: NativeEventListener)

    // Kết nối đến server socket
    external fun connect(ip: String, port: Int): Int

    // Đóng kết nối
    external fun disconnect()

    // Đăng ký
    external fun registerUser(name: String, email: String, pass: String): Int

    // Đăng nhập: Trả về UserDto nếu OK, ném Exception nếu lỗi
    @Throws(Exception::class)
    external fun loginUser(email: String, pass: String): UserDto?

    /**
     * Gửi yêu cầu lấy danh sách bạn bè.
     * Lưu ý: Dữ liệu sẽ gửi về qua callback onFriendListReceived.
     * @throws NativeException nếu lỗi mạng (gửi thất bại).
     */
    external fun getFriends(offset: Int, limit: Int): Int
    
    external fun sendFriendRequest(targetId: Int)

    external fun respondFriendRequest(requestId: Int, isAccepted: Boolean)

    external fun unfriendUser(targetId: Int)
    external fun searchUsers(keyword: String, offset: Int, limit: Int)

    external fun getPendingRequests()
    external fun sendMessage(senderId: Int, receiverId: Int, content: String, tempId: Int, chatType: String)
    external fun fetchOfflineMessages()
    // Thêm tham số isGroup
    external fun getChatHistory(targetId: Int, isGroup: Boolean, offset: Int, limit: Int)
    external fun createGroup(name: String, memberIds: IntArray)
    external fun addMembersToGroup(groupId: Int, userIds: IntArray)

    external fun leaveGroup(groupId: Int)
    external fun getGroupList(offset: Int, limit: Int)
}
