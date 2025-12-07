package com.example.konnichat.di

import android.content.Context
import com.example.konnichat.data.mapper.MessageMapper
import com.example.konnichat.data.mapper.UserMapper
import com.example.konnichat.data.repository.ChatRepositoryImpl
import com.example.konnichat.data.repository.FriendRepositoryImpl
import com.example.konnichat.data.source.local.AppDatabase
import com.example.konnichat.domain.repository.ChatRepository
import com.example.konnichat.domain.repository.FriendRepository
import com.example.konnichat.domain.usecase.GetFriendsUseCase
import com.example.konnichat.domain.usecase.GetMessagesUseCase
import com.example.konnichat.domain.usecase.ReceiveMessageLoopUseCase
import com.example.konnichat.domain.usecase.SendMessageUseCase
import com.example.konnichat.domain.usecase.SyncChatHistoryUseCase
import com.example.konnichat.domain.usecase.SyncOfflineMessagesUseCase

object Injection {
    private fun provideDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    // Repository Friend
    fun provideFriendRepository(context: Context): FriendRepository {
        val database = provideDatabase(context)
        return FriendRepositoryImpl(database.friendDao(), database.userDao(), UserMapper())
    }

    // Repository Chat
    fun provideChatRepository(context: Context): ChatRepository {
        val database = provideDatabase(context)
        return ChatRepositoryImpl(database.messageDao(), MessageMapper())
    }

    // --- Provide UseCases ---

    fun provideGetFriendsUseCase(context: Context): GetFriendsUseCase {
        return GetFriendsUseCase(provideFriendRepository(context))
    }

    // Gom nhóm UseCase cho màn hình Chat
    fun provideChatUseCases(context: Context): ChatUseCases {
        val repo = provideChatRepository(context)
        return ChatUseCases(
            getMessages = GetMessagesUseCase(repo),
            sendMessage = SendMessageUseCase(repo),
            receiveMessageLoop = ReceiveMessageLoopUseCase(repo),
            syncHistory = SyncChatHistoryUseCase(repo)
        )
    }

    fun provideSyncOfflineMessagesUseCase(context: Context): SyncOfflineMessagesUseCase {
        return SyncOfflineMessagesUseCase(provideChatRepository(context))
    }
}

// Wrapper class
data class ChatUseCases(
    val getMessages: GetMessagesUseCase,
    val sendMessage: SendMessageUseCase,
    val receiveMessageLoop: ReceiveMessageLoopUseCase,
    val syncHistory: SyncChatHistoryUseCase
)
