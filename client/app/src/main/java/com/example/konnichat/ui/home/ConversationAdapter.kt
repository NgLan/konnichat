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
import android.widget.ImageButton // [THÊM] Import này
import com.bumptech.glide.Glide
import com.example.konnichat.databinding.ItemConversationBinding

class ConversationAdapter(
    private val onItemClick: (ConversationItem) -> Unit,
    private val onMoreClick: (ConversationItem, View) -> Unit
) : ListAdapter<ConversationItem, ConversationAdapter.ConversationViewHolder>(DiffCallback) {

    class ConversationViewHolder(val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root)

//    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
//        val tvName: TextView = itemView.findViewById(R.id.tvName)
//        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
//        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
//        val viewStatusDot: View = itemView.findViewById(R.id.viewStatusDot)
//        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
//    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )

//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvName.text = item.name

            // Xử lý tin nhắn cuối
            if (!item.lastMessage.isNullOrEmpty()) {
                tvLastMessage.text = item.lastMessage
                tvLastMessage.visibility = View.VISIBLE
            } else {
                tvLastMessage.text = context.getString(R.string.msg_no_message)
                tvLastMessage.visibility = View.VISIBLE
            }

            // Xử lý thời gian
            if (item.lastMessageTime != null) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvTime.text = sdf.format(item.lastMessageTime)
                tvTime.visibility = View.VISIBLE
            } else {
                tvTime.visibility = View.GONE
            }

            // Xử lý Avatar và Status
            // Dùng Glide để load ảnh nếu có URL
            if (!item.avatar.isNullOrEmpty()) {
                Glide.with(context)
                    .load(item.avatar)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .circleCrop()
                    .into(imgAvatar)
            } else {
                imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            }

            if (item.chatType == "group") {
                viewStatusDot.visibility = View.GONE
            } else {
                // Private chat
                viewStatusDot.visibility = if (item.isOnline) View.VISIBLE else View.GONE
            }

            // Sự kiện click
            root.setOnClickListener { onItemClick(item) }
            btnMore.setOnClickListener { onMoreClick(item, it) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ConversationItem>() {
        override fun areItemsTheSame(
            oldItem: ConversationItem,
            newItem: ConversationItem
        ): Boolean {
            // Cần so sánh cả ID và Type vì ID có thể trùng giữa User và Group
            return oldItem.id == newItem.id && oldItem.chatType == newItem.chatType
        }

        override fun areContentsTheSame(
            oldItem: ConversationItem,
            newItem: ConversationItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}