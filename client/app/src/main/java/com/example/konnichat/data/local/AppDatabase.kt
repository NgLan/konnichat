package com.example.konnichat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.konnichat.data.local.converter.DateConverter
import com.example.konnichat.data.local.dao.*
import com.example.konnichat.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        FriendRequestEntity::class,
        FriendEntity::class,
        MessageEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ReactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
}
