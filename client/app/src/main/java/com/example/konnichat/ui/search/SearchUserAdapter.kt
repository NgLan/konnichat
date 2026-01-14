package com.example.konnichat.ui.search

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.databinding.ItemSearchUserBinding
import androidx.core.graphics.toColorInt

class SearchUserAdapter(
    private val onAddFriendClick: (Int) -> Unit,
    private val onChatClick: (Int) -> Unit
) : ListAdapter<UserSearchUiModel, SearchUserAdapter.SearchViewHolder>(DiffCallback) {

    class SearchViewHolder(val binding: ItemSearchUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemSearchUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val user = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvSearchName.text = user.name
            tvSearchEmail.text = user.email

            // Placeholder avatar
            imgSearchAvatar.setImageResource(R.mipmap.ic_launcher_round)

            // Click vào item
            root.setOnClickListener { onChatClick(user.id) }

            // Logic hiển thị nút
            when (user.status) {
                UserSearchUiModel.STATUS_FRIEND -> {
                    setupButton(
                        btnSearchAction,
                        context.getString(R.string.action_friend_chat),
                        "#757575",
                        true
                    )
                    btnSearchAction.setOnClickListener { onChatClick(user.id) }
                }

                UserSearchUiModel.STATUS_SENT -> {
                    setupButton(
                        btnSearchAction,
                        context.getString(R.string.action_friend_sent),
                        "#E0E0E0",
                        false
                    )
                    btnSearchAction.setTextColor(Color.GRAY)
                    btnSearchAction.setOnClickListener(null)
                }

                UserSearchUiModel.STATUS_RECEIVED -> {
                    setupButton(
                        btnSearchAction,
                        context.getString(R.string.action_friend_response),
                        "#2196F3",
                        true
                    )
                    btnSearchAction.setOnClickListener { /* TODO: Mở trang phản hồi */ }
                }

                else -> { // NONE
                    setupButton(
                        btnSearchAction,
                        context.getString(R.string.action_add_friend),
                        "#4CAF50",
                        true
                    )
                    btnSearchAction.setOnClickListener { onAddFriendClick(user.id) }
                }
            }
        }
    }

    // Helper để set giao diện nút nhanh gọn
    private fun setupButton(btn: Button, text: String, colorHex: String, isEnabled: Boolean) {
        btn.text = text
        btn.isEnabled = isEnabled
        btn.setBackgroundColor(colorHex.toColorInt())
        if (isEnabled) btn.setTextColor(Color.WHITE)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<UserSearchUiModel>() {
        override fun areItemsTheSame(oldItem: UserSearchUiModel, newItem: UserSearchUiModel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UserSearchUiModel, newItem: UserSearchUiModel) =
            oldItem == newItem
    }
}