package com.example.konnichat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    // --- KHAI BÁO JNI ---
    // Hàm kết nối server
//    external fun connectToServer(): String
//    // Hàm gửi user/pass để đăng nhập
//    external fun loginUser(user: String, pass: String): Int

    companion object {
        // Load thư viện native-lib (tên trong CMakeLists.txt)
        init {
            System.loadLibrary("konnichat")
        }
    }

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
                // Gọi hàm C
                val userId = NativeClient.loginUser(email, password)

                // Cập nhật UI ở luồng chính
                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Đăng Nhập"

                    if (userId > 0) {
                        Toast.makeText(this@LoginActivity, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

                        // --- TODO: CHUYỂN SANG MÀN HÌNH CHÍNH (HOME) ---
                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                        intent.putExtra("USER_ID", userId) // Truyền ID sang để dùng
                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(this@LoginActivity, "Sai Email hoặc Mật khẩu!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}