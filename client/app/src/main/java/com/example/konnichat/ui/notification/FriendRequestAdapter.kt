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
import com.example.konnichat.databinding.ItemFriendRequestBinding

class FriendRequestAdapter(
    private val onAccept: (PendingRequestDto) -> Unit,
    private val onDeny: (PendingRequestDto) -> Unit
) : ListAdapter<PendingRequestDto, FriendRequestAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        with(holder.binding) {
            tvSenderName.text = item.senderName
            btnAccept.setOnClickListener { onAccept(item) }
            btnDeny.setOnClickListener { onDeny(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PendingRequestDto>() {
        override fun areItemsTheSame(
            oldItem: PendingRequestDto,
            newItem: PendingRequestDto
        ): Boolean {
            return oldItem.requestId == newItem.requestId
        }

        override fun areContentsTheSame(
            oldItem: PendingRequestDto,
            newItem: PendingRequestDto
        ): Boolean {
            return oldItem == newItem
        }
    }
}