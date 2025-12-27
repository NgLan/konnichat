// File: client/app/src/main/java/com/example/konnichat/ui/home/FriendAdapter.kt
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
import com.example.konnichat.data.local.entity.UserEntity

class FriendAdapter(
    private val onItemClick: (UserEntity) -> Unit
) : ListAdapter<UserEntity, FriendAdapter.FriendViewHolder>(DiffCallback) {

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar) // ID trong item_friend.xml là mặc định hoặc phải thêm id
        val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvFriendStatusText)
        val viewStatusDot: View = itemView.findViewById(R.id.viewStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false) // Dùng layout item_friend
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = getItem(position)

        holder.tvName.text = user.name
        holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round) // Ảnh tạm

        if (user.isOnline) {
            holder.tvStatus.text = "Online"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Xanh lá
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_online_bg)
        } else {
            holder.tvStatus.text = "Offline"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#888888")) // Xám
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_offline_bg)
        }

        holder.itemView.setOnClickListener { onItemClick(user) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<UserEntity>() {
        override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem.serverId == newItem.serverId
        override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem == newItem
    }
}