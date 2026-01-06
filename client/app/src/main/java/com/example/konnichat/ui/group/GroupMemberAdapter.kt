package com.example.konnichat.ui.group

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
import com.example.konnichat.data.local.model.GroupMemberWithUser

class GroupMemberAdapter(
    private val myUserId: Int,
    private val onKickClick: (Int) -> Unit // Callback khi chọn Kick
) : ListAdapter<GroupMemberWithUser, GroupMemberAdapter.MemberViewHolder>(DiffCallback) {
    private var amIAdmin: Boolean = false

    fun setAdminStatus(isAdmin: Boolean) {
        this.amIAdmin = isAdmin
        notifyDataSetChanged() // Load lại list để hiện nút menu
    }
    class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvRole: TextView = itemView.findViewById(R.id.tvRole)

        val btnMenu: ImageButton = itemView.findViewById(R.id.btnMenu)
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

        if (amIAdmin && user.serverId != myUserId) {
            holder.btnMenu.visibility = View.VISIBLE
            holder.btnMenu.setOnClickListener { view ->
                showPopupMenu(view, user.serverId, user.name)
            }
        } else {
            holder.btnMenu.visibility = View.GONE
        }
    }

    private fun showPopupMenu(view: View, userId: Int, userName: String) {
        val popup = PopupMenu(view.context, view)
        popup.menu.add("Mời ra khỏi nhóm") // Thêm item menu bằng code cho nhanh

        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title == "Mời ra khỏi nhóm") {
                onKickClick(userId) // Gọi callback về Activity
                true
            } else {
                false
            }
        }
        popup.show()
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