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

class SearchUserAdapter(
    private val onAddFriendClick: (Int) -> Unit,
    private val onChatClick: (Int) -> Unit
) : ListAdapter<UserSearchUiModel, SearchUserAdapter.SearchViewHolder>(DiffCallback) {

    class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvSearchName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvSearchEmail)
        val btnAction: Button = itemView.findViewById(R.id.btnSearchAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val user = getItem(position)
        holder.tvName.text = user.name
        holder.tvEmail.text = user.email

        // Click vào item vẫn mở chat (xem lịch sử cũ)
        holder.itemView.setOnClickListener { onChatClick(user.id) }

        // --- LOGIC HIỂN THỊ NÚT DỰA TRÊN STATUS TỪ SERVER ---
        when (user.status) {
            UserSearchUiModel.STATUS_FRIEND -> {
                // Đã là bạn
                setupButton(holder.btnAction, "Bạn bè", "#757575", true) // Xám, Bấm để chat
                holder.btnAction.setOnClickListener { onChatClick(user.id) }
            }
            UserSearchUiModel.STATUS_SENT -> {
                // Đã gửi lời mời
                setupButton(holder.btnAction, "Đã gửi", "#E0E0E0", false) // Xám nhạt, Disable
                holder.btnAction.setTextColor(Color.GRAY)
                holder.btnAction.setOnClickListener { null } // Không làm gì
            }
            UserSearchUiModel.STATUS_RECEIVED -> {
                // Người ta gửi cho mình (Hiếm gặp khi search nhưng cứ handle)
                setupButton(holder.btnAction, "Phản hồi", "#2196F3", true) // Xanh dương
                // Bấm vào thì nên mở trang Lời mời (hoặc chấp nhận luôn tùy logic)
                holder.btnAction.setOnClickListener { /* TODO: Mở dialog chấp nhận */ }
            }
            else -> { // STATUS_NONE (Người lạ)
                setupButton(holder.btnAction, "Kết bạn", "#4CAF50", true) // Xanh lá
                holder.btnAction.setOnClickListener { onAddFriendClick(user.id) }
            }
        }
    }

    // Helper để set giao diện nút nhanh gọn
    private fun setupButton(btn: Button, text: String, colorHex: String, isEnabled: Boolean) {
        btn.text = text
        btn.isEnabled = isEnabled
        btn.setBackgroundColor(Color.parseColor(colorHex))
        if (isEnabled) btn.setTextColor(Color.WHITE)
    }
    companion object DiffCallback : DiffUtil.ItemCallback<UserSearchUiModel>() {
        override fun areItemsTheSame(oldItem: UserSearchUiModel, newItem: UserSearchUiModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UserSearchUiModel, newItem: UserSearchUiModel) = oldItem == newItem
    }
}