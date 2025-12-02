package com.example.konnichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FriendAdapter(private val friendList: List<Friend>) :
    RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val tvStatusText: TextView = view.findViewById(R.id.tvFriendStatusText)
        val viewStatusDot: View = view.findViewById(R.id.viewStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendList[position]

        holder.tvName.text = friend.name

        if (friend.isOnline) {
            holder.tvStatusText.text = "Đang hoạt động"
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_online_bg)
        } else {
            holder.tvStatusText.text = "Ngoại tuyến"
            holder.viewStatusDot.setBackgroundResource(R.drawable.status_offline_bg)
        }

        // Sự kiện click vào item (để sau này làm chức năng Chat)
        holder.itemView.setOnClickListener {
            // TODO: Mở màn hình chat với user này
        }
    }

    override fun getItemCount() = friendList.size
}
