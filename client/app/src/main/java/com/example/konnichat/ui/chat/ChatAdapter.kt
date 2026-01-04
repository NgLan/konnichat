package com.example.konnichat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(private val currentUserId: Int) :
    ListAdapter<MessageEntity, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        // Nếu người gửi là mình -> Loại SENT, ngược lại -> RECEIVED
        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            // Layout tin nhắn mình gửi (Bên phải)
            val view = inflater.inflate(R.layout.item_chat_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            // Layout tin nhắn họ gửi (Bên trái)
            val view = inflater.inflate(R.layout.item_chat_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    // --- ViewHolder cho tin nhắn gửi đi ---
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ánh xạ đúng ID trong item_chat_sent.xml
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(msg: MessageEntity) {
            tvContent.text = msg.content

            // Format ngày giờ: 14:30
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(msg.createdAt)

            // Đảm bảo hiện thời gian (vì trong XML có thể set gone)
            tvTime.visibility = View.VISIBLE
        }
    }

    // --- ViewHolder cho tin nhắn nhận được ---
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ánh xạ đúng ID trong item_chat_received.xml
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(msg: MessageEntity) {
            tvContent.text = msg.content

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(msg.createdAt)
        }
    }

    // --- DiffUtil để tối ưu hiệu năng RecyclerView ---
    class MessageDiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            // So sánh ID server trước
            if (oldItem.serverId > 0 && newItem.serverId > 0) {
                return oldItem.serverId == newItem.serverId
            }
            // Nếu là tin nhắn đang gửi (serverId âm hoặc 0), so sánh timestamp và content
            return oldItem.createdAt.time == newItem.createdAt.time && oldItem.content == newItem.content
        }

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
            return oldItem == newItem
        }
    }
}