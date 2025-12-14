package com.example.konnichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Thêm callback vào Constructor: (Friend) -> Unit
class FriendAdapter(
    private val friendList: List<Friend>,
    private val onUnfriendClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val tvStatusText: TextView = view.findViewById(R.id.tvFriendStatusText)
        val viewStatusDot: View = view.findViewById(R.id.viewStatusDot)
        val btnMenu: ImageButton = view.findViewById(R.id.btnMoreMenu) // Ánh xạ nút mới
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendList[position]
        holder.tvName.text = friend.name

        // Logic hiển thị trạng thái (Giữ nguyên)
        if (friend.isOnline) {
            holder.tvStatusText.text = "Đang hoạt động"
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_online_bg)
        } else {
            holder.tvStatusText.text = "Ngoại tuyến"
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_offline_bg)
        }

        // --- XỬ LÝ NÚT 3 CHẤM ---
        holder.btnMenu.setOnClickListener { view ->
            // Tạo PopupMenu gắn vào nút bấm
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.menu_friend_item) // Load file menu tạo ở Bước 1

            // Bắt sự kiện chọn item trong menu
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_unfriend -> {
                        // Gọi callback báo ra ngoài Fragment
                        onUnfriendClick(friend)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        holder.itemView.setOnClickListener {
            // TODO: Mở chat (giữ nguyên logic cũ)
        }
    }

    override fun getItemCount() = friendList.size
}