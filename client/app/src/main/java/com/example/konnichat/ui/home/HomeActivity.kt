// File: client/app/src/main/java/com/example/konnichat/ui/home/HomeActivity.kt
package com.example.konnichat.ui.home

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.NativeEventListenerImpl
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    // Sử dụng Factory để lấy ViewModel (Lấy ChatRepository từ App)
    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory((application as App).userRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1. Lấy instance Application
        val app = application as App

        // 2. Cấu hình Listener (Để Native C biết đường lưu dữ liệu vào đâu)
        NativeEventListenerImpl.userRepository = app.userRepository

        // 3. Bắt đầu lắng nghe Socket
        NativeClient.startListening(NativeEventListenerImpl)

        // 4. Setup UI
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)

        // Mặc định load Tab Message
        loadFragment(MessageListFragment())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_message -> {
                    tvTitle.text = "Đoạn chat"
                    loadFragment(MessageListFragment())
                    true
                }
                R.id.nav_search -> {
                    tvTitle.text = "Tìm bạn bè"
                    loadFragment(com.example.konnichat.ui.search.SearchFragment()) // <-- SỬA DÒNG NÀY
                    true
                }
                R.id.nav_notification -> {
                    tvTitle.text = "Thông báo"
                    Toast.makeText(this, "Chức năng Thông báo đang phát triển", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}

// Factory để tạo HomeViewModel
class HomeViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}