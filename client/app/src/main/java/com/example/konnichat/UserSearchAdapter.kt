package com.example.konnichat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.UserSearchInfo
import com.example.konnichat.databinding.ItemUserSearchBinding // Giả sử bạn đã tạo XML layout này

class UserSearchAdapter(
    private var userList: ArrayList<UserSearchInfo>,
    private val onAddFriendClick: (Int) -> Unit // Callback trả về ID người được chọn
) : RecyclerView.Adapter<UserSearchAdapter.UserViewHolder>() {

    inner class UserViewHolder(val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.binding.apply {
            tvName.text = user.name
            tvEmail.text = user.email // Hiển thị email để phân biệt
            btnAddFriend.setOnClickListener {
                onAddFriendClick(user.id) // Gửi sự kiện ra ngoài Activity xử lý
            }
        }
    }

    override fun getItemCount() = userList.size

    fun updateData(newList: ArrayList<UserSearchInfo>) {
        userList = newList
        notifyDataSetChanged()
    }
}