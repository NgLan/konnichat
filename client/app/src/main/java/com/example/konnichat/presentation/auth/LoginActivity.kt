package com.example.konnichat.presentation.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.konnichat.presentation.home.HomeActivity
import com.example.konnichat.NativeClient
import com.example.konnichat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Đảm bảo tên file layout đúng với file XML của bạn
        setContentView(R.layout.activity_login)

        // 1. Ánh xạ View theo ID mới trong XML
        val etEmail = findViewById<EditText>(R.id.etLoginEmail)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword) // ID mới
        val btnLogin = findViewById<Button>(R.id.btnLogin)            // ID mới
        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)  // ID mới

        // 2. Tự động kết nối Server khi mở màn hình này
        // (Chạy ngầm để không đơ UI)
        CoroutineScope(Dispatchers.IO).launch {
            val status = NativeClient.connectToServer()
            // Có thể log status ra Logcat để kiểm tra nếu cần
        }

        // 3. Xử lý sự kiện bấm nút "Đăng ký ngay"
        tvSignUpLink.setOnClickListener {
            // Chuyển sang màn hình Đăng ký
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 4. Xử lý sự kiện bấm nút "Đăng Nhập"
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // Validate cơ bản
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Email và Mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Gọi xuống tầng C (Native) để xử lý
            btnLogin.isEnabled = false // Khóa nút để tránh bấm nhiều lần
            btnLogin.text = "Đang xử lý..."

            CoroutineScope(Dispatchers.IO).launch {
                // 1. Gọi Login lấy Full Info
                val userDto = NativeClient.loginUser(email, password)

                // Cập nhật UI ở luồng chính
                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Đăng Nhập"

                    if (userDto != null && userDto.id > 0) {
                        // 2. LƯU DỮ LIỆU THẬT VÀO ROOM
                        // Cần inject UserDao vào đây, hoặc gọi thông qua Repository
                        // Để nhanh, ta gọi trực tiếp DB:
                        val db = com.example.konnichat.data.source.local.AppDatabase.getDatabase(applicationContext)
                        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

                        val myUserEntity = com.example.konnichat.data.source.local.entity.UserEntity(
                            id = userDto.id,
                            email = userDto.email, // Dữ liệu thật từ Server
                            name = userDto.name,   // Dữ liệu thật từ Server
                            password = "", // Không lưu pass plaintext
                            age = 0,
                            status = "active",
                            isOnline = "online",
                            avatarUrl = null,
                            createdAt = currentTime,
                            updatedAt = currentTime
                        )

                        // Chạy trên IO
                        withContext(Dispatchers.IO) {
                            db.userDao().insertUser(myUserEntity)
                        }

                        Toast.makeText(
                            this@LoginActivity,
                            "Đăng nhập thành công!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // --- TODO: CHUYỂN SANG MÀN HÌNH CHÍNH (HOME) ---
                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                        intent.putExtra("USER_ID", userDto.id) // Truyền ID sang để dùng
                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Sai Email hoặc Mật khẩu!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}