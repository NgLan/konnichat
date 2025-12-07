package com.example.konnichat.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.konnichat.data.source.local.dao.*
import com.example.konnichat.data.source.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        FriendRequestEntity::class,
        FriendEntity::class,
        MessageEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupMessageEntity::class,
        IconEntity::class,
        ReactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // Khai báo các DAO (Abstract method)
    abstract fun userDao(): UserDao
    abstract fun friendDao(): FriendDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun reactionDao(): ReactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "konnichat_database" // Tên file DB trong điện thoại
                )
                    .fallbackToDestructiveMigration() // Xóa DB cũ nếu đổi version (Dev mode)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}