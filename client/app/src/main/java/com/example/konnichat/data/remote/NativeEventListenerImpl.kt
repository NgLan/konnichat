package com.example.konnichat.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.PendingRequestDto
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
import com.example.konnichat.data.repository.ChatRepository // Import này có thể cần sửa tùy package của bạn
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.utils.NotificationHelper
// Nếu ChatActivity nằm ở package khác, hãy import nó hoặc sửa logic check id
// import com.example.konnichat.ui.chat.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object NativeEventListenerImpl : NativeEventListener {
    var userRepository: UserRepository? = null
    var chatRepository: ChatRepository? = null // Tui đã thêm biến này để tránh lỗi
    var context: Context? = null

    // Biến tạm để check xem user có đang chat không (Bạn cần cập nhật biến này từ Activity)
    var currentChatTargetId: Int = -1

    private const val TAG = "KONNI_EVENT"

    fun init(context: Context, userRepository: UserRepository) {
        this.context = context.applicationContext // Luôn lấy Application Context
        this.userRepository = userRepository
    }

    // --- 1. XỬ LÝ KẾT BẠN (TRỌNG TÂM) ---

    override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {
        Log.d(TAG, "🔔 Nhận lời mời từ: $senderName (ID: $senderId) - ReqID: $requestId")

        // 1. Hiện thông báo
        context?.let { ctx ->
            NotificationHelper.showNotification(
                ctx,
                title = "Lời mời kết bạn mới",
                content = "$senderName muốn kết bạn với bạn",
                type = "FRIEND_REQ"
            )
        }

        // 2. Optimistic Update
        val fakeRequest = PendingRequestDto(requestId, senderId, senderName)
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.addSingleRequest(fakeRequest)
            }
        }

        // 3. Sync lại cho chắc
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NativeClient.getPendingRequests()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi getPendingRequests: ${e.message}")
            }
        }
    }

    override fun onFriendRequestAccepted(user: UserDto) {
        Log.d(TAG, "✅ ${user.name} đã chấp nhận lời mời.")

        context?.let { ctx ->
            NotificationHelper.showNotification(
                ctx,
                title = "Đã kết bạn!",
                content = "${user.name} đã chấp nhận lời mời kết bạn.",
                type = "FRIEND_LIST"
            )
        }

        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.saveSingleFriend(user)
                repo.updateSearchStatusToFriend(user.id)
                // Optional: NativeClient.getFriends(0, 100)
            }
        }
    }

    override fun onPendingRequestsReceived(requests: Array<PendingRequestDto>) {
        Log.d(TAG, "📥 Server trả về ${requests.size} lời mời chờ duyệt.")
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.processPendingRequests(requests)
            }
        }
    }

    override fun onFriendListReceived(friends: Array<UserDto>) {
        Log.d(TAG, "Nhận danh sách bạn bè: ${friends.size} người")
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.saveFriendsFromNetwork(friends)
            }
        }
    }

    override fun onFriendStatusChanged(friendId: Int, isOnline: Boolean) {
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.updateFriendStatus(friendId, isOnline)
            }
        }
    }

    override fun onFriendRemoved(exFriendId: Int) {
        Log.d(TAG, "💔 Quan hệ với User $exFriendId đã bị xóa.")
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.deleteFriend(exFriendId)
                repo.updateSearchStatusToNone(exFriendId)
            }
        }
    }

    override fun onRequestResponse(cmd: Int, status: Int) {
        Log.d(TAG, "Response CMD: $cmd, Status: $status")
    }

    // C. Khi bị hủy kết bạn HOẶC Bị từ chối lời mời (Server cần gửi CMD_NOTIFY_UNFRIENDED)
    override fun onFriendRemoved(exFriendId: Int) {
        Log.d(TAG, "💔 Quan hệ với User $exFriendId đã bị xóa (Unfriend/Reject).")

    override fun onMessageReceived(msg: MessageDto) {
        Log.d(TAG, "📩 Có tin nhắn mới từ ${msg.senderId}: ${msg.content}")

        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                // 1. Lưu vào Database
                repo.saveMessageFromNetwork(msg)

                // 2. Logic Thông báo
                // Kiểm tra xem có đang chat với người này không.
                // Lưu ý: Bạn cần update biến currentChatTargetId từ ChatActivity (onResume/onPause)
                if (currentChatTargetId != msg.senderId) {
                    context?.let { ctx ->
                        val senderName = "User ${msg.senderId}" // Có thể query DB lấy tên thật sau
                        NotificationHelper.showNotification(
                            ctx,
                            title = senderName,
                            content = msg.content,
                            type = "MESSAGE"
                        )
                    }
                } else {
                    Log.d(TAG, "User đang chat với ${msg.senderId}. Không hiện Notification.")
                }
            }
        }
    }

    override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
        Log.d(TAG, "ack: Tin đã gửi (Temp: $tempId -> Server: $serverId)")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                // Gọi hàm xử lý trong Repo
                repo.handleMessageSentAck(tempId, serverId, serverTime)
            }
        }
    }

    override fun onMessageDelivered(serverId: Int) {
        Log.d(TAG, "seen: Tin nhắn $serverId đã tới nơi")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.updateMessageStatus(serverId, "delivered")
            }
        }
    }

    override fun onHistoryReceived(messages: Array<MessageDto>) {
        Log.d(TAG, "📜 Nhận lịch sử chat: ${messages.size} tin")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                messages.forEach { msg ->
                    repo.saveMessageFromNetwork(msg)
                }
            }
        }
    }

    // --- 3. CÁC HÀM KHÁC ---

    override fun onSearchResult(results: Array<UserSearchDto>) {
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.processSearchResults(results)
            }
        }
    }

    override fun onRequestResponse(cmd: Int, status: Int) {
        Log.d(TAG, "Response CMD: $cmd, Status: $status")
    }

    override fun onConnectionClosed(reason: String) {
        Log.e(TAG, "❌ Mất kết nối: $reason")
    }
}
    override fun onGroupCreated(groupId: Int, groupName: String) {
        TODO("Not yet implemented")
    }

    override fun onConnectionClosed(reason: String) { 
        Log.e(TAG, "Mất kết nối: $reason") 
    }
}
