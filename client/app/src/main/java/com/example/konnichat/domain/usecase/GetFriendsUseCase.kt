package com.example.konnichat.domain.usecase

import com.example.konnichat.domain.model.User
import com.example.konnichat.domain.repository.FriendRepository

class GetFriendsUseCase(private val repository: FriendRepository) {
    suspend operator fun invoke(myUserId: Int): List<User> {
        return repository.getListFriends(myUserId)
    }
}
