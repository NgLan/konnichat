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
import android.widget.PopupMenu // [THÊM]
import com.example.konnichat.data.local.model.ConversationItem // [THÊM]
import com.example.konnichat.utils.DialogUtils

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
        adapter = ConversationAdapter(
            onItemClick = { item ->
                // Logic cũ: Mở màn hình chat
                val intent = Intent(requireContext(), ChatActivity::class.java)
                intent.putExtra("TARGET_ID", item.id)
                intent.putExtra("TARGET_NAME", item.name)
                intent.putExtra("CHAT_TYPE", item.chatType)
                startActivity(intent)
            },
            onMoreClick = { item, view ->
                // Logic mới: Hiển thị menu tùy chọn
                showActionMenu(view, item)
            }
        )

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

    // [THÊM MỚI] Hàm hiển thị Menu popup
    private fun showActionMenu(view: View, item: ConversationItem) {
        val popup = PopupMenu(requireContext(), view)

        // Kiểm tra loại chat để hiển thị menu phù hợp
        if (item.chatType == "private") {
            popup.menu.add("Hủy kết bạn")
        } else if (item.chatType == "group") {
            popup.menu.add("Rời nhóm")
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                "Hủy kết bạn" -> {
                    // Gọi ViewModel để hủy kết bạn
                    viewModel.unfriendUser(item.id)
                    true
                }
                "Rời nhóm" -> {
                    // Tạm thời chỉ hiện thông báo
                    handleGroupActionClick(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun handleGroupActionClick(item: ConversationItem) {
        // Dùng lifecycleScope để chạy coroutine kiểm tra DB
        lifecycleScope.launch {
            val role = viewModel.getGroupRole(item.id)

            android.util.Log.d("CheckRole", "Group: ${item.id}, User Role: $role")

            if (role != null && role.equals("admin", ignoreCase = true)) {
                DialogUtils.showConfirmationDialog(
                    requireContext(),
                    "Giải tán nhóm?",
                    "Bạn là trưởng nhóm. Hành động này sẽ xóa nhóm vĩnh viễn.",
                    positiveLabel = "Giải tán"
                ) {
                    viewModel.dissolveGroup(item.id)
                }
            } else {
                DialogUtils.showConfirmationDialog(
                    requireContext(),
                    "Rời nhóm?",
                    "Bạn có chắc chắn muốn rời nhóm?",
                    positiveLabel = "Rời nhóm"
                ) {
                    viewModel.leaveGroup(item.id)
                }
            }
        }
    }
}