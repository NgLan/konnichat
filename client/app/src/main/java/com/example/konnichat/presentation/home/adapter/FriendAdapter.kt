package com.example.konnichat.presentation.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.domain.enums.OnlineStatus
import com.example.konnichat.domain.model.User

class FriendAdapter(
    private var friendList: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    fun updateData(newList: List<User>) {
        friendList = newList
        notifyDataSetChanged()
    }

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val tvStatus: TextView = view.findViewById(R.id.tvFriendStatusText)
        val viewDot: View = view.findViewById(R.id.viewStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = friendList[position]
        holder.tvName.text = user.name

        if (user.isOnline == OnlineStatus.ONLINE) {
            holder.tvStatus.text = "Online"
            holder.viewDot.setBackgroundResource(R.drawable.status_online_bg)
        } else {
            holder.tvStatus.text = "Offline"
            holder.viewDot.setBackgroundResource(R.drawable.status_offline_bg)
        }

        holder.itemView.setOnClickListener { onItemClick(user) }
    }

    override fun getItemCount() = friendList.size
}
