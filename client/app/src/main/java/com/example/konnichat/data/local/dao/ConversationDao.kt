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
     * PHẦN 1: Lấy User (Private Chat)
     * - Join bảng users với tin nhắn mới nhất.
     *
     * PHẦN 2: Lấy Group (Group Chat)
     * - Join bảng groups với tin nhắn mới nhất (receiver_id = group_id, chat_type = 'group').
     *
     * UNION ALL: Gộp 2 kết quả lại.
     * ORDER BY: Sắp xếp theo thời gian tin nhắn mới nhất giảm dần.
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

            UNION ALL

            -- PHẦN 2: GROUP
            SELECT 
                g.server_id AS id,
                g.name AS name,
                g.avatar_url AS avatar,
                0 AS isOnline, -- Group mặc định là 0 (coi như offline)
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
            -- Có thể thêm điều kiện kiểm tra User có trong nhóm không nếu bảng group_members đầy đủ
            -- Hiện tại cứ lấy tất cả group có trong DB local
        )
        ORDER BY lastMessageTime DESC
    """)
    fun getConversationList(myUserId: Int): Flow<List<ConversationItem>>
}