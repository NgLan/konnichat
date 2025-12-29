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

        // --- CẢI TIẾN 1: Click vào bất cứ đâu trên dòng này đều mở chat ---
        // Giúp xem lại tin nhắn cũ kể cả khi nút bấm đang là "Kết bạn"
        holder.itemView.setOnClickListener {
            onChatClick(user.id)
        }

        // --- CẢI TIẾN 2: Logic hiển thị nút bấm ---
        if (user.isFriend) {
            // Trạng thái: Đã là bạn bè
            holder.btnAction.text = "Bạn bè"
            // Đổi màu Xám để phân biệt (Thể hiện trạng thái tĩnh)
            holder.btnAction.setBackgroundColor(Color.parseColor("#757575"))

            // Nếu bấm vào nút "Bạn bè", ta cũng có thể cho mở chat hoặc mở profile
            holder.btnAction.setOnClickListener { onChatClick(user.id) }
        } else {
            // Trạng thái: Chưa kết bạn / Đã hủy kết bạn
            holder.btnAction.text = "Kết bạn"
            // Màu Xanh lá (Hành động mời)
            holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50"))

            // Bấm nút này thì gửi lời mời
            holder.btnAction.setOnClickListener { onAddFriendClick(user.id) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<UserSearchUiModel>() {
        override fun areItemsTheSame(oldItem: UserSearchUiModel, newItem: UserSearchUiModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UserSearchUiModel, newItem: UserSearchUiModel) = oldItem == newItem
    }
}