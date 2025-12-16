package com.example.konnichat

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KonniChatKotlin"

        // Load thư viện native 'konnichat-client' khi class được khởi tạo
        init {
            System.loadLibrary("konnichat")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Không cần setContentView nếu chưa có giao diện,
        // nhưng nên giữ để app không bị trắng trơn (nếu có layout)
        // setContentView(R.layout.activity_main)

        // 1. Khởi tạo JNI (Lưu tham chiếu Activity để C gọi ngược lại)
        initNative()

        // 2. Kết nối Server trong Thread riêng (Bắt buộc để tránh crash)
        Thread {
            Log.d(TAG, "Đang kết nối đến server 10.0.2.2:8080...")
            // 10.0.2.2 là localhost của máy tính khi chạy trên Emulator
            val isConnected = connectServer("10.0.2.2", 8080)

            if (isConnected) {
                Log.i(TAG, "Kết nối thành công! Bắt đầu chạy kịch bản test...")
                runTestScenario()
            } else {
                Log.e(TAG, "Kết nối thất bại! Hãy kiểm tra Server và quyền Internet.")
            }
        }.start()
    }

    /**
     * Kịch bản Test các chức năng (Chạy trên background thread)
     */
    private fun runTestScenario() {
        try {
            // Bước 1: Đăng ký
            Log.d(TAG, ">>> TEST: Đang đăng ký User...")
            registerUser("KotlinUser", "kotlin@test.com", "123456")
            Thread.sleep(1000) // Nghỉ 1s để chờ server phản hồi

            // Bước 2: Đăng nhập
            Log.d(TAG, ">>> TEST: Đang đăng nhập...")
            loginUser("kotlin@test.com", "123456")
            Thread.sleep(1000)

            // Bước 3: Lấy danh sách bạn bè
            Log.d(TAG, ">>> TEST: Lấy danh sách bạn bè...")
            getFriendList()
            Thread.sleep(1000)

            // Bước 4: Tìm kiếm user tên 'an'
            Log.d(TAG, ">>> TEST: Tìm kiếm user 'an'...")
            searchUser("an")
            Thread.sleep(1000)

            // Bước 5: Gửi tin nhắn cho User ID 1
            Log.d(TAG, ">>> TEST: Gửi tin nhắn cho User ID 1...")
            sendMessage(1, "Xin chào từ Kotlin Native!")

            // Bước 6: Lấy tin nhắn offline
            Log.d(TAG, ">>> TEST: Lấy tin nhắn offline...")
            fetchOfflineMessages()

        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * JNI Callback: Hàm này được gọi từ C (native-lib.c)
     * Lưu ý: Tên hàm và tham số phải khớp với code C gọi: GetMethodID(..., "onNativeMessage", ...)
     */
    fun onNativeMessage(message: String) {
        Log.i("KonniChatNative", message)
    }

    // --- KHAI BÁO CÁC HÀM NATIVE (EXTERNAL) ---
    external fun initNative()
    external fun connectServer(ip: String, port: Int): Boolean
    external fun registerUser(name: String, email: String, password: String)
    external fun loginUser(email: String, password: String)
    external fun getFriendList()
    external fun searchUser(keyword: String)
    external fun sendMessage(receiverId: Int, content: String)
    external fun fetchOfflineMessages()
}
