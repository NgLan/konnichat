package com.example.konnichat.ui.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.model.GroupMemberWithUser

class GroupMemberAdapter :
    ListAdapter<GroupMemberWithUser, GroupMemberAdapter.MemberViewHolder>(DiffCallback) {

    class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvRole: TextView = itemView.findViewById(R.id.tvRole)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val item = getItem(position)
        val user = item.user

        holder.tvName.text = user.name
        holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round) // Placeholder

        // Hiển thị trạng thái
        if (user.isOnline) {
            holder.tvStatus.text = "Online"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            holder.tvStatus.text = "Offline"
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#888888"))
        }

        // Hiển thị vai trò
        if (item.role == "admin") {
            holder.tvRole.visibility = View.VISIBLE
            holder.tvRole.text = "Trưởng nhóm"
        } else {
            holder.tvRole.visibility = View.GONE
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<GroupMemberWithUser>() {
        override fun areItemsTheSame(oldItem: GroupMemberWithUser, newItem: GroupMemberWithUser): Boolean {
            return oldItem.user.serverId == newItem.user.serverId
        }

        override fun areContentsTheSame(oldItem: GroupMemberWithUser, newItem: GroupMemberWithUser): Boolean {
            return oldItem == newItem
        }
    }
}