package com.example.konnichat
import android.util.Log
import java.util.ArrayList
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
    /**
     * Task 8: Gửi lời mời kết bạn
     * @param senderId: ID của mình
     * @param receiverId: ID người muốn kết bạn
     * @return >0 nếu thành công (là RequestID), mã lỗi nếu thất bại
     */
    external fun sendFriendRequest(senderId: Int, receiverId: Int): Int

    // Lấy danh sách chờ
    /**
     * Task 9A: Lấy danh sách lời mời đang chờ (để hiển thị cho người nhận duyệt)
     * @param userId: ID của mình
     * @return Danh sách các lời mời
     */
    external fun getPendingRequests(userId: Int): ArrayList<PendingRequest>?

    // Duyệt (1: Đồng ý, 0: Từ chối). Return 1 nếu thành công
    /**
     * Task 9B: Phản hồi lời mời
     * @param requestId: ID của lời mời (lấy từ PendingRequest.requestId)
     * @param isAccepted: 1 là Đồng ý, 0 là Từ chối
     * @return 1 nếu thành công, 0 nếu thất bại
     */
    external fun respondFriendRequest(requestId: Int, isAccepted: Int): Int

    // Hủy kết bạn. Return 1 nếu thành công
    /**
     * Task 10: Hủy kết bạn
     * @param userId: ID của mình
     * @param friendId: ID người bạn muốn xóa
     * @return 1 nếu thành công
     */
    external fun unfriend(userId: Int, friendId: Int): Int

    // Trả về danh sách người dùng tìm được (trừ bản thân và bạn bè đã kết bạn - tùy logic server)
    /**
     * Task 11: Tìm kiếm người dùng
     * @param keyword: Tên muốn tìm
     * @param currentUserId: ID của mình (để loại trừ khỏi kết quả)
     * @return Danh sách kết quả tìm kiếm
     */
    external fun searchUsers(keyword: String, currentUserId: Int): ArrayList<UserSearchInfo>?
    // 3. Chat
//    external fun sendMessage(senderId: Int, receiverId: Int, content: String)
//    external fun receiveMessage(): Message?

    // Gọi hàm này ngay sau khi Login thành công
    external fun startListening()

    interface FriendRequestCallback {
        fun onNewRequestReceived(senderId: Int, senderName: String)
    }

    private var callback: FriendRequestCallback? = null

    fun setFriendRequestCallback(cb: FriendRequestCallback?) {
        this.callback = cb
    }

    fun onFriendRequestReceived(senderId: Int, senderName: String) {
        Log.d("NativeClient", "NHẬN ĐƯỢC LỜI MỜI TỪ: $senderName (ID: $senderId)")

        // Báo ngay cho màn hình nào đang lắng nghe
        callback?.onNewRequestReceived(senderId, senderName)
    }

}
