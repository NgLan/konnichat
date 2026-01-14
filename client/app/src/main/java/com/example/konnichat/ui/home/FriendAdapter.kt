package com.example.konnichat.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.konnichat.R
import com.example.konnichat.data.local.entity.UserEntity
import com.example.konnichat.databinding.ItemFriendBinding

class FriendAdapter(
    private val onItemClick: (UserEntity) -> Unit,
    private val onUnfriendClick: (UserEntity) -> Unit // Callback hủy kết bạn
) : ListAdapter<UserEntity, FriendAdapter.FriendViewHolder>(DiffCallback) {

    class FriendViewHolder(val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvFriendName.text = user.name

            // Load Avatar dùng Glide
            if (!user.avatarUrl.isNullOrEmpty()) {
                Glide.with(context)
                    .load(user.avatarUrl)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .circleCrop()
                    .into(imgAvatar)
            } else {
                imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            }

            // Xử lý trạng thái Online/Offline
            if (user.isOnline) {
                tvFriendStatusText.text = context.getString(R.string.status_online)
                tvFriendStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_online))
                viewStatusDot.setBackgroundResource(R.drawable.status_online_bg)
            } else {
                tvFriendStatusText.text = context.getString(R.string.status_offline)
                tvFriendStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_offline))
                viewStatusDot.setBackgroundResource(R.drawable.status_offline_bg)
            }

            // Click vào item -> Chat
            root.setOnClickListener { onItemClick(user) }

            // Click vào nút 3 chấm -> Hiện Menu
            btnMore.setOnClickListener { view ->
                showPopupMenu(view, user)
            }
        }
    }

    private fun showPopupMenu(view: View, user: UserEntity) {
        val context = view.context
        val popup = PopupMenu(context, view)

        val unfriendTitle = context.getString(R.string.action_unfriend)
        popup.menu.add(unfriendTitle)

        popup.setOnMenuItemClickListener { item ->
            if (item.title == unfriendTitle) {
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