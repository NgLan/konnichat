package com.example.konnichat.presentation.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.di.Injection
import com.example.konnichat.presentation.chat.adapter.ChatAdapter

class ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val myUserId = intent.getIntExtra("MY_ID", -1)
        val friendId = intent.getIntExtra("FRIEND_ID", -1)
        val friendName = intent.getStringExtra("FRIEND_NAME") ?: "..."

        findViewById<TextView>(R.id.tvChatTitle).text = friendName

        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // Đóng ChatActivity, quay về HomeActivity
        }

        setupViewModel(myUserId, friendId)
        setupRecyclerView(myUserId)
        setupInput()
        observeMessages()
    }

    private fun setupViewModel(myUserId: Int, friendId: Int) {
        val useCases = Injection.provideChatUseCases(this)
        val factory = ChatViewModelFactory(useCases, myUserId, friendId)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    private fun setupRecyclerView(myUserId: Int) {
        rvChat = findViewById(R.id.rvChat)
        adapter = ChatAdapter(emptyList(), myUserId)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // Cuộn xuống dưới cùng
        rvChat.layoutManager = layoutManager
        rvChat.adapter = adapter
    }

    private fun setupInput() {
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)

        btnSend.setOnClickListener {
            val content = etMessage.text.toString()
            viewModel.sendMessage(content)
            etMessage.text.clear()
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            adapter.updateData(messages)
            if (messages.isNotEmpty()) {
                rvChat.scrollToPosition(messages.size - 1)
            }
        }
    }
}
