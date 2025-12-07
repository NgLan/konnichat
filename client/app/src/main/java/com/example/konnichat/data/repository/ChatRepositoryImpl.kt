package com.example.konnichat.data.repository

import com.example.konnichat.data.mapper.MessageMapper
import com.example.konnichat.data.source.local.dao.MessageDao
import com.example.konnichat.domain.model.Message
import com.example.konnichat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val dao: MessageDao,
    private val mapper: MessageMapper
) : ChatRepository {

    override fun getMessages(myUserId: Int, friendId: Int): Flow<List<Message>> {
        // Flow<List<MessageEntity>> -> Flow<List<Message>>
        return dao.getConversation(myUserId, friendId).map { entityList ->
            entityList.map { mapper.mapToDomain(it) }
        }
    }

    override suspend fun sendMessage(message: Message) {
        val entity = mapper.mapToEntity(message)
        dao.insertMessage(entity)
        // TODO: Gọi NativeClient.sendMessage() tại đây nếu muốn online
    }
}
