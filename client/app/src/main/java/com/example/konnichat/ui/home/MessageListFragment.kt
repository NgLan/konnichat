package com.example.konnichat.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.konnichat.data.local.model.ConversationItem // [THÊM]
import com.example.konnichat.databinding.FragmentMessageListBinding
import com.example.konnichat.utils.DialogUtils

class MessageListFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels {
        HomeViewModelFactory(
            (requireActivity().application as App).userRepository,
            (requireActivity().application as App).chatRepository,
            (requireActivity().application as App).sessionManager
        )
    }

    private var _binding: FragmentMessageListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMessageListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(
            onItemClick = { item ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("TARGET_ID", item.id)
                    putExtra("TARGET_NAME", item.name)
                    putExtra("CHAT_TYPE", item.chatType)
                }
                startActivity(intent)
            },
            onMoreClick = { item, anchorView ->
                showActionMenu(anchorView, item)
            }
        )

        binding.rvConversations.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MessageListFragment.adapter
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.conversations.collectLatest { list ->
                    if (list.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvConversations.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvConversations.visibility = View.VISIBLE
                        adapter.submitList(list)
                    }
                }
            }
        }
    }

    // Hàm hiển thị Menu popup
    private fun showActionMenu(view: View, item: ConversationItem) {
        val popup = PopupMenu(requireContext(), view)

        // Kiểm tra loại chat để hiển thị menu phù hợp
        if (item.chatType == "private") {
            popup.menu.add(getString(R.string.action_unfriend))
        } else if (item.chatType == "group") {
            popup.menu.add(getString(R.string.action_leave))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                getString(R.string.action_unfriend) -> {
                    viewModel.unfriendUser(item.id)
                    true
                }
                getString(R.string.action_leave) -> {
                    handleGroupActionClick(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun handleGroupActionClick(item: ConversationItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val role = viewModel.getGroupRole(item.id)

            Log.d("CheckRole", "Group: ${item.id}, User Role: $role")

            if (role != null && role.equals("admin", ignoreCase = true)) {
                DialogUtils.showConfirmationDialog(
                    requireContext(),
                    getString(R.string.dialog_dissolve_group_title),
                    getString(R.string.dialog_dissolve_group_msg),
                    positiveLabel = getString(R.string.action_dissolve)
                ) {
                    viewModel.dissolveGroup(item.id)
                }
            } else {
                DialogUtils.showConfirmationDialog(
                    requireContext(),
                    getString(R.string.dialog_leave_group_title),
                    getString(R.string.dialog_leave_group_msg),
                    positiveLabel = getString(R.string.action_leave)
                ) {
                    viewModel.leaveGroup(item.id)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}