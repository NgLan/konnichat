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
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.konnichat.ui.chat.ChatActivity
import com.example.konnichat.ui.group.GroupInfoActivity
import kotlinx.coroutines.launch

/**
 * BaseActivity: Chứa logic chung cho tất cả màn hình.
 * - Hiển thị banner trạng thái mạng (Connecting/Disconnected).
 * - Lắng nghe sự kiện điều hướng toàn cục (ví dụ: bị kick khỏi nhóm).
 */
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

    // Banner báo mất mạng/đang kết nối
    private fun setupConnectionObserver() {
        if (statusBanner == null) {
            val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)

            statusBanner = TextView(this).apply {
                text = "Đang kết nối lại..."
                setTextColor(Color.WHITE)
                setBackgroundColor("#FF5722".toColorInt()) // Màu cam nổi bật
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
                visibility = View.GONE // Mặc định ẩn

                // Layout Params: Hiện ở trên cùng
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
            // Nếu user đã chủ động logout, không hiện lỗi mạng
            if (NativeEventListenerImpl.isUserLoggedOut) {
                statusBanner?.visibility = View.GONE
                return@observe
            }

            when (state) {
                ConnectionState.CONNECTED -> {
                    statusBanner?.visibility = View.GONE
                }

                ConnectionState.CONNECTING -> {
                    statusBanner?.apply {
                        text = "Đang kết nối lại..."
                        setBackgroundColor("#FF9800".toColorInt()) // Cam
                        visibility = View.VISIBLE
                    }
                }

                ConnectionState.DISCONNECTED -> {
                    statusBanner?.apply {
                        text = "Mất kết nối máy chủ"
                        setBackgroundColor("#F44336".toColorInt()) // Đỏ
                        visibility = View.VISIBLE
                    }
                }

                else -> {
                    statusBanner?.visibility = View.GONE
                }
            }
        }
    }

    // Lắng nghe sự kiện điều hướng từ Server
    private fun setupNavigationObserver() {
        lifecycleScope.launch {
            // Block này sẽ chạy khi Activity ở trạng thái STARTED và tự dừng khi STOPPED
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NativeEventListenerImpl.navigationEvent.collectLatest { command: NativeEventListenerImpl.NavCommand ->
                    val currentTargetId = when (this@BaseActivity) {
                        is ChatActivity -> this@BaseActivity.intent.getIntExtra("TARGET_ID", -1)
                        is GroupInfoActivity -> this@BaseActivity.intent.getIntExtra("GROUP_ID", -1)
                        else -> -1
                    }

                    if (currentTargetId != -1 && currentTargetId == command.targetId) {
                        Toast.makeText(this@BaseActivity, command.reason, Toast.LENGTH_LONG).show()

                        val intent = Intent(this@BaseActivity, HomeActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)

                        finish()
                    }
                }
            }
        }
    }
}
