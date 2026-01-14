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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.konnichat.core.state.Resource
import com.example.konnichat.App
import com.example.konnichat.databinding.ActivitySplashBinding
import com.example.konnichat.ui.base.BaseActivity
import com.example.konnichat.ui.home.HomeActivity

class SplashActivity : BaseActivity() {

    private val TAG = "SplashActivity"
    private lateinit var binding: ActivitySplashBinding

    // Khởi tạo ViewModel
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as App).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        splashScreen.setKeepOnScreenCondition {
            // Giữ splash nếu trạng thái đang là Loading hoặc chưa có kết quả
            viewModel.autoLoginState.value is Resource.Loading || viewModel.autoLoginState.value == null
        }

        setupObservers()
        setupListeners()

        // Bắt đầu kết nối ngay khi mở màn hình
        Log.d(TAG, "Checking auto login...")
        viewModel.checkAutoLogin()
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            Log.d(TAG, "User clicked Retry")
            viewModel.checkAutoLogin()
        }
    }

    private fun setupObservers() {
        // Observe LiveData
        viewModel.autoLoginState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                }
                is Resource.Success -> {
                    val isLoggedIn = resource.data ?: false
                    if (isLoggedIn) {
                        navigateToHome()
                    } else {
                        navigateToLogin()
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Error: ${resource.message}")
                    binding.pbLoading.visibility = View.GONE
                    binding.tvError.text = resource.message
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnRetry.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        // Cờ này để xóa Splash khỏi back stack, user bấm Back sẽ thoát app luôn chứ ko quay lại Splash
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}