// File: client/app/src/main/java/com/example/konnichat/App.kt
package com.example.konnichat

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.remote.DataSyncManager
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.data.repository.AuthRepository
import com.example.konnichat.utils.NotificationHelper
import com.example.konnichat.data.remote.NativeEventListenerImpl

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

    // SỬA Ở ĐÂY: Truyền SharedPreferences vào UserRepository
    val userRepository by lazy {
        // Tên file "konnichat_prefs" phải KHỚP với tên file bên LoginActivity
        val prefs = applicationContext.getSharedPreferences("konnichat_prefs", Context.MODE_PRIVATE)
        UserRepository(db.userDao(),db.messageDao(), prefs)
    }

    val chatRepository by lazy {
        ChatRepository(
            db.conversationDao(),
            db.messageDao(),
            db.groupDao(),
            db.userDao()
        )
    }

    val authRepository by lazy {
        val prefs = applicationContext.getSharedPreferences("konnichat_prefs", Context.MODE_PRIVATE)
        AuthRepository(db.userDao(), db, prefs)
    }

    override fun onCreate() {
        super.onCreate()

        // 2. Tạo bộ quản lý đồng bộ
        syncManager = DataSyncManager(db)

        NativeEventListenerImpl.context = applicationContext
        NativeEventListenerImpl.userRepository = userRepository
        NativeEventListenerImpl.chatRepository = chatRepository

        NativeEventListenerImpl.authRepository = authRepository

        NotificationHelper.createNotificationChannel(this)
        // 3. Bắt đầu lắng nghe sự kiện từ Native (C)
//        NativeClient.startListening(syncManager)
    }
}