package com.example.konnichat.data.repository

import com.example.konnichat.data.mapper.UserMapper
import com.example.konnichat.data.source.local.dao.FriendDao
import com.example.konnichat.domain.model.User
import com.example.konnichat.domain.repository.FriendRepository

class FriendRepositoryImpl(
    private val dao: FriendDao,
    private val mapper: UserMapper
) : FriendRepository {

    override suspend fun getListFriends(myUserId: Int): List<User> {
        val entities = dao.getFriendListDetails(myUserId)
        return entities.map { mapper.mapToDomain(it) }
    }
}
