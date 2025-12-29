// File: client/app/src/main/java/com/example/konnichat/data/remote/NativeEventListenerImpl.kt
package com.example.konnichat.data.remote

import android.content.Context
import android.util.Log
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.PendingRequestDto
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NativeEventListenerImpl : NativeEventListener {

    var userRepository: UserRepository? = null
    var context: Context? = null
    private const val TAG = "KONNI_EVENT"

    // --- 1. XỬ LÝ KẾT BẠN (TRỌNG TÂM) ---

    // A. Khi có người gửi lời mời kết bạn (Real-time)
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

        // 2. [QUAN TRỌNG] Tự động thêm trực tiếp vào danh sách chờ của Repo (Optimistic Update)
        // Thay vì chờ Server phản hồi (có thể bị lỗi mạng/socket như trong log),
        // ta tự tạo một DTO giả lập và đẩy vào Repo luôn để UI hiện ngay.
        val fakeRequest = PendingRequestDto(requestId, senderId, senderName)
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "⚡ Tự động thêm lời mời vào danh sách (Optimistic)")
                repo.addSingleRequest(fakeRequest)
            }
        }

        // 3. Vẫn gửi lệnh lấy mới nhất từ Server để đồng bộ sau (nếu socket còn sống)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NativeClient.getPendingRequests()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi gọi getPendingRequests: ${e.message}")
            }
        }
    }

    // B. Khi người khác chấp nhận lời mời của mình
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
                // 2. QUAN TRỌNG: Lưu ngay user này vào Database
                // Việc này sẽ kích hoạt Flow ở MessageListFragment -> List tự reload
                repo.saveSingleFriend(user)

                repo.updateSearchStatusToFriend(user.id)

                // 3. (Optional) Gọi thêm API lấy full list để backup
                NativeClient.getFriends(0, 100)
            }
        }
    }

    // C. Nhận danh sách lời mời (Phản hồi từ Server)
    override fun onPendingRequestsReceived(requests: Array<PendingRequestDto>) {
        Log.d(TAG, "📥 Server trả về ${requests.size} lời mời chờ duyệt.")
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.processPendingRequests(requests)
            }
        }
    }

    // --- 2. CÁC HÀM KHÁC ---

    override fun onMessageReceived(msg: MessageDto) {
        Log.d(TAG, "📩 Có tin nhắn mới từ ${msg.senderId}: ${msg.content}")
    }

    override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
        Log.d(TAG, "ack: Tin nhắn đã gửi thành công (ServerID: $serverId)")
    }

    override fun onMessageDelivered(serverId: Int) {
        Log.d(TAG, "seen: Tin nhắn $serverId đã được chuyển tới người nhận")
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

    override fun onSearchResult(results: Array<UserSearchDto>) {
        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.processSearchResults(results)
            }
        }
    }

    override fun onRequestResponse(cmd: Int, status: Int) { Log.d(TAG, "Response CMD: $cmd, Status: $status") }
    // C. Khi bị hủy kết bạn HOẶC Bị từ chối lời mời (Server cần gửi CMD_NOTIFY_UNFRIENDED)
    override fun onFriendRemoved(exFriendId: Int) {
        Log.d(TAG, "💔 Quan hệ với User $exFriendId đã bị xóa (Unfriend/Reject).")

        userRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                // 1. Xóa khỏi DB (Nếu đang là bạn bè thì sẽ mất khỏi danh sách chat)
                repo.deleteFriend(exFriendId)

                // 2. Cập nhật màn hình Search (Nếu đang tìm kiếm người này)
                // Nút sẽ đổi từ "Bạn bè" hoặc "Đã gửi" -> "Kết bạn"
                repo.updateSearchStatusToNone(exFriendId)
            }
        }

        // (Tùy chọn) Hiện thông báo nếu cần, nhưng thường từ chối thì không cần báo ầm ĩ.
    }

    override fun onConnectionClosed(reason: String) { Log.e(TAG, "Mất kết nối: $reason") }
}