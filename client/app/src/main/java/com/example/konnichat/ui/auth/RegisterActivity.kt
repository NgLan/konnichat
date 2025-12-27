package com.example.konnichat.ui.auth

import com.example.konnichat.R
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.konnichat.core.state.Resource

class RegisterActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Ánh xạ View
        // Chú ý: Cần thêm ID cho EditText Tên trong file XML nếu chưa có (ví dụ: etSignUpName)
        // Trong XML bạn gửi tui không thấy trường nhập Tên (Name), chỉ có Email/Pass.
        // Tui giả định bạn sẽ thêm EditText tên có ID là etSignUpName.
        // Nếu chưa có, bạn nhớ thêm vào XML nhé. Tạm thời tui comment lại dòng lấy name.

         val etName = findViewById<EditText>(R.id.etSignUpName)
        val etEmail = findViewById<EditText>(R.id.etSignUpEmail)
        val etPass = findViewById<EditText>(R.id.etSignUpPassword)
        val etConfirmPass = findViewById<EditText>(R.id.etSignUpConfirmPassword)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        tvLoginLink.setOnClickListener {
            finish() // Quay lại màn hình Login
        }

        btnSignUp.setOnClickListener {
             val name = etName.text.toString().trim()
//            val name = "User" // Tạm thời hardcode nếu UI chưa có trường Name
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val confirmPass = etConfirmPass.text.toString().trim()

            if (pass != confirmPass) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(name, email, pass)
        }

        // Lắng nghe kết quả Đăng ký
        viewModel.registerState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    btnSignUp.isEnabled = false
                    btnSignUp.text = "Đang đăng ký..."
                }
                is Resource.Success -> {
                    btnSignUp.isEnabled = true
                    btnSignUp.text = "Đăng Ký"
                    Toast.makeText(this, "Đăng ký thành công! Hãy đăng nhập.", Toast.LENGTH_LONG).show()
                    finish() // Quay về màn hình login
                }
                is Resource.Error -> {
                    btnSignUp.isEnabled = true
                    btnSignUp.text = "Đăng Ký"
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}