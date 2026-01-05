package com.example.konnichat.data.repository

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
import kotlinx.coroutines.flow.Flow
import java.util.Date
import androidx.room.Transaction
import com.example.konnichat.data.local.model.MessageWithSender
import com.example.konnichat.data.local.dao.UserDao // [THÊM] Import UserDao
import com.example.konnichat.data.local.entity.UserEntity


class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val userDao: UserDao
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
    fun loadHistory(targetId: Int, offset: Int, limit: Int) {
        // Gọi Native, dữ liệu trả về sẽ vào callback onHistoryReceived -> lưu DB -> Flow update UI
        NativeClient.getChatHistory(targetId, false, offset, limit)
    }

    // [NEW] Lưu tin nhắn từ Network (Socket trả về)
    suspend fun saveMessageFromNetwork(dto: MessageDto) {
        val entity = MessageEntity(
            serverId = dto.id,
            senderId = dto.senderId,
            receiverId = dto.receiverId,
            chatType = dto.chatType,
            content = dto.content,
            status = "sent", // Tin từ server về mặc định là sent
            createdAt = Date(dto.timestamp)
        )
        messageDao.insertMessage(entity)
    }

    fun createGroup(name: String, memberIds: IntArray) {
        // Gọi xuống C -> Server xử lý -> Trả về qua callback onGroupCreated
        NativeClient.createGroup(name, memberIds)
    }

    // 2. Gọi Native để thêm thành viên
    fun addMembersToGroup(groupId: Int, userIds: IntArray) {
        NativeClient.addMembersToGroup(groupId, userIds)
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
        val systemMsg = MessageEntity(
            serverId = -System.currentTimeMillis().toInt(), // ID tạm
            senderId = memberId, // Người rời là người gửi tin này
            receiverId = groupId,
            chatType = "group",
            msgType = 9, // TYPE_SYSTEM
            content = "đã rời nhóm",
            status = "sent",
            createdAt = Date()
        )
        messageDao.insertMessage(systemMsg)
    }

    // [THÊM MỚI] Xử lý khi nhóm bị giải tán
    suspend fun handleGroupDissolved(groupId: Int) {
        // Xóa toàn bộ nhóm khỏi DB
        groupDao.deleteGroup(groupId)
        Log.d("ChatRepo", "Nhóm $groupId đã bị giải tán bởi Admin, đã xóa khỏi máy.")
    }
}