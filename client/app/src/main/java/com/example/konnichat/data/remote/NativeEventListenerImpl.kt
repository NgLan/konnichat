package com.example.konnichat.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.konnichat.data.remote.dto.GroupDto
import com.example.konnichat.data.remote.dto.GroupMemberDto
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

    override fun onMessageReceived(msg: MessageDto) {
        Log.d(TAG, "📩 Có tin nhắn mới [${msg.chatType}] từ ${msg.senderId}: ${msg.content}")

        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                // 1. Lưu tin nhắn vào Database (Room)
                repo.saveMessageFromNetwork(msg)

                // 2. Xác định ngữ cảnh (Group hay Private)
                val isGroup = (msg.chatType == "group")

                // Nếu là Group: Target là GroupID (receiverId).
                // Nếu là Private: Target là người gửi (senderId).
                val targetId = if (isGroup) msg.receiverId else msg.senderId

                // 3. Kiểm tra xem người dùng có đang mở màn hình chat này không
                // (currentChatTargetId cần được cập nhật từ onResume/onPause của ChatActivity)
                if (currentChatTargetId == targetId) {
                    Log.d(TAG, "Đang chat trong cuộc hội thoại $targetId. Bỏ qua thông báo.")
                    return@launch
                }

                // 4. Chuẩn bị dữ liệu hiển thị thông báo
                context?.let { ctx ->
                    var notifyTitle = ""
                    var notifyContent = ""

                    if (isGroup) {
                        // --- TRƯỜNG HỢP CHAT NHÓM ---
                        // Lấy tên nhóm để làm Tiêu đề
                        val groupInfo = repo.getGroupInfo(targetId)
                        val groupName = groupInfo?.name ?: "Nhóm $targetId"

                        // Lấy tên người gửi để ghép vào nội dung
                        val senderInfo = userRepository?.getFriendById(msg.senderId)
                        val senderName = senderInfo?.name ?: "User ${msg.senderId}"

                        notifyTitle = groupName
                        // Nội dung: "Tên A: Nội dung tin nhắn"
                        notifyContent = "$senderName: ${msg.content}"
                    } else {
                        // --- TRƯỜNG HỢP CHAT RIÊNG (1-1) ---
                        // Lấy tên người gửi làm Tiêu đề
                        val senderInfo = userRepository?.getFriendById(msg.senderId)
                        val senderName = senderInfo?.name ?: "Người dùng ${msg.senderId}"

                        notifyTitle = senderName
                        // Nội dung: Chỉ hiện nội dung tin nhắn
                        notifyContent = msg.content
                    }

                    // 5. Hiển thị thông báo
                    NotificationHelper.showNotification(
                        ctx,
                        title = notifyTitle,
                        content = notifyContent,
                        type = "MESSAGE",
                        targetId = targetId,   // ID để mở lại đoạn chat (GroupId hoặc FriendId)
                        targetName = notifyTitle, // Tên hiển thị trên Toolbar khi mở chat
                        chatType = if (isGroup) "group" else "private"
                    )
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

    override fun onGroupCreated(groupId: Int, groupName: String) {
        Log.d(TAG, "🎉 Group Created: $groupName (ID: $groupId)")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.saveGroupFromNetwork(groupId, groupName)
            }
        }
    }

    // [SỬA] Implement logic nhận sự kiện thêm thành viên
    override fun onGroupMembersAdded(
        groupId: Int,
        addedBy: String,
        newMemberIds: IntArray
    ) {
        Log.d(TAG, "👥 Có thành viên mới vào Group $groupId (Thêm bởi $addedBy). Số lượng: ${newMemberIds.size}")

        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                // Gọi hàm xử lý an toàn trong Repository
                repo.syncGroupMembers(groupId, addedBy, newMemberIds)
            }
        }
    }

    override fun onMemberLeft(
        groupId: Int,
        memberId: Int,
        memberName: String
    ) {
        Log.d(TAG, "🏃 Member Left: $memberName (ID: $memberId) from Group $groupId")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.handleMemberLeft(groupId, memberId, memberName)
            }
        }
    }

    override fun onGroupListReceived(groups: Array<GroupDto>) {
        Log.d(TAG, "📋 Nhận danh sách nhóm từ Server: ${groups.size} nhóm")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.saveGroupListFromNetwork(groups)
            }
        }
    }

    override fun onMemberRemoved(
        groupId: Int,
        memberId: Int,
        memberName: String,
        adminId: Int,
        adminName: String
    ) {
        Log.d(TAG, "🔨 Member Removed: $memberName by Admin $adminName in Group $groupId")

        chatRepository?.let { repo ->
            // [THÊM] Lấy My User ID từ SharedPreferences (thông qua Context)
            val prefs = context?.getSharedPreferences("konnichat_prefs", Context.MODE_PRIVATE)
            val myUserId = prefs?.getInt("USER_ID", -1) ?: -1

            if (myUserId != -1) {
                CoroutineScope(Dispatchers.IO).launch {
                    // Truyền thêm myUserId vào
                    repo.handleMemberRemoved(groupId, memberId, memberName, adminId, adminName, myUserId)
                }
            }
        }
    }

    override fun onGroupDissolved(groupId: Int) {
        Log.d(TAG, "🚫 Group Dissolved: $groupId")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.handleGroupDissolved(groupId)
            }
        }
    }

    override fun onGroupMembersReceived(
        groupId: Int,
        members: Array<GroupMemberDto>
    ) {
        Log.d(TAG, "📋 Nhận danh sách thành viên nhóm $groupId: ${members.size} người")
        chatRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                repo.saveGroupMembersList(groupId, members)
            }
        }
    }

    override fun onMessageUpdated(messageId: Int, actionType: Int) {
        TODO("Not yet implemented")
    }

    override fun onConnectionClosed(reason: String) {
        Log.e(TAG, "Mất kết nối: $reason")
    }
}