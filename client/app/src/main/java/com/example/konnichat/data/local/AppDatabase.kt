package com.example.konnichat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.konnichat.data.local.converter.DateConverter
import com.example.konnichat.data.local.dao.ConversationDao
import com.example.konnichat.data.local.dao.MessageDao
import com.example.konnichat.data.local.dao.UserDao
import com.example.konnichat.data.local.dao.GroupDao
import com.example.konnichat.data.local.entity.FriendEntity
import com.example.konnichat.data.local.entity.FriendRequestEntity
import com.example.konnichat.data.local.entity.GroupEntity
import com.example.konnichat.data.local.entity.GroupMemberEntity
import com.example.konnichat.data.local.entity.IconEntity
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.entity.ReactionEntity
import com.example.konnichat.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        FriendRequestEntity::class,
        FriendEntity::class,
        MessageEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ReactionEntity::class,
        IconEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao

    abstract fun conversationDao(): ConversationDao

    abstract fun groupDao(): GroupDao
}
