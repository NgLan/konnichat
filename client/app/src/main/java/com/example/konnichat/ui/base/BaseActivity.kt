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

open class BaseActivity : AppCompatActivity() {

    private var statusBanner: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}