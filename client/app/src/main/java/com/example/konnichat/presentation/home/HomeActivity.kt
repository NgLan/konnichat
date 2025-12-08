package com.example.konnichat.presentation.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.di.Injection
import com.example.konnichat.domain.model.User
import com.example.konnichat.presentation.chat.ChatActivity
import com.example.konnichat.presentation.home.adapter.FriendAdapter

class HomeActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: FriendAdapter
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        currentUserId = intent.getIntExtra("USER_ID", -1)

        setupViewModel()
        setupRecyclerView()
        observeData()

        // Load dữ liệu
        if (currentUserId != -1) {
            viewModel.loadFriends(currentUserId)
        }
    }

    private fun setupViewModel() {
        val getFriendsUseCase = Injection.provideGetFriendsUseCase(this)
        val syncMsgUseCase = Injection.provideSyncOfflineMessagesUseCase(this)

        val factory = HomeViewModelFactory(getFriendsUseCase, syncMsgUseCase)

        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    private fun setupRecyclerView() {
        val rvFriends = findViewById<RecyclerView>(R.id.rvFriends)
        adapter = FriendAdapter(emptyList()) { selectedUser ->
            openChat(selectedUser)
        }
        rvFriends.layoutManager = LinearLayoutManager(this)
        rvFriends.adapter = adapter
    }

    private fun observeData() {
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyState)
        val rvFriends = findViewById<RecyclerView>(R.id.rvFriends)

        viewModel.friends.observe(this) { list ->
            if (list.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvFriends.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvFriends.visibility = View.VISIBLE
                adapter.updateData(list)
            }
        }
    }

    private fun openChat(user: User) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("MY_ID", currentUserId)
        intent.putExtra("FRIEND_ID", user.id)
        intent.putExtra("FRIEND_NAME", user.name)
        startActivity(intent)
    }
}
