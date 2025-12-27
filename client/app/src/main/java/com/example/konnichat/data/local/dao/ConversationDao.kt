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
            (SELECT content FROM messages m 
             WHERE (m.sender_id = u.server_id OR m.receiver_id = u.server_id) 
             ORDER BY m.created_at DESC LIMIT 1) AS lastMessage,
            (SELECT created_at FROM messages m 
             WHERE (m.sender_id = u.server_id OR m.receiver_id = u.server_id) 
             ORDER BY m.created_at DESC LIMIT 1) AS lastMessageTime
        FROM users u
        ORDER BY lastMessageTime DESC
    """)
    fun getConversationList(): Flow<List<ConversationItem>>
}