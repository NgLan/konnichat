package com.example.konnichat

import android.app.Application
import androidx.room.Room
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.remote.DataSyncManager
import com.example.konnichat.data.remote.NativeClient

class App : Application() {

    // Database dùng chung toàn App (Singleton pattern đơn giản)
    lateinit var database: AppDatabase
        private set

    lateinit var syncManager: DataSyncManager
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Khởi tạo Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "konnichat-db"
        ).build()

        // 2. Tạo bộ quản lý đồng bộ
        syncManager = DataSyncManager(database)
        // 3. Bắt đầu lắng nghe sự kiện từ Native (C)
        // Đây chính là hành động "Gắn hòm thư" mà chúng ta đã bàn
        NativeClient.startListening(syncManager)
    }
}