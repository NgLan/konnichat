package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.konnichat.data.local.model.ConversationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    /**
     * Câu lệnh SQL phức tạp để lấy danh sách hội thoại hỗn hợp (User + Group).
     *
     * [SỬA ĐỔI]: Thêm điều kiện lọc ở PHẦN 1 để ẩn người lạ chưa có tin nhắn.
     */
    @Query("""
        SELECT * FROM (
            -- PHẦN 1: USER (PRIVATE)
            SELECT 
                u.server_id AS id,
                u.name AS name,
                u.avatar_url AS avatar,
                u.is_online AS isOnline,
                'private' AS chatType,
                m.content AS lastMessage,
                m.created_at AS lastMessageTime
            FROM users u
            LEFT JOIN messages m ON m.server_id = (
                SELECT server_id FROM messages 
                WHERE (sender_id = u.server_id OR receiver_id = u.server_id)
                AND chat_type = 'private'
                ORDER BY created_at DESC 
                LIMIT 1
            )
            WHERE u.server_id != :myUserId
            AND (
                u.relation_type = 1          -- Điều kiện 1: Là BẠN BÈ thì luôn hiện (kể cả chưa chat)
                OR 
                m.server_id IS NOT NULL      -- Điều kiện 2: Nếu là NGƯỜI LẠ (type=0) thì phải có tin nhắn mới hiện
            )

            UNION ALL

            -- PHẦN 2: GROUP (Group Chat)
            SELECT 
                g.server_id AS id,
                g.name AS name,
                g.avatar_url AS avatar,
                0 AS isOnline, 
                'group' AS chatType,
                gm.content AS lastMessage,
                gm.created_at AS lastMessageTime
            FROM `groups` g
            LEFT JOIN messages gm ON gm.server_id = (
                SELECT server_id FROM messages 
                WHERE receiver_id = g.server_id 
                AND chat_type = 'group'
                ORDER BY created_at DESC 
                LIMIT 1
            )
        )
        ORDER BY lastMessageTime DESC
    """)
    fun getConversationList(myUserId: Int): Flow<List<ConversationItem>>
}