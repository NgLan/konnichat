package com.example.konnichat.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.ui.chat.ChatActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageListFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels {
        HomeViewModelFactory(
            (requireActivity().application as App).userRepository,
            (requireActivity().application as App).chatRepository,
            requireContext().getSharedPreferences("konnichat_prefs", android.content.Context.MODE_PRIVATE)
        )
    }

    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_message_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvConversations = view.findViewById<RecyclerView>(R.id.rvConversations)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyState)

        // Cập nhật Adapter với callback unfriend
        adapter = ConversationAdapter { item ->
            // Callback khi click vào item
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("TARGET_ID", item.id)       // ID User hoặc Group
            intent.putExtra("TARGET_NAME", item.name)   // Tên
            intent.putExtra("CHAT_TYPE", item.chatType) // [QUAN TRỌNG] "private" hoặc "group"
            startActivity(intent)
        }

        rvConversations.layoutManager = LinearLayoutManager(context)
        rvConversations.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversations.collectLatest { list ->
                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvConversations.visibility = View.GONE
                    tvEmpty.text = "Chưa có cuộc trò chuyện nào."
                } else {
                    tvEmpty.visibility = View.GONE
                    rvConversations.visibility = View.VISIBLE
                    adapter.submitList(list)
                }
            }
        }
    }
}