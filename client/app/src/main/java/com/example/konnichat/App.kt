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
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "konnichat-db")
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                // Dùng onOpen thay vì onCreate để đảm bảo chạy mỗi lần mở app
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Kiểm tra nếu bảng icons rỗng thì mới insert
                    val cursor = db.query("SELECT count(*) FROM icons")
                    cursor.moveToFirst()
                    val count = cursor.getInt(0)
                    cursor.close()

                    if (count == 0) {
                        val now = System.currentTimeMillis()
                        // Dùng transaction cho an toàn
                        db.beginTransaction()
                        try {
                            db.execSQL("INSERT OR REPLACE INTO icons (server_id, code, image_url, created_at) VALUES (1, 'like', '', $now)")
                            db.execSQL("INSERT OR REPLACE INTO icons (server_id, code, image_url, created_at) VALUES (2, 'love', '', $now)")
                            db.execSQL("INSERT OR REPLACE INTO icons (server_id, code, image_url, created_at) VALUES (3, 'haha', '', $now)")
                            db.execSQL("INSERT OR REPLACE INTO icons (server_id, code, image_url, created_at) VALUES (4, 'wow', '', $now)")
                            db.execSQL("INSERT OR REPLACE INTO icons (server_id, code, image_url, created_at) VALUES (5, 'sad', '', $now)")
                            db.execSQL("INSERT OR REPLACE INTO icons (server_id, code, image_url, created_at) VALUES (6, 'angry', '', $now)")
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                }
            })
            .build()
    }
    lateinit var syncManager: DataSyncManager
        private set

    private val sharedPrefs by lazy {
        applicationContext.getSharedPreferences("konnichat_prefs", Context.MODE_PRIVATE)
    }
    // SỬA Ở ĐÂY: Truyền SharedPreferences vào UserRepository
    val userRepository by lazy {
        // Tên file "konnichat_prefs" phải KHỚP với tên file bên LoginActivity
        UserRepository(db.userDao(),db.messageDao(), sharedPrefs)
    }

    val chatRepository by lazy {
        ChatRepository(
            db.conversationDao(),
            db.messageDao(),
            db.groupDao(),
            db.userDao(),
            db.reactionDao(),
            sharedPrefs
        )
    }

    val authRepository by lazy {
        AuthRepository(db.userDao(), db, sharedPrefs)
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