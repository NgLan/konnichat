package com.example.konnichat.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.UserSearchInfo

class UserSearchAdapter(
    private var userList: ArrayList<UserSearchInfo>,
    private var friendIds: Set<Int>, // Danh sách ID đã là bạn
    private val onAddFriendClick: (Int) -> Unit // Callback khi bấm nút
) : RecyclerView.Adapter<UserSearchAdapter.SearchViewHolder>() {

    class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDetail: TextView = view.findViewById(R.id.tvDetail)
        val btnAction: Button = view.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        // Load layout mới vừa tạo
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val user = userList[position]
        holder.tvName.text = user.name
        holder.tvDetail.text = user.email // Hiển thị email

        // --- LOGIC QUAN TRỌNG: Kiểm tra quan hệ bạn bè ---
        if (friendIds.contains(user.id)) {
            // Đã là bạn bè -> Nút Xám, không bấm được
            holder.btnAction.text = "Bạn bè"
            holder.btnAction.setBackgroundColor(Color.GRAY)
            holder.btnAction.isEnabled = false
        } else {
            // Chưa là bạn -> Nút Xanh, bấm để gửi kết bạn
            holder.btnAction.text = "Kết bạn"
            holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50")) // Màu xanh lá
            holder.btnAction.isEnabled = true

            holder.btnAction.setOnClickListener {
                // Click xong thì tạm thời disable để tránh spam
                holder.btnAction.text = "Đã gửi"
                holder.btnAction.isEnabled = false
                holder.btnAction.setBackgroundColor(Color.LTGRAY)
                onAddFriendClick(user.id)
            }
        }
    }

    override fun getItemCount() = userList.size

    // Hàm cập nhật dữ liệu mới từ Fragment
    fun updateData(newList: ArrayList<UserSearchInfo>, newFriendIds: Set<Int>) {
        userList = newList
        friendIds = newFriendIds
        notifyDataSetChanged()
    }
}