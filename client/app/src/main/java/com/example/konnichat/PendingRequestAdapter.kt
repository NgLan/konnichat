package com.example.konnichat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.PendingRequest
import com.example.konnichat.databinding.ItemPendingRequestBinding

class PendingRequestAdapter(
    private var requestList: ArrayList<PendingRequest>,
    private val onResponse: (Int, Boolean) -> Unit // Trả về RequestID và trạng thái (Accept/Deny)
) : RecyclerView.Adapter<PendingRequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(val binding: ItemPendingRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemPendingRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requestList[position]
        holder.binding.apply {
            tvSenderName.text = "Lời mời từ: ${request.senderName}"

            btnAccept.setOnClickListener { onResponse(request.requestId, true) }
            btnDeny.setOnClickListener { onResponse(request.requestId, false) }
        }
    }

    override fun getItemCount() = requestList.size

    fun updateData(newList: ArrayList<PendingRequest>) {
        requestList = newList
        notifyDataSetChanged()
    }
}