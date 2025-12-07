package com.example.konnichat.presentation.chat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.domain.model.Message

class ChatAdapter(
    private var messageList: List<Message>,
    private val currentUserId: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    fun updateData(newList: List<Message>) {
        messageList = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messageList[position]
        if (holder is SentViewHolder) {
            holder.bind(msg)
        } else if (holder is ReceivedViewHolder) {
            holder.bind(msg)
        }
    }

    override fun getItemCount() = messageList.size

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val content: TextView = view.findViewById(R.id.tvContent)
        private val time: TextView = view.findViewById(R.id.tvTime)
        fun bind(msg: Message) {
            content.text = msg.content
            time.text = msg.createdAt
        }
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val content: TextView = view.findViewById(R.id.tvContent)
        private val time: TextView = view.findViewById(R.id.tvTime)
        fun bind(msg: Message) {
            content.text = msg.content
            time.text = msg.createdAt
        }
    }
}
