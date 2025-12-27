package com.example.konnichat.ui.home

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.konnichat.R
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.ui.home.HomeViewModel // Đảm bảo import đúng ViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    // Khởi tạo ViewModel (Chia sẻ cho các Fragment con dùng chung)
    // Lưu ý: Cần sửa lại cách khởi tạo ViewModel này trong thực tế nếu dùng Factory,
    // nhưng ở đây tui viết kiểu đơn giản nhất để chạy được demo.
    private val viewModel: HomeViewModel by viewModels {
        // Tạm thời truyền DB thủ công (Sau này dùng Dependency Injection sẽ sạch hơn)
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val db = androidx.room.Room.databaseBuilder(
                    applicationContext,
                    AppDatabase::class.java, "konnichat-db"
                ).build()
                return HomeViewModel(db) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)

        // Mặc định load Tab 1 (MessageListFragment)
        loadFragment(MessageListFragment())

        // Xử lý sự kiện bấm vào Bottom Navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_message -> {
                    tvTitle.text = "Đoạn chat"
                    loadFragment(MessageListFragment())
                    true
                }
                R.id.nav_search -> {
                    tvTitle.text = "Tìm bạn bè"
                    // Tab 2: Tạm thời chưa làm, hiện thông báo
                    Toast.makeText(this, "Chức năng Tìm kiếm đang phát triển", Toast.LENGTH_SHORT).show()
                    // loadFragment(SearchFragment()) <- Sau này sẽ mở comment dòng này
                    true
                }
                R.id.nav_notification -> {
                    tvTitle.text = "Thông báo"
                    // Tab 3: Tạm thời chưa làm
                    Toast.makeText(this, "Chức năng Thông báo đang phát triển", Toast.LENGTH_SHORT).show()
                    // loadFragment(NotificationFragment()) <- Sau này sẽ mở comment dòng này
                    true
                }
                else -> false
            }
        }
    }

    // Hàm hỗ trợ thay đổi Fragment (Màn hình con)
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}