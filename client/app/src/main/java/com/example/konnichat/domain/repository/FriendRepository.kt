package com.example.konnichat.domain.repository

import com.example.konnichat.domain.model.User

interface FriendRepository {
    suspend fun getListFriends(myUserId: Int): List<User>
}
