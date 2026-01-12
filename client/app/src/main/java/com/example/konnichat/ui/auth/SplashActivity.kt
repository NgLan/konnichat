package com.example.konnichat.ui.auth

import com.example.konnichat.R
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import com.example.konnichat.core.state.Resource
import com.example.konnichat.App
import com.example.konnichat.ui.base.BaseActivity
import com.example.konnichat.ui.home.HomeActivity

class SplashActivity : BaseActivity() {

    // Khởi tạo ViewModel
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as App).authRepository)
    }

    private val TAG = "[SplashUI]"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Ánh xạ View
        val pbLoading = findViewById<ProgressBar>(R.id.pbLoading)
        val tvError = findViewById<TextView>(R.id.tvError)
        val btnRetry = findViewById<Button>(R.id.btnRetry)

        // Bắt đầu kết nối ngay khi mở màn hình
        Log.d(TAG, "🎬 onCreate: Bắt đầu check AutoLogin")
        viewModel.checkAutoLogin()

        // Lắng nghe kết quả kết nối
        viewModel.autoLoginState.observe(this) { resource ->
            Log.d(TAG, "👀 Observer nhận state mới: ${resource.javaClass.simpleName}")

            when (resource) {
                is Resource.Loading -> {
                    Log.d(TAG, "⏳ Đang xử lý (Loading)...")
                    pbLoading.visibility = View.VISIBLE
                    tvError.visibility = View.GONE
                    btnRetry.visibility = View.GONE
                }
                is Resource.Success -> {
                    val isLoggedIn = resource.data ?: false
                    if (isLoggedIn) {
                        Log.i(TAG, "✅ Đã đăng nhập -> Go Home")
                        // Đã lưu pass và login thành công -> Vào thẳng Home
                        startActivity(Intent(this, HomeActivity::class.java))
                    } else {
                        Log.i(TAG, "➡️ Auto Login thất bại hoặc chưa có dữ liệu -> Vào Login")
                        // Kết nối OK nhưng chưa đăng nhập -> Vào màn Login
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    finish() // Đóng SplashActivity để user không back lại được
                }
                is Resource.Error -> {
                    Log.e(TAG, "❌ Lỗi: ${resource.message}")
                    // Lỗi mạng (socket fail) -> Hiện nút Retry
                    pbLoading.visibility = View.GONE
                    tvError.text = resource.message
                    tvError.visibility = View.VISIBLE
                    btnRetry.visibility = View.VISIBLE
                }
            }
        }

        // Xử lý khi bấm nút Thử lại
        btnRetry.setOnClickListener {
            Log.d(TAG, "🔄 User bấm Retry")
            viewModel.checkAutoLogin()
        }
    }
}