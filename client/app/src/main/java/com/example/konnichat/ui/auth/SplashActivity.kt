package com.example.konnichat.ui.auth

// Chú ý import R của đúng package ứng dụng
import com.example.konnichat.R
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.konnichat.core.state.Resource
import com.example.konnichat.App
class SplashActivity : AppCompatActivity() {

    // Khởi tạo ViewModel
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Ánh xạ View
        val pbLoading = findViewById<ProgressBar>(R.id.pbLoading)
        val tvError = findViewById<TextView>(R.id.tvError)
        val btnRetry = findViewById<Button>(R.id.btnRetry)

        // Bắt đầu kết nối ngay khi mở màn hình
        viewModel.connectDefault()

        // Lắng nghe kết quả kết nối
        viewModel.connectState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    pbLoading.visibility = View.VISIBLE
                    tvError.visibility = View.GONE
                    btnRetry.visibility = View.GONE
                }
                is Resource.Success -> {
                    // Kết nối thành công -> Chuyển sang Login
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish() // Đóng SplashActivity để user không back lại được
                }
                is Resource.Error -> {
                    // Lỗi -> Hiện nút thử lại
                    pbLoading.visibility = View.GONE
                    tvError.text = resource.message
                    tvError.visibility = View.VISIBLE
                    btnRetry.visibility = View.VISIBLE
                }
            }
        }

        // Xử lý khi bấm nút Thử lại
        btnRetry.setOnClickListener {
            viewModel.connectDefault()
        }
    }
}