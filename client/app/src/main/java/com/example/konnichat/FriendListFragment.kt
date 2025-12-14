package com.example.konnichat

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FriendListFragment : Fragment() {

    private var currentUserId: Int = -1
    private lateinit var rvFriends: RecyclerView
    private lateinit var tvEmptyState: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_friend_list, container, false)
        currentUserId = arguments?.getInt("USER_ID") ?: -1

        rvFriends = view.findViewById(R.id.rvFriends)
        tvEmptyState = view.findViewById(R.id.tvEmptyFriends)
        rvFriends.layoutManager = LinearLayoutManager(context)

        loadFriendsData()

        return view
    }

    private var adapter: FriendAdapter? = null

    private fun loadFriendsData() {
        if (currentUserId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Lấy danh sách bạn bè từ Server
                val friendList = NativeClient.getFriendList(currentUserId)

                withContext(Dispatchers.Main) {
                    if (friendList != null && friendList.isNotEmpty()) {
                        tvEmptyState.visibility = View.GONE
                        rvFriends.visibility = View.VISIBLE

                        val arrayList = ArrayList(friendList)
                        adapter = FriendAdapter(arrayList) { friend ->
                            showConfirmUnfriendDialog(friend)
                        }

                        // --- SỬA ĐOẠN NÀY: Truyền callback vào Adapter ---
                        rvFriends.adapter = adapter
                    } else {
                        rvFriends.visibility = View.GONE
                        tvEmptyState.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addNewFriendToUI(friend: Friend) {
        // Ẩn thông báo rỗng nếu có
        tvEmptyState.visibility = View.GONE
        rvFriends.visibility = View.VISIBLE

        // Thêm vào adapter
        adapter?.addFriend(friend)
    }
    // Hàm hiển thị hộp thoại xác nhận
    private fun showConfirmUnfriendDialog(friend: Friend) {
        AlertDialog.Builder(context)
            .setTitle("Hủy kết bạn")
            .setMessage("Bạn có chắc chắn muốn xóa ${friend.name} khỏi danh sách bạn bè không?")
            .setPositiveButton("Xóa") { _, _ ->
                performUnfriend(friend)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // Hàm gọi xuống Native Client để xóa
    private fun performUnfriend(friend: Friend) {
        CoroutineScope(Dispatchers.IO).launch {
            // Gọi hàm Task 10: Unfriend
            val success = NativeClient.unfriend(currentUserId, friend.id)

            withContext(Dispatchers.Main) {
                if (success == 1) {
                    Toast.makeText(context, "Đã xóa ${friend.name}", Toast.LENGTH_SHORT).show()
                    // Load lại danh sách để cập nhật giao diện
                    loadFriendsData()
                } else {
                    Toast.makeText(context, "Lỗi kết nối, thử lại sau!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}