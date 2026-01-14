package com.example.konnichat

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.prefs.SessionManager
import com.example.konnichat.data.remote.DataSyncManager
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.data.repository.AuthRepository
import com.example.konnichat.utils.NotificationHelper
import com.example.konnichat.data.remote.NativeEventListenerImpl

/**
 * Lớp Application: Đóng vai trò là Container chứa các Singleton (Service Locator Pattern).
 */
class App : Application() {

    // Database dùng chung toàn App
    // lazy: chỉ khởi tạo khi lần đầu tiên được gọi đến
    val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "konnichat-db"
        )
            .fallbackToDestructiveMigration(true) // Reset DB nếu thay đổi version (tránh crash lúc dev)
            .build()
    }

    lateinit var syncManager: DataSyncManager
        private set

    val sessionManager by lazy {
        SessionManager(applicationContext)
    }

    val userRepository by lazy {
        UserRepository(db.userDao(), db.messageDao(), sessionManager)
    }

    val chatRepository by lazy {
        ChatRepository(
            db.conversationDao(),
            db.messageDao(),
            db.groupDao(),
            db.userDao(),
            sessionManager
        )
    }

    val authRepository by lazy {
        AuthRepository(db.userDao(), db, sessionManager)
    }

    override fun onCreate() {
        super.onCreate()

        syncManager = DataSyncManager(db)

        // Cấu hình Native Listener
        NativeEventListenerImpl.context = applicationContext
        NativeEventListenerImpl.userRepository = userRepository
        NativeEventListenerImpl.chatRepository = chatRepository
        NativeEventListenerImpl.authRepository = authRepository

        // Khởi tạo kênh thông báo
        NotificationHelper.createNotificationChannel(this)
    }
}