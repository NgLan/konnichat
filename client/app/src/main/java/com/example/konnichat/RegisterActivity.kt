package com.example.konnichat // <--- Đổi thành package thực tế của bạn

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {

    // --- KHAI BÁO JNI ---
    // Gọi xuống C++ để đăng ký
//    external fun registerUser(user: String, pass: String): Int

    companion object {
        init {
            // LƯU Ý: Tên thư viện trong CMakeLists.txt của bạn là gì?
            // Nếu mặc định là "konnichat" hoặc "chatapp" thì điền vào đây.
            System.loadLibrary("konnichat")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register) // File XML bạn vừa đưa

        // 1. Ánh xạ View theo ID MỚI trong XML của Konnichat
        val etEmail = findViewById<EditText>(R.id.etSignUpEmail)
        val etPass = findViewById<EditText>(R.id.etSignUpPassword)
        val etConfirmPass = findViewById<EditText>(R.id.etSignUpConfirmPassword)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        // 2. Xử lý nút "Đăng nhập ngay" (Quay lại màn hình Login)
        tvLoginLink.setOnClickListener {
            finish() // Đóng màn hình đăng ký, quay về LoginActivity đang chờ ở dưới
        }

        etPass.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val inputPass = s.toString()

                // Gọi hàm kiểm tra ngay khi người dùng vừa nhấc tay khỏi bàn phím
                val errorMsg = validatePassword(inputPass)

                // Hiển thị lỗi ngay lập tức (hoặc xóa lỗi nếu null)
                etPass.error = errorMsg

                // (Nâng cao) Nếu mật khẩu chính thay đổi,
                // bạn cũng nên kiểm tra lại xem nó còn khớp với ô "Xác nhận" không
                val confirmPass = etConfirmPass.text.toString()
                if (confirmPass.isNotEmpty() && inputPass != confirmPass) {
                    etConfirmPass.error = "Mật khẩu xác nhận không khớp"
                } else {
                    etConfirmPass.error = null // Xóa lỗi bên ô xác nhận nếu đã khớp lại
                }
            }
        })

        // 3. Xử lý nút "Đăng Ký"
        btnSignUp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString()
            val confirmPass = etConfirmPass.text.toString()

            // --- VALIDATION (Kiểm tra dữ liệu) ---

            // A. Kiểm tra trống
            if (email.isEmpty()) {
                etEmail.error = "Vui lòng nhập Email"
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Email không đúng định dạng!"
                etEmail.requestFocus() // Đưa con trỏ về ô nhập lỗi
                return@setOnClickListener
            }

            // C. Kiểm tra độ mạnh mật khẩu (Logic bảo mật)
            val validationMsg = validatePassword(pass)
            if (validationMsg != null) {
                // Nếu có lỗi, báo đỏ ngay ô mật khẩu
                etPass.error = validationMsg
                etPass.requestFocus()
                return@setOnClickListener
            }

            // B. Kiểm tra mật khẩu khớp nhau
            if (pass != confirmPass) {
                etConfirmPass.error = "Mật khẩu xác nhận không khớp"
                return@setOnClickListener
            }



            // --- GỌI JNI ĐỂ ĐĂNG KÝ ---
            btnSignUp.isEnabled = false // Khóa nút để tránh spam
            btnSignUp.text = "Đang đăng ký..."

            CoroutineScope(Dispatchers.IO).launch {
                val result = NativeClient.registerUser(email, pass)

                withContext(Dispatchers.Main) {
                    btnSignUp.isEnabled = true
                    btnSignUp.text = "Đăng Ký"

                    if (result == 1) {
                        Toast.makeText(this@RegisterActivity, "Đăng ký thành công! Hãy đăng nhập.", Toast.LENGTH_LONG).show()
                        finish() // Tự động quay về trang Login
                    } else {
                        Toast.makeText(this@RegisterActivity, "Đăng ký thất bại (Email đã tồn tại?)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Hàm kiểm tra mật khẩu chặt chẽ
    private fun validatePassword(pass: String): String? {
        if (pass.length < 8) return "Mật khẩu phải từ 8 ký tự trở lên"

        // 1. Chặn các ký tự gây lỗi code C/SQL
        val forbiddenChars = listOf('\'', '"', '\\', ';')
        for (char in forbiddenChars) {
            if (pass.contains(char)) return "Mật khẩu không được chứa ký tự: $char"
        }

        // 2. Regex yêu cầu: Số, Chữ thường, Chữ Hoa, Ký tự đặc biệt
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
        val pattern = Pattern.compile(passwordPattern)

        if (!pattern.matcher(pass).matches()) {
            return "Cần: 1 Hoa, 1 thường, 1 số, 1 ký tự đặc biệt (@#$%^&+=!)"
        }

        return null // Hợp lệ
    }
}