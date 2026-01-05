package com.example.konnichat.ui.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.R
import com.example.konnichat.data.local.entity.UserEntity

class SelectFriendAdapter(
    private val onSelectionChanged: (Int) -> Unit // Callback trả về số lượng người đã chọn
) : ListAdapter<UserEntity, SelectFriendAdapter.SelectViewHolder>(DiffCallback) {

    // Set chứa các ID đã chọn
    private val selectedIds = HashSet<Int>()

    fun getSelectedUserIds(): List<Int> {
        return selectedIds.toList()
    }

    class SelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        // Lưu ý: Layout item_friend_selection phải có CheckBox với ID này
        val chkSelect: CheckBox = itemView.findViewById(R.id.chkSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectViewHolder {
        // [QUAN TRỌNG] Bạn cần tạo layout item_friend_selection.xml
        // (Copy item_friend.xml, đổi ImageButton thành CheckBox id: chkSelect)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_selection, parent, false)
        return SelectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectViewHolder, position: Int) {
        val user = getItem(position)

        holder.tvName.text = user.name
        holder.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)

        // Tránh lỗi trạng thái khi scroll RecyclerView
        holder.chkSelect.setOnCheckedChangeListener(null)

        // Set trạng thái hiện tại
        holder.chkSelect.isChecked = selectedIds.contains(user.serverId)

        // Lắng nghe sự kiện click vào cả item
        holder.itemView.setOnClickListener {
            toggleSelection(user.serverId)
            notifyItemChanged(position) // Update UI chỉ item này
        }

        // Lắng nghe sự kiện click vào checkbox
        holder.chkSelect.setOnClickListener {
            toggleSelection(user.serverId)
            // Không cần notify vì checkbox tự đổi trạng thái visual
        }
    }

    private fun toggleSelection(userId: Int) {
        if (selectedIds.contains(userId)) {
            selectedIds.remove(userId)
        } else {
            selectedIds.add(userId)
        }
        // Báo ra ngoài biết số lượng thay đổi (để update nút "Tạo (3)")
        onSelectionChanged(selectedIds.size)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<UserEntity>() {
        override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem.serverId == newItem.serverId
        override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem == newItem
    }
}