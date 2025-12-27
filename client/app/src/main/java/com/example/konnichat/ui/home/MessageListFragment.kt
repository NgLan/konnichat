// File: client/app/src/main/java/com/example/konnichat/ui/home/MessageListFragment.kt
package com.example.konnichat.ui.home

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessageListFragment : Fragment() {

    // Inject UserRepository từ App
    private val viewModel: HomeViewModel by activityViewModels {
        HomeViewModelFactory((requireActivity().application as App).userRepository)
    }

    private lateinit var adapter: FriendAdapter

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

        // Dùng FriendAdapter mới
        adapter = FriendAdapter { user ->
            // Click vào bạn bè -> Mở chat (Tính năng sau này)
            Toast.makeText(requireContext(), "Chat với: ${user.name}", Toast.LENGTH_SHORT).show()
        }

        rvConversations.layoutManager = LinearLayoutManager(context)
        rvConversations.adapter = adapter

        // Lắng nghe list friends
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friends.collectLatest { list ->
                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvConversations.visibility = View.GONE
                    tvEmpty.text = "Chưa có bạn bè nào.\nHãy kết bạn thêm nhé!"
                } else {
                    tvEmpty.visibility = View.GONE
                    rvConversations.visibility = View.VISIBLE
                    adapter.submitList(list)
                }
            }
        }
    }
}