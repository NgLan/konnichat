package com.example.konnichat

import android.app.Application
import androidx.room.Room
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.remote.DataSyncManager
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.data.repository.ChatRepository

class App : Application() {

    // Database dùng chung toàn App (Singleton pattern đơn giản)

    val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "konnichat-db"
        ).build()
    }

    lateinit var syncManager: DataSyncManager
        private set

    val userRepository by lazy { UserRepository(db.userDao()) }
    val chatRepository by lazy { ChatRepository(db.conversationDao()) }

    override fun onCreate() {
        super.onCreate()

        // 2. Tạo bộ quản lý đồng bộ
        syncManager = DataSyncManager(db)
        // 3. Bắt đầu lắng nghe sự kiện từ Native (C)
        // Đây chính là hành động "Gắn hòm thư" mà chúng ta đã bàn
        NativeClient.startListening(syncManager)
    }
}