package com.example.konnichat

object NativeClient {
    init {
        System.loadLibrary("konnichat")
    }

    // --- CÁC HÀM DÙNG CHUNG CHO TOÀN APP ---

    // 1. Kết nối & Auth
    external fun connectToServer(): String
    external fun loginUser(user: String, pass: String): Int
    external fun registerUser(user: String, pass: String): Int

    // 2. Bạn bè
    external fun getFriendList(userId: Int): ArrayList<Friend>?
    // Gửi lời mời. Return > 0 nếu thành công (ReqID), <= 0 nếu lỗi
    external fun sendFriendRequest(senderId: Int, receiverId: Int): Int

    // Lấy danh sách chờ
    external fun getPendingRequests(userId: Int): ArrayList<PendingRequest>?

    // Duyệt (1: Đồng ý, 0: Từ chối). Return 1 nếu thành công
    external fun respondFriendRequest(requestId: Int, isAccepted: Int): Int

    // Hủy kết bạn. Return 1 nếu thành công
    external fun unfriend(userId: Int, friendId: Int): Int

    // Trả về danh sách người dùng tìm được (trừ bản thân và bạn bè đã kết bạn - tùy logic server)
    external fun searchUsers(keyword: String, currentUserId: Int): ArrayList<UserSearchInfo>?
    // 3. Chat
//    external fun sendMessage(senderId: Int, receiverId: Int, content: String)
//    external fun receiveMessage(): Message?
}