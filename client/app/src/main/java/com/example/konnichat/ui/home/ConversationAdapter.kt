package com.example.konnichat.ui.home

import android.graphics.Color
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

// Adapter sử dụng ListAdapter để tối ưu hiệu năng khi list thay đổi
class ConversationAdapter(
    private val onItemClick: (ConversationItem) -> Unit
) : ListAdapter<ConversationItem, ConversationAdapter.ConversationViewHolder>(DiffCallback) {

    // ViewHolder nắm giữ các View trong layout
    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar) // Tui sẽ dùng ID này trong XML
        val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage) // ID mới
        val tvStatus: TextView = itemView.findViewById(R.id.tvFriendStatusText) // Tận dụng làm chỗ hiện giờ
        val viewStatusDot: View = itemView.findViewById(R.id.viewStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        // Inflate layout item_conversation (Tui sẽ cung cấp XML bên dưới)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val item = getItem(position)

        // 1. Gán Tên
        holder.tvName.text = item.friendName

        // 2. Gán Tin nhắn cuối (Nếu null thì để chuỗi rỗng)
        holder.tvLastMessage.text = item.lastMessage ?: "Chưa có tin nhắn"

        // 3. Gán Avatar (Tạm thời set cứng ảnh mặc định vì chưa có thư viện load ảnh như Glide/Coil)
        // Sau này bạn cài Glide thì sửa chỗ này sau.
        holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)

        // 4. Xử lý trạng thái Online (Chấm xanh)
        if (item.isOnline) {
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_online_bg)
        } else {
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_offline_bg)
        }

        // 5. Hiển thị giờ (Format đơn giản)
        item.lastMessageTime?.let {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.tvStatus.text = sdf.format(it)
        } ?: run {
            holder.tvStatus.text = ""
        }

        // Click vào dòng chat
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    // Callback để so sánh dữ liệu cũ/mới giúp RecyclerView render mượt
    companion object DiffCallback : DiffUtil.ItemCallback<ConversationItem>() {
        override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            return oldItem.friendId == newItem.friendId
        }

        override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            return oldItem == newItem
        }
    }
}