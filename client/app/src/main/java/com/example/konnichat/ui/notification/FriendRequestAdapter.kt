package com.example.konnichat.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.remote.dto.PendingRequestDto

class FriendRequestAdapter(
    private val onAccept: (PendingRequestDto) -> Unit,
    private val onDeny: (PendingRequestDto) -> Unit
) : ListAdapter<PendingRequestDto, FriendRequestAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvSenderName)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnDeny: Button = itemView.findViewById(R.id.btnDeny)
        // val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar) // Nếu có avatar
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Sử dụng layout item_friend_request.xml mà tôi đã gửi ở lượt trước
        // Nếu chưa có, hãy báo tôi gửi lại layout XML
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.tvName.text = item.senderName

        holder.btnAccept.setOnClickListener { onAccept(item) }
        holder.btnDeny.setOnClickListener { onDeny(item) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PendingRequestDto>() {
        override fun areItemsTheSame(oldItem: PendingRequestDto, newItem: PendingRequestDto): Boolean {
            return oldItem.requestId == newItem.requestId
        }

        override fun areContentsTheSame(oldItem: PendingRequestDto, newItem: PendingRequestDto): Boolean {
            return oldItem == newItem
        }
    }
}