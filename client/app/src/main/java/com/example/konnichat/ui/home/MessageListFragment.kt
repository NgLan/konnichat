package com.example.konnichat.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.data.local.AppDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.konnichat.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MessageListFragment : Fragment() {

    // Dùng chung ViewModel với Activity cha (HomeActivity)
    private val viewModel: HomeViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Tạo DB (Lưu ý dùng requireContext())
                val db = androidx.room.Room.databaseBuilder(
                    requireContext().applicationContext,
                    AppDatabase::class.java, "konnichat-db"
                ).build()
                return HomeViewModel(db) as T
            }
        }
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

        // Setup Adapter
        adapter = ConversationAdapter { item ->
            // Khi click vào 1 dòng -> Mở màn hình chat (Sẽ làm sau)
            // val intent = Intent(requireContext(), ChatActivity::class.java)
            // intent.putExtra("USER_ID", item.friendId)
            // startActivity(intent)
        }

        rvConversations.layoutManager = LinearLayoutManager(context)
        rvConversations.adapter = adapter

        // Lắng nghe dữ liệu từ ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversations.collectLatest { list ->
                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvConversations.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvConversations.visibility = View.VISIBLE
                    adapter.submitList(list)
                }
            }
        }
    }
}