package com.example.konnichat.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.konnichat.data.local.dao.ConversationDao
import com.example.konnichat.data.local.dao.MessageDao // [NEW] Thêm DAO
import com.example.konnichat.data.local.dao.GroupDao
import com.example.konnichat.data.local.entity.GroupEntity // [MỚI] Import
import com.example.konnichat.data.local.entity.GroupMemberEntity
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.model.ConversationItem
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.GroupMemberDto
import kotlinx.coroutines.flow.Flow
import java.util.Date
import androidx.room.Transaction
import com.example.konnichat.data.local.model.MessageWithSender
import com.example.konnichat.data.local.dao.UserDao // [THÊM] Import UserDao
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.data.local.model.GroupMemberWithUser


class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val userDao: UserDao,
    private val prefs: SharedPreferences
) {

    // Hàm lấy danh sách hội thoại (Realtime Local)
    fun getConversationList(myUserId: Int): Flow<List<ConversationItem>> {
        return conversationDao.getConversationList(myUserId)
    }

    // [NEW] Lấy tin nhắn Realtime từ DB
    fun getMessages(myUserId: Int, targetId: Int, chatType: String): Flow<List<MessageWithSender>> {
        return if (chatType == "group") {
            // Nếu là nhóm -> Gọi hàm DAO nhóm
//            messageDao.getGroupMessages(targetId)
            messageDao.getGroupMessagesWithSender(targetId)
        } else {
            // Nếu là private -> Gọi hàm DAO cũ
//            messageDao.getMessagesBetween(myUserId, targetId)
            messageDao.getMessagesBetweenWithSender(myUserId, targetId)
        }
    }

    // [NEW] Gửi tin nhắn
    suspend fun sendMessage(myUserId: Int, receiverId: Int, content: String, chatType: String) {
        // Tạo tempId dương để gửi qua Socket (C chỉ nhận int)
        val tempId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        // Tạo localId âm để lưu vào Room (Tránh trùng với ID thật từ Server vốn là số dương)
        val localId = -tempId

        val pendingMsg = MessageEntity(
            serverId = localId, // ID ÂM
            senderId = myUserId,
            receiverId = receiverId,
            chatType = chatType,
            content = content,
            status = "sending",
            createdAt = Date()
        )
        messageDao.insertMessage(pendingMsg)

        try {
            // Gửi tempId dương xuống Native
            NativeClient.sendMessage(myUserId, receiverId, content, tempId, chatType)
        } catch (e: Exception) {
            Log.e("ChatRepo", "Send failed: ${e.message}")
            // Nếu lỗi, update trạng thái thành failed
            messageDao.updateMessageStatus(localId, "failed")
        }
    }

    // [MỚI] Xử lý khi tin nhắn đã gửi thành công (Server ACK)
    // Logic: Xóa tin tạm (ID âm) -> Chèn tin thật (ID dương)
    @Transaction
    suspend fun handleMessageSentAck(tempId: Int, serverId: Int, serverTime: Long) {
        val localId = -tempId // ID âm đã lưu lúc gửi

        // 1. Tìm tin nhắn tạm trong DB để lấy nội dung (content, receiverId,...)
        val tempMsg = messageDao.getMessageById(localId)

        if (tempMsg != null) {
            // 2. Xóa tin nhắn tạm (ID âm)
            messageDao.deleteMessageById(localId)

            // 3. Tạo tin nhắn mới với ID thật từ Server và Timestamp chuẩn
            val realMsg = tempMsg.copy(
                serverId = serverId,        // ID dương từ Server
                status = "sent",            // Cập nhật trạng thái
                createdAt = Date(serverTime), // Dùng thời gian server
                updatedAt = Date()
            )

            // 4. Lưu tin nhắn chính thức vào DB
            messageDao.insertMessage(realMsg)

            Log.d("ChatRepo", "Swapped TempID $localId to ServerID $serverId")
        } else {
            Log.w("ChatRepo", "Could not find temp message with ID $localId to swap")
        }
    }
    // [MỚI] Xử lý update status delivered
    suspend fun updateMessageStatus(serverId: Int, status: String) {
        messageDao.updateMessageStatus(serverId, status)
    }

    // [NEW] Load lịch sử cũ hơn
    fun loadHistory(targetId: Int, isGroup: Boolean, offset: Int, limit: Int) {
        // Gọi Native, dữ liệu trả về sẽ vào callback onHistoryReceived -> lưu DB -> Flow update UI
        NativeClient.getChatHistory(targetId, isGroup, offset, limit)
    }

    // [NEW] Lưu tin nhắn từ Network (Socket trả về)
    suspend fun saveMessageFromNetwork(dto: MessageDto) {
        val existingMsg = messageDao.getMessageById(dto.id) // dto.id là ServerID
        if (existingMsg != null) {
            Log.d("ChatRepo", "Tin nhắn ${dto.id} đã tồn tại. Bỏ qua insert.")
            return
        }

        val existingUser = userDao.getUserById(dto.senderId)
        if (existingUser == null) {
            // 2. Nếu chưa có -> Tạo User tạm để thỏa mãn khóa ngoại
            val placeholderUser = UserEntity(
                serverId = dto.senderId,
                email = "user_${dto.senderId}@unknown.com", // Email giả
                name = "Người dùng ${dto.senderId}",        // Tên tạm
                isOnline = false,
                status = "active",
                age = null,
                avatarUrl = null,
                createdAt = Date(),
                updatedAt = Date()
            )
            // Lưu User tạm vào trước
            userDao.insertUser(placeholderUser)
            Log.d("ChatRepo", "⚠️ Đã tạo User tạm (ID: ${dto.senderId}) để nhận tin nhắn.")
        }

        val status = when (dto.status) {
            4 -> "revoked"
            2 -> "delivered"
            3 -> "read"
            else -> "sent"
        }

        val finalContent = if (status == "revoked") "Tin nhắn đã bị thu hồi" else dto.content

        // 3. Sau khi đảm bảo User tồn tại, mới lưu tin nhắn
        val entity = MessageEntity(
            serverId = dto.id,
            senderId = dto.senderId,
            receiverId = dto.receiverId,
            chatType = dto.chatType,
            msgType = dto.type,
            content = finalContent,
            status = status,
            createdAt = Date(dto.timestamp)
        )
        messageDao.insertMessage(entity)
    }

    fun createGroup(name: String, memberIds: IntArray) {
        // Gọi xuống C -> Server xử lý -> Trả về qua callback onGroupCreated
        NativeClient.createGroup(name, memberIds)
    }

    // 2. Gọi Native để thêm thành viên
    suspend fun addMembersToGroup(groupId: Int, userIds: IntArray) {
        try {
            // 1. Gửi lệnh xuống Server (Nếu mạng lỗi sẽ throw Exception và dừng luôn)
            NativeClient.addMembersToGroup(groupId, userIds)

            // 2. Lấy tên các thành viên vừa thêm từ Local DB để tạo nội dung tin nhắn
            // Kết quả sẽ dạng: "đã thêm Nguyễn Văn A, Trần Thị B"
            val namesList = mutableListOf<String>()
            for (uid in userIds) {
                val user = userDao.getUserById(uid)
                if (user != null) {
                    namesList.add(user.name)
                } else {
                    namesList.add("User $uid") // Fallback nếu chưa có tên
                }
            }

            // Ghép danh sách tên thành chuỗi cách nhau bởi dấu phẩy
            val namesString = namesList.joinToString(", ")
            val content = "đã thêm $namesString"

            // 3. Tự chèn tin nhắn hệ thống vào Local DB để hiển thị ngay lập tức
            insertLocalSystemMessage(groupId, content)

        } catch (e: Exception) {
            Log.e("ChatRepo", "Lỗi thêm thành viên: ${e.message}")
            // Ném lỗi tiếp để ViewModel biết mà hiện thông báo lỗi (nếu cần)
            throw e
        }
    }

    // 3. Callback: Lưu thông tin nhóm vào DB khi tạo thành công
    suspend fun saveGroupFromNetwork(groupId: Int, groupName: String) {
        val group = GroupEntity(
            serverId = groupId,
            name = groupName,
            avatarUrl = null,
            notification = "active", // Mặc định bật thông báo
            createdAt = Date()
        )

        groupDao.insertGroup(group)
        val myUserId = prefs.getInt("USER_ID", -1)
        if (myUserId != -1) {
            insertLocalSystemMessage(groupId, "đã tạo nhóm", myUserId)
        }
    }

    suspend fun insertLocalSystemMessage(groupId: Int, content: String, senderId: Int? = null) {
        val myUserId = senderId ?: prefs.getInt("USER_ID", -1)
        if (myUserId == -1) return

        val sysMsg = MessageEntity(
            serverId = -System.currentTimeMillis().toInt(), // ID âm
            senderId = myUserId,
            receiverId = groupId,
            chatType = "group",
            msgType = 9, // [QUAN TRỌNG] SYSTEM TYPE
            content = content,
            status = "sent",
            createdAt = Date()
        )
        messageDao.insertMessage(sysMsg)
    }

    @Transaction
    suspend fun saveGroupListFromNetwork(groupDtos: Array<com.example.konnichat.data.remote.dto.GroupDto>) {
        if (groupDtos.isEmpty()) return

        val entities = groupDtos.map { dto ->
            GroupEntity(
                serverId = dto.id,
                name = dto.name,
                avatarUrl = dto.avatarUrl, // Server trả về URL avatar (nếu có)
                notification = "active",   // Mặc định bật thông báo
                createdAt = Date()         // Tạm lấy thời gian hiện tại
            )
        }
        groupDao.insertGroups(entities)
        Log.d("ChatRepo", "Đã lưu ${entities.size} nhóm vào Database.")
    }

    // 4. Callback: Lưu danh sách thành viên vào DB
    @Transaction
    suspend fun saveGroupMembersFromNetwork(groupId: Int, memberIds: IntArray) {
        val members = memberIds.map { memberId ->
            GroupMemberEntity(
                serverId = 0, // Local generate hoặc server trả về (ở đây tạm để 0 vì logic chưa có ID riêng cho row này)
                groupId = groupId,
                memberId = memberId,
                status = "active",
                role = "member", // Mặc định là member
                joinedAt = Date()
            )
        }
        groupDao.insertMembers(members)
        Log.d("ChatRepo", "Saved ${members.size} members for Group $groupId")
    }

    suspend fun syncGroupMembers(groupId: Int, addedBy: String, memberIds: IntArray) {
        // 1. Kiểm tra xem Group này đã có trong DB máy mình chưa
        val existingGroup = groupDao.getGroupById(groupId)

        if (existingGroup == null) {
            // 2. Nếu chưa có -> Tạo một "Group Tạm" để thỏa mãn khóa ngoại
            // Tên nhóm tạm thời là "Nhóm <ID>", sau này có thể update sau
            val placeholderGroup = GroupEntity(
                serverId = groupId,
                name = "Nhóm $groupId",
                avatarUrl = null,
                notification = "active",
                createdAt = Date()
            )
            groupDao.insertGroup(placeholderGroup)
            Log.d("ChatRepo", "⚠️ Đã tạo Group tạm (ID: $groupId) để tránh lỗi crash.")
            // Gọi server lấy danh sách nhóm để cập nhật lại Tên thật và Avatar cho nhóm tạm
            try {
                // Lấy 100 nhóm đầu tiên để sync lại thông tin nhóm vừa vào
                NativeClient.getGroupList(0, 100)
            } catch (e: Exception) {
                Log.e("ChatRepo", "Lỗi khi request cập nhật info nhóm: ${e.message}")
            }
        }

        memberIds.forEach { memberId ->
            val existingUser = userDao.getUserById(memberId)

            if (existingUser == null) {
                // Tạo User tạm với email giả định dạng đặc biệt để không trùng
                val placeholderUser = UserEntity(
                    serverId = memberId,
                    email = "temp_${memberId}@placeholder.konnichat", // Email giả unique
                    name = "Người dùng $memberId", // Tên tạm
                    age = null,
                    status = "active",
                    isOnline = false, // Mặc định offline
                    avatarUrl = null,
                    createdAt = Date(),
                    updatedAt = Date()
                )
                // Insert vào DB (Nếu sau này có data thật, lệnh INSERT REPLACE sẽ tự ghi đè)
                userDao.insertUser(placeholderUser)
                Log.d("ChatRepo", "⚠️ Đã tạo User tạm (ID: $memberId) để thỏa mãn khóa ngoại.")
            }
        }

        // 3. Sau khi đảm bảo Group đã tồn tại, mới lưu danh sách thành viên
        saveGroupMembersFromNetwork(groupId, memberIds)

        // (Optional) Tạo tin nhắn hệ thống báo "A đã thêm B vào nhóm" nếu muốn
    }

    suspend fun getGroupMemberIds(groupId: Int): List<Int> {
        // Lấy list entity từ DAO và map sang list Int (memberId)
        return groupDao.getMembersByGroupId(groupId).map { it.memberId }
    }

    suspend fun runDataFixer() {
        try {
            messageDao.fixLegacyMessages()
            Log.d("ChatRepo", "Đã chạy lệnh sửa lỗi dữ liệu cũ (Legacy Fix)")
        } catch (e: Exception) {
            Log.e("ChatRepo", "Lỗi khi chạy fix data: ${e.message}")
        }
    }

    suspend fun getGroupRole(groupId: Int, userId: Int): String? {
        return groupDao.getMemberRole(groupId, userId)
    }

    suspend fun leaveGroup(groupId: Int) {
        // 1. Gửi lệnh lên Server (Nếu lỗi mạng sẽ throw Exception)
        NativeClient.leaveGroup(groupId)
        // 2. Nếu thành công -> Xóa dữ liệu local
        groupDao.deleteGroup(groupId)
        Log.d("ChatRepo", "Đã rời và xóa nhóm $groupId")
        insertLocalSystemMessage(groupId, "đã rời nhóm")
    }

    suspend fun dissolveGroup(groupId: Int) {
        // 1. Gửi lệnh giải tán
        NativeClient.dissolveGroup(groupId)
        // 2. Xóa dữ liệu local
        groupDao.deleteGroup(groupId)
        Log.d("ChatRepo", "Đã giải tán nhóm $groupId")
    }

    suspend fun handleMemberLeft(groupId: Int, memberId: Int, memberName: String) {
        // 1. Xóa thành viên khỏi bảng group_members
        groupDao.deleteMember(groupId, memberId)

        // 2. Chèn tin nhắn hệ thống: "Nguyễn Văn A đã rời nhóm"
        // Server ID cho tin hệ thống tự sinh này có thể dùng số âm hoặc hash để tránh trùng
//        val systemMsg = MessageEntity(
//            serverId = -System.currentTimeMillis().toInt(), // ID tạm
//            senderId = memberId, // Người rời là người gửi tin này
//            receiverId = groupId,
//            chatType = "group",
//            msgType = 9, // TYPE_SYSTEM
//            content = "đã rời nhóm",
//            status = "sent",
//            createdAt = Date()
//        )
//        messageDao.insertMessage(systemMsg)
    }

    // [THÊM MỚI] Xử lý khi nhóm bị giải tán
    suspend fun handleGroupDissolved(groupId: Int) {
        groupDao.deleteMembersByGroupId(groupId)
        // Xóa toàn bộ nhóm khỏi DB
        groupDao.deleteGroup(groupId)
        Log.d("ChatRepo", "Nhóm $groupId đã bị giải tán bởi Admin, đã xóa khỏi máy.")
    }

    @Transaction
    suspend fun saveGroupMembersList(groupId: Int, members: Array<GroupMemberDto>) {
        if (members.isEmpty()) return

        // 1. Lưu User vào bảng Users (Cẩn thận để không ghi đè trạng thái bạn bè)
        members.forEach { dto ->
            // B1: Tạo entity mặc định là Stranger (relationType = 0)
            val userEntity = UserEntity(
                serverId = dto.userId,
                email = dto.email,
                name = dto.name,
                isOnline = dto.isOnline,
                status = "active",
                age = null,
                avatarUrl = null,
                relationType = 0, // Mặc định là người lạ
                createdAt = Date(),
                updatedAt = Date()
            )

            // B2: Thử Insert (Nếu chưa có thì thêm mới là Stranger)
            val rowId = userDao.insertUserIgnore(userEntity)

            // B3: Nếu Insert thất bại (rowId == -1) nghĩa là User đã có trong DB (có thể là Bạn hoặc Người lạ cũ)
            // Ta chỉ cập nhật thông tin mới nhất (Tên, Online...) mà KHÔNG đổi relationType
            if (rowId == -1L) {
                userDao.updateAndVerifyUser(
                    id = dto.userId,
                    name = dto.name,
                    email = dto.email,
                    isOnline = dto.isOnline
                )
            }
        }

        // 2. Update bảng GroupMembers (Giữ nguyên code cũ)
        val memberEntities = members.map { dto ->
            GroupMemberEntity(
                serverId = 0,
                groupId = groupId,
                memberId = dto.userId,
                role = dto.role,
                status = "active",
                joinedAt = Date()
            )
        }
        groupDao.insertMembers(memberEntities)
        Log.d("ChatRepo", "Đã lưu ${members.size} thành viên cho nhóm $groupId")
    }

    fun getGroupMembersFlow(groupId: Int): Flow<List<GroupMemberWithUser>> {
        return groupDao.getMembersWithUserInfo(groupId)
    }

    suspend fun getGroupInfo(groupId: Int): GroupEntity? {
        return groupDao.getGroupById(groupId)
    }

    suspend fun fetchGroupMembers(groupId: Int) {
        try {
            // Lấy 100 thành viên đầu tiên (hoặc nhiều hơn tùy logic phân trang của bạn)
            NativeClient.getGroupMembers(groupId, 0, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun kickMember(groupId: Int, targetId: Int) {
        try {
            // 1. Lấy tên người bị kick trước để làm nội dung tin nhắn
            val targetUser = userDao.getUserById(targetId)
            val targetName = targetUser?.name ?: "thành viên"

            // 2. Gửi lệnh lên Server (Nếu lỗi sẽ văng Exception và dừng tại đây)
            NativeClient.kickMember(groupId, targetId)

            // 3. Nếu thành công -> Tự cập nhật Local DB ngay lập tức

            // A. Xóa thành viên khỏi danh sách nhóm trong máy mình
            groupDao.deleteMember(groupId, targetId)

            // B. Chèn tin nhắn hệ thống: "đã mời Nguyễn Văn A ra khỏi nhóm"
            val content = "đã mời $targetName ra khỏi nhóm"
            insertLocalSystemMessage(groupId, content)

            Log.d("ChatRepo", "Đã kick $targetName và cập nhật local.")

        } catch (e: Exception) {
            Log.e("ChatRepo", "Lỗi kick member: ${e.message}")
            throw e
        }
    }

    // [THÊM MỚI] Xử lý sự kiện Member Removed từ Server
// [SỬA ĐỔI] Xử lý sự kiện Member Removed từ Server
    @Transaction
    suspend fun handleMemberRemoved(
        groupId: Int,
        memberId: Int,
        memberName: String,
        adminId: Int,
        adminName: String,
        myUserId: Int // [THÊM THAM SỐ] Cần biết ID của mình để so sánh
    ) {
        if (memberId == myUserId) {
            // --- TRƯỜNG HỢP 1: CHÍNH TÔI BỊ KICK ---
            Log.d("ChatRepo", "Tôi đã bị kick khỏi nhóm $groupId. Đang xóa dữ liệu...")

            // 1. Xóa thông tin nhóm
            groupDao.deleteGroup(groupId) // Hàm này xóa trong bảng `groups`

            // 2. Xóa thành viên (Logic deleteGroup có thể chưa cascade hết nếu thiết kế DB lỏng)
            // Tốt nhất xóa tay cho sạch bảng group_members liên quan đến nhóm này
            // (Tuy nhiên groupDao.deleteGroup chỉ xóa dòng trong groups,
            // các bảng khác nếu có ForeignKey CASCADE thì tự bay, nếu không thì phải xóa tay).
            // Giả sử bảng group_members có FK nối với groups -> CASCADE -> Tự xóa.

            // 3. Xóa tin nhắn của nhóm này
            messageDao.deleteGroupMessages(groupId)

            Log.d("ChatRepo", "Đã xóa sạch dữ liệu nhóm $groupId khỏi máy.")

        } else {
            // --- TRƯỜNG HỢP 2: NGƯỜI KHÁC BỊ KICK ---

            // 1. Xóa thành viên đó khỏi DB Local
            groupDao.deleteMember(groupId, memberId)

            if (adminId == myUserId) {
                insertLocalSystemMessage(groupId, "đã mời $memberName ra khỏi nhóm")
            }

            // 2. Tạo tin nhắn hệ thống báo cho mình biết
//            val content = "đã mời $memberName ra khỏi nhóm"
//            val systemMsg = MessageEntity(
//                serverId = -System.currentTimeMillis().toInt(),
//                senderId = adminId,
//                receiverId = groupId,
//                chatType = "group",
//                msgType = 9, // TYPE_SYSTEM
//                content = content,
//                status = "sent",
//                createdAt = Date()
//            )
//            messageDao.insertMessage(systemMsg)
            Log.d("ChatRepo", "Đã xóa member $memberName khỏi nhóm $groupId (Local)")
        }
    }
    fun checkGroupMembership(groupId: Int, userId: Int): Flow<Boolean> {
        return groupDao.isUserInGroupFlow(groupId, userId)
    }

    fun isGroupMuted(groupId: Int): Boolean {
        // Sử dụng key định dạng: MUTE_GROUP_12
        return prefs.getBoolean("MUTE_GROUP_$groupId", false)
    }

    fun setGroupMute(groupId: Int, isMuted: Boolean) {
        prefs.edit().putBoolean("MUTE_GROUP_$groupId", isMuted).apply()
    }

    // 1. Gửi lệnh thu hồi (Gọi từ UI -> ViewModel -> Repo -> Native)
    suspend fun recallMessage(messageId: Int) {
        // messageId ở đây phải là ServerID
        try {
            NativeClient.recallMessage(messageId)
        } catch (e: Exception) {
            Log.e("ChatRepo", "Lỗi gửi lệnh recall: ${e.message}")
            throw e
        }
    }

    // 2. Xử lý khi nhận tín hiệu update từ Server (Callback -> Repo -> DAO)
    suspend fun handleMessageRevoked(messageId: Int) {
        // Cập nhật DB Local: đổi status -> revoked, content -> "Tin nhắn đã bị thu hồi"
        messageDao.markMessageAsRevoked(messageId)
        Log.d("ChatRepo", "Đã đánh dấu tin nhắn $messageId là REVOKED trong DB")
    }
}