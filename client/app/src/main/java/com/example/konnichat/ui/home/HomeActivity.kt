// File: client/app/src/main/java/com/example/konnichat/ui/home/HomeActivity.kt
package com.example.konnichat.ui.home

import android.content.Intent
import android.os.Bundle
// Đã xóa import TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.NativeEventListenerImpl
import com.example.konnichat.data.repository.UserRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.konnichat.ui.chat.ChatActivity

class HomeActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory((application as App).userRepository)
    }

    private lateinit var bottomNav: BottomNavigationView
    // Đã xóa biến tvTitle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1. Setup Native Listener
        val app = application as App
        NativeEventListenerImpl.userRepository = app.userRepository
        NativeEventListenerImpl.context = applicationContext

        // Uncomment dòng này để bắt đầu nhận sự kiện
        NativeClient.startListening(NativeEventListenerImpl)

        // 2. Ánh xạ View
        bottomNav = findViewById(R.id.bottom_navigation)
        // Đã xóa dòng findViewById(tvTitle)

        // 3. Setup Bottom Navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_message -> {
                    // Đã xóa dòng tvTitle.text = ...
                    loadFragment(MessageListFragment())
                    true
                }
                R.id.nav_search -> {
                    // Đã xóa dòng tvTitle.text = ...
                    loadFragment(com.example.konnichat.ui.search.SearchFragment())
                    true
                }
                R.id.nav_notification -> {
                    // Đã xóa dòng tvTitle.text = ...
                    loadFragment(com.example.konnichat.ui.notification.FriendRequestFragment())
                    true
                }
                else -> false
            }
        }

        // 4. Mặc định load tab Message
        if (savedInstanceState == null) {
            loadFragment(MessageListFragment())
            handleNavigationIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent) {
        val type = intent.getStringExtra("NAVIGATE_TO")
        if (type == "FRIEND_REQ") {
            bottomNav.selectedItemId = R.id.nav_notification
        } else if (type == "MESSAGE") {
            bottomNav.selectedItemId = R.id.nav_message

            // [THÊM MỚI] Logic tự động mở màn hình Chat
            val targetId = intent.getIntExtra("TARGET_ID", -1)
            val targetName = intent.getStringExtra("TARGET_NAME")

            if (targetId != -1) {
                val chatIntent = Intent(this, ChatActivity::class.java)
                chatIntent.putExtra("TARGET_ID", targetId)
                chatIntent.putExtra("TARGET_NAME", targetName ?: "Người dùng")
                startActivity(chatIntent)
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}

class HomeViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}