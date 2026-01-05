package com.example.konnichat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.model.MessageWithSender
import com.example.konnichat.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(private val currentUserId: Int) :

    ListAdapter<MessageWithSender, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 9
    }

//    override fun getItemViewType(position: Int): Int {
//        val message = getItem(position)
//        val msg = message.message
//        // Nếu người gửi là mình -> Loại SENT, ngược lại -> RECEIVED
//        if (message.msgType == 9) return VIEW_TYPE_SYSTEM
//        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
//    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        val msg = item.message

        // [THÊM] Kiểm tra msgType hệ thống
        if (msg.msgType == 9) return VIEW_TYPE_SYSTEM

        return if (msg.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            // Layout tin nhắn mình gửi (Bên phải)
            val view = inflater.inflate(R.layout.item_chat_sent, parent, false)
            SentMessageViewHolder(view)
        } else if (viewType == VIEW_TYPE_RECEIVED) {
            // Layout tin nhắn họ gửi (Bên trái)
            val view = inflater.inflate(R.layout.item_chat_received, parent, false)
            ReceivedMessageViewHolder(view)
        } else if (viewType == VIEW_TYPE_SYSTEM){
            val view = inflater.inflate(R.layout.item_chat_system, parent, false)
            SystemMessageViewHolder(view)
        } else {
            throw IllegalArgumentException("Unknown viewType")
        }
    }

//    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//        val message = getItem(position)
//        if (holder is SentMessageViewHolder) {
//            holder.bind(message)
//        } else if (holder is ReceivedMessageViewHolder) {
//            holder.bind(message)
//        }
//    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is SentMessageViewHolder -> holder.bind(item.message)
            is ReceivedMessageViewHolder -> holder.bind(item.message)
            // [THÊM] Bind tin nhắn hệ thống
            is SystemMessageViewHolder -> holder.bind(item)
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

    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvSystemMessage)

        fun bind(item: MessageWithSender) {
            // Logic ghép tên: "Nguyễn Văn A" + " " + "đã tạo nhóm"
            val senderName = item.senderName ?: "Người dùng ${item.message.senderId}"
            val content = item.message.content

            val fullText = "$senderName $content"
            tvContent.text = fullText
        }
    }

    // --- DiffUtil để tối ưu hiệu năng RecyclerView ---
//    class MessageDiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
//        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
//            // So sánh ID server trước
//            if (oldItem.serverId > 0 && newItem.serverId > 0) {
//                return oldItem.serverId == newItem.serverId
//            }
//            // Nếu là tin nhắn đang gửi (serverId âm hoặc 0), so sánh timestamp và content
//            return oldItem.createdAt.time == newItem.createdAt.time && oldItem.content == newItem.content
//        }
//
//        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity): Boolean {
//            return oldItem == newItem
//        }
//    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageWithSender>() {
        override fun areItemsTheSame(oldItem: MessageWithSender, newItem: MessageWithSender): Boolean {
            if (oldItem.message.serverId > 0 && newItem.message.serverId > 0) {
                return oldItem.message.serverId == newItem.message.serverId
            }
            return oldItem.message.createdAt.time == newItem.message.createdAt.time
                    && oldItem.message.content == newItem.message.content
        }

        override fun areContentsTheSame(oldItem: MessageWithSender, newItem: MessageWithSender): Boolean {
            return oldItem == newItem
        }
    }
}