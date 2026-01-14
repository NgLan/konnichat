package com.example.konnichat.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
// Đã xóa import TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.data.local.prefs.SessionManager
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.NativeEventListenerImpl
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.ui.base.BaseActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.konnichat.ui.chat.ChatActivity
import  com.example.konnichat.ui.auth.AuthViewModel
import com.example.konnichat.ui.group.CreateGroupActivity

class HomeActivity : BaseActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            (application as App).userRepository,
            (application as App).chatRepository,
            (application as App).sessionManager
        )
    }

    // Khai báo thêm AuthViewModel để xử lý Logout
    private val authViewModel: AuthViewModel by viewModels {
        com.example.konnichat.ui.auth.AuthViewModelFactory((application as App).authRepository)
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

        authViewModel.logoutState.observe(this) { resource ->
            if (resource is com.example.konnichat.core.state.Resource.Success) {
                // Chuyển về màn hình Login và xóa sạch Stack các màn hình cũ
                val intent = Intent(this, com.example.konnichat.ui.auth.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_create_group -> {
                // Mở màn hình tạo nhóm
                val intent = Intent(this, CreateGroupActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_logout -> {
                authViewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

            val chatType = intent.getStringExtra("CHAT_TYPE") ?: "private"

            if (targetId != -1) {
                val chatIntent = Intent(this, ChatActivity::class.java)
                chatIntent.putExtra("TARGET_ID", targetId)
                chatIntent.putExtra("TARGET_NAME", targetName ?: "Người dùng")

                chatIntent.putExtra("CHAT_TYPE", chatType)


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

class HomeViewModelFactory(
    private val userRepo: UserRepository,
    private val chatRepo: ChatRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userRepo, chatRepo, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}