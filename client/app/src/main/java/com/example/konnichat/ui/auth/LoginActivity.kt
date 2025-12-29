package com.example.konnichat.ui.auth

import com.example.konnichat.R
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.konnichat.core.state.Resource
// Import HomeActivity khi bạn tạo nó sau này
 import com.example.konnichat.ui.home.HomeActivity
import com.example.konnichat.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Ánh xạ View
        val etEmail = findViewById<EditText>(R.id.etLoginEmail)
        val etPass = findViewById<EditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)

        // Xử lý bấm nút Login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()
            viewModel.login(email, pass)
        }

        // Xử lý chuyển sang màn hình Đăng ký
        tvSignUpLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Lắng nghe kết quả Login
        viewModel.loginState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    btnLogin.isEnabled = false
                    btnLogin.text = "Đang xử lý..."
                }
                is Resource.Success -> {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Đăng Nhập"
                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

                    // TODO: Lưu thông tin User vào Session/SharedPreferences ở đây
                    val userDto = resource.data
                    if (userDto != null) {
                        val prefs = getSharedPreferences("konnichat_prefs", MODE_PRIVATE)
                        prefs.edit().apply {
                            putInt("USER_ID", userDto.id)
                            putString("USER_NAME", userDto.name)
                            putString("USER_EMAIL", userDto.email)
                            apply() // Lưu xuống file
                        }

                        val app = application as App
                        CoroutineScope(Dispatchers.IO).launch {
                            // Xóa sạch bảng users, messages, friends... của người dùng trước
                            app.db.clearAllTables()

                            // Sau khi xóa xong thì mới chuyển màn hình
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@LoginActivity, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }

                    // Chuyển sang màn hình chính
                     val intent = Intent(this, HomeActivity::class.java)
                     startActivity(intent)
                     finish()
                }
                is Resource.Error -> {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Đăng Nhập"
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}