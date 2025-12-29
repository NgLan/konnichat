package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.konnichat.data.local.model.ConversationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // Lấy danh sách bạn bè VÀ tin nhắn cuối cùng của họ
    // Logic: Duyệt bảng users, với mỗi user thì tìm trong bảng messages lấy ra 1 tin mới nhất
    @Query("""
        SELECT 
            u.server_id AS friendId,
            u.name AS friendName,
            u.avatar_url AS avatar,
            u.is_online AS isOnline,
            m.content AS lastMessage,
            m.created_at AS lastMessageTime
        FROM users u
        LEFT JOIN messages m ON m.server_id = (
            SELECT server_id FROM messages 
            WHERE (sender_id = u.server_id OR receiver_id = u.server_id)
            ORDER BY created_at DESC 
            LIMIT 1
        )
        WHERE u.server_id != :myUserId
    """)

    fun getConversationList(myUserId: Int): Flow<List<ConversationItem>>
}