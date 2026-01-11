package com.example.konnichat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.model.MessageWithSender
import com.example.konnichat.data.local.entity.MessageEntity
import com.example.konnichat.data.local.entity.ReactionEntity
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(
    private val currentUserId: Int,
    private val onMessageLongClick: (MessageWithSender) -> Unit
) :

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

        if (holder !is SystemMessageViewHolder) {
            holder.itemView.setOnLongClickListener {
                onMessageLongClick(item)
                true // Trả về true để tiêu thụ sự kiện
            }
        }

        when (holder) {
            is SentMessageViewHolder -> holder.bind(item.message, item.reactions.orEmpty())
            // [SỬA] Truyền nguyên cục item (MessageWithSender) thay vì chỉ item.message
            is ReceivedMessageViewHolder -> holder.bind(item)
            is SystemMessageViewHolder -> holder.bind(item)
        }
    }

    // --- ViewHolder cho tin nhắn gửi đi ---
    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ánh xạ đúng ID trong item_chat_sent.xml
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvReaction: TextView = itemView.findViewById(R.id.tvReaction)

        fun bind(msg: MessageEntity, reactions: List<ReactionEntity>) {
            tvContent.text = msg.content

            // Format ngày giờ: 14:30
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(msg.createdAt)

            // Đảm bảo hiện thời gian (vì trong XML có thể set gone)
            tvTime.visibility = View.VISIBLE

            if (msg.status == "revoked") {
                tvContent.text = "Tin nhắn đã bị thu hồi"

                // 1. Đổi background sang loại có viền, nền trong suốt
                tvContent.setBackgroundResource(R.drawable.bg_message_revoked)

                // 2. Chữ màu xám
                tvContent.setTextColor(android.graphics.Color.GRAY)

                // 3. Chữ in nghiêng
                tvContent.setTypeface(null, android.graphics.Typeface.ITALIC)
            } else {
                tvContent.text = msg.content
                tvContent.setTextColor(android.graphics.Color.WHITE) // Màu gốc (check lại layout xml của bạn)
                tvContent.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            bindReactions(tvReaction, reactions)
        }
    }

    private fun getEmojiByCode(code: Int): String {
        return when(code) {
            1 -> "👍"
            2 -> "❤️"
            3 -> "😂"
            4 -> "😮"
            5 -> "😢"
            6 -> "😡"
            else -> "❤️"
        }
    }

    // --- ViewHolder cho tin nhắn nhận được ---
    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Ánh xạ đúng ID trong item_chat_received.xml
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvReaction: TextView = itemView.findViewById(R.id.tvReaction) // Đã thêm vào XML
        fun bind(item: MessageWithSender) { // Lưu ý: Truyền cả item (MessageWithSender) vào
            val msg = item.message

            tvContent.text = msg.content

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(msg.createdAt)

            // [THÊM] Hiển thị Tên người gửi
            // item.senderName lấy từ bảng Users nhờ câu lệnh JOIN trong DAO
            val displayName = item.senderName ?: "User ${msg.senderId}"
            tvSenderName.text = displayName

            // [THÊM] Logic ẩn/hiện tên:
            // Nếu là chat Group: Luôn hiện tên (hoặc logic tùy bạn)
            // Nếu là chat Private: Có thể ẩn tên đi cho gọn (vì chỉ chat với 1 người)
            if (msg.chatType == "group") {
                tvSenderName.visibility = View.VISIBLE
                imgAvatar.visibility = View.VISIBLE
            } else {
                // Chat 1-1 thì ẩn tên và avatar đi cho giống Messenger (hoặc để nguyên tùy ý thích)
                tvSenderName.visibility = View.GONE
                imgAvatar.visibility = View.VISIBLE // Vẫn hiện avatar cho đẹp
            }

            if (msg.status == "revoked") {
                tvContent.text = "Tin nhắn đã bị thu hồi"
                tvContent.setBackgroundResource(R.drawable.bg_message_revoked)

                // 2. Màu xám
                tvContent.setTextColor(android.graphics.Color.GRAY)

                // 3. In nghiêng
                tvContent.setTypeface(null, android.graphics.Typeface.ITALIC)
            } else {
                tvContent.text = msg.content
                tvContent.setTextColor(android.graphics.Color.BLACK) // Màu gốc
                tvContent.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            bindReactions(tvReaction, item.reactions.orEmpty())
            // [THÊM] Hiển thị Avatar (Placeholder)
            imgAvatar.setImageResource(com.example.konnichat.R.mipmap.ic_launcher_round)
        }
    }

    private fun bindReactions(tvReaction: TextView, reactions: List<ReactionEntity>) {
        if (reactions.isNotEmpty()) {
            tvReaction.visibility = View.VISIBLE
            val lastReaction = reactions.last()
            val emoji = getEmojiByCode(lastReaction.iconId)

            if (reactions.size > 1) {
                tvReaction.text = "$emoji ${reactions.size}"
            } else {
                tvReaction.text = emoji
            }
        } else {
            tvReaction.visibility = View.GONE
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