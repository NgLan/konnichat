package com.example.konnichat.ui.base

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.konnichat.data.remote.ConnectionState
import com.example.konnichat.data.remote.NativeEventListenerImpl
import com.example.konnichat.ui.home.HomeActivity
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest


open class BaseActivity : AppCompatActivity() {

    private var statusBanner: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupNavigationObserver()
    }

    override fun onStart() {
        super.onStart()
        setupConnectionObserver()
    }

    private fun setupConnectionObserver() {
        // Tạo View thông báo bằng Code (Không cần sửa XML)
        if (statusBanner == null) {
            val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)

            statusBanner = TextView(this).apply {
                text = "Đang kết nối lại..."
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#FF5722")) // Màu cam nổi bật
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
                visibility = View.GONE // Mặc định ẩn

                // Layout Params: Hiện ở trên cùng (Top) hoặc dưới cùng (Bottom) tùy bạn
                // Ở đây tôi để ở trên cùng
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                params.gravity = Gravity.TOP
                layoutParams = params
            }

            rootView.addView(statusBanner)
        }

        // Lắng nghe trạng thái toàn cục từ NativeEventListenerImpl
        NativeEventListenerImpl.connectionState.observe(this) { state ->

            if (NativeEventListenerImpl.isUserLoggedOut) {
                statusBanner?.visibility = View.GONE
                return@observe
            }

            when (state) {
                ConnectionState.CONNECTED -> {
                    statusBanner?.visibility = View.GONE
                }
                ConnectionState.CONNECTING -> {
                    statusBanner?.text = "Đang kết nối lại..."
                    statusBanner?.setBackgroundColor(Color.parseColor("#FF9800")) // Cam
                    statusBanner?.visibility = View.VISIBLE
                }
                ConnectionState.DISCONNECTED -> {
                    statusBanner?.text = "Mất kết nối máy chủ"
                    statusBanner?.setBackgroundColor(Color.parseColor("#F44336")) // Đỏ
                    statusBanner?.visibility = View.VISIBLE
                }
                else -> { statusBanner?.visibility = View.GONE }
            }
        }
    }

    private fun setupNavigationObserver() {
        lifecycleScope.launchWhenStarted {
            // Chỉ rõ kiểu dữ liệu (command: NativeEventListenerImpl.NavCommand) để tránh lỗi infer type
            NativeEventListenerImpl.navigationEvent.collectLatest { command: NativeEventListenerImpl.NavCommand ->
                val currentTargetId = when (this@BaseActivity) {
                    is com.example.konnichat.ui.chat.ChatActivity -> {
                        this@BaseActivity.intent.getIntExtra("TARGET_ID", -1)
                    }
                    is com.example.konnichat.ui.group.GroupInfoActivity -> {
                        this@BaseActivity.intent.getIntExtra("GROUP_ID", -1)
                    }
                    else -> -1
                }

                // Bây giờ các biến targetId và reason sẽ được nhận diện đúng từ object command
                if (currentTargetId != -1 && currentTargetId == command.targetId) {
                    // Sửa lỗi Unresolved reference 'show' bằng cách gọi .show() đúng cú pháp
                    Toast.makeText(this@BaseActivity, command.reason, Toast.LENGTH_LONG).show()

                    val intent = Intent(this@BaseActivity, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)

                    finish()
                }
            }
        }
    }
}