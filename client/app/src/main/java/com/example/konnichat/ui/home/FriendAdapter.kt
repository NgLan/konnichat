package com.example.konnichat.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.entity.UserEntity

class FriendAdapter(
    private val onItemClick: (UserEntity) -> Unit,
    private val onUnfriendClick: (UserEntity) -> Unit // Callback hủy kết bạn
) : ListAdapter<UserEntity, FriendAdapter.FriendViewHolder>(DiffCallback) {

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvFriendStatusText)
        val viewStatusDot: View = itemView.findViewById(R.id.viewStatusDot)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore) // Nút 3 chấm
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = getItem(position)

        holder.tvName.text = user.name
        holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)

        if (user.isOnline) {
            holder.tvStatus.text = "Online"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_online_bg)
        } else {
            holder.tvStatus.text = "Offline"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#888888"))
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_offline_bg)
        }

        // Click vào item -> Chat
        holder.itemView.setOnClickListener { onItemClick(user) }

        // Click vào nút 3 chấm -> Hiện Menu Hủy kết bạn
        holder.btnMore.setOnClickListener { view ->
            showPopupMenu(view, user)
        }
    }

    private fun showPopupMenu(view: View, user: UserEntity) {
        val popup = PopupMenu(view.context, view)
        // Thêm menu item bằng code thay vì tạo file xml menu để đơn giản hóa
        popup.menu.add("Hủy kết bạn")

        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Hủy kết bạn") {
                onUnfriendClick(user)
                true
            } else {
                false
            }
        }
        popup.show()
    }

    companion object DiffCallback : DiffUtil.ItemCallback<UserEntity>() {
        override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem.serverId == newItem.serverId
        override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem == newItem
    }
}