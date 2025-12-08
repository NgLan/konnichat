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
import com.example.konnichat.NetworkDiscovery
import com.example.konnichat.R
import com.example.konnichat.data.dto.NativeUserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private var detectedIp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Đảm bảo tên file layout đúng với file XML của bạn
        setContentView(R.layout.activity_login)

        // 1. Ánh xạ View theo ID mới trong XML
        val etEmail = findViewById<EditText>(R.id.etLoginEmail)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword) // ID mới
        val btnLogin = findViewById<Button>(R.id.btnLogin)            // ID mới
        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)  // ID mới

        // --- TỰ ĐỘNG TÌM SERVER KHI MỞ APP ---
        btnLogin.isEnabled = false
        btnLogin.text = "Đang tìm Server..."

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Quét UDP tìm IP
            val ip = NetworkDiscovery.findServerIp()

            withContext(Dispatchers.Main) {
                if (ip != null) {
                    detectedIp = ip
                    Toast.makeText(this@LoginActivity, "Đã tìm thấy Server: $ip", Toast.LENGTH_SHORT).show()

                    // 2. Tìm thấy thì thử kết nối TCP luôn
                    connectTcp(ip, btnLogin)
                } else {
                    // Không tìm thấy (do Emulator hoặc Firewall chặn)
                    btnLogin.isEnabled = true
                    btnLogin.text = "Kết nối thất bại (Thử lại)"
                    Toast.makeText(this@LoginActivity, "Không tìm thấy Server! Hãy kiểm tra Firewall.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 3. Xử lý sự kiện bấm nút "Đăng ký ngay"
        tvSignUpLink.setOnClickListener {
            // Chuyển sang màn hình Đăng ký
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 4. Xử lý sự kiện bấm nút "Đăng Nhập"
        btnLogin.setOnClickListener {
            if (detectedIp == null) {
                btnLogin.text = "Đang tìm lại..."
                btnLogin.isEnabled = false
                CoroutineScope(Dispatchers.IO).launch {
                    val ip = NetworkDiscovery.findServerIp()
                    withContext(Dispatchers.Main) {
                        if (ip != null) {
                            detectedIp = ip
                            connectTcp(ip, btnLogin)
                        } else {
                            btnLogin.isEnabled = true
                            btnLogin.text = "Thử lại"
                            Toast.makeText(this@LoginActivity, "Vẫn không tìm thấy!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return@setOnClickListener
            }

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
                        saveDataAndGoHome(userDto)
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

    private fun connectTcp(ip: String, btnLogin: Button) {
        CoroutineScope(Dispatchers.IO).launch {
            // Gọi hàm Native với IP vừa tìm được
            val status = NativeClient.connectToServer(ip, 8080)

            withContext(Dispatchers.Main) {
                if (status.contains("thành công")) {
                    btnLogin.text = "Đăng Nhập"
                    btnLogin.isEnabled = true
                } else {
                    btnLogin.text = "Lỗi Socket"
                    Toast.makeText(this@LoginActivity, status, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun saveDataAndGoHome(userDto: NativeUserDto) {
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
    }
}