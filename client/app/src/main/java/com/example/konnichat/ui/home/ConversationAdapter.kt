package com.example.konnichat.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.model.ConversationItem
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationAdapter(
    private val onItemClick: (ConversationItem) -> Unit
) : ListAdapter<ConversationItem, ConversationAdapter.ConversationViewHolder>(DiffCallback) {

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val viewStatusDot: View = itemView.findViewById(R.id.viewStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val item = getItem(position)

        holder.tvName.text = item.name

        // Xử lý hiển thị tin nhắn cuối
        if (item.lastMessage != null) {
            holder.tvLastMessage.text = item.lastMessage
            holder.tvLastMessage.visibility = View.VISIBLE
        } else {
            holder.tvLastMessage.text = "Chưa có tin nhắn"
            holder.tvLastMessage.visibility = View.VISIBLE
        }

        // Xử lý thời gian
        if (item.lastMessageTime != null) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.tvTime.text = sdf.format(item.lastMessageTime)
            holder.tvTime.visibility = View.VISIBLE
        } else {
            holder.tvTime.visibility = View.GONE
        }

        // Xử lý Avatar và Status dựa trên chatType
        if (item.chatType == "group") {
            // Nếu là Group: Dùng icon mặc định khác (ví dụ ic_launcher_foreground hoặc hình người nhóm)
            // Tạm thời dùng icon launcher round
            holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            // Group thì ẩn chấm Online
            holder.viewStatusDot.visibility = View.GONE
        } else {
            // Nếu là Private (User)
            holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            // Hiện chấm online nếu user online
            if (item.isOnline) {
                holder.viewStatusDot.visibility = View.VISIBLE
            } else {
                holder.viewStatusDot.visibility = View.GONE
            }
        }

        // Click sự kiện
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ConversationItem>() {
        override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            // Cần so sánh cả ID và Type vì ID có thể trùng giữa User và Group (do server ID riêng biệt)
            return oldItem.id == newItem.id && oldItem.chatType == newItem.chatType
        }

        override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            return oldItem == newItem
        }
    }
}