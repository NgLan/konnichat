package com.example.konnichat

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

        // 1. Lấy User ID từ Activity truyền sang
        currentUserId = arguments?.getInt("USER_ID") ?: -1

        rvFriends = view.findViewById(R.id.rvFriends)
        tvEmptyState = view.findViewById(R.id.tvEmptyFriends) // Lưu ý ID trong XML fragment
        rvFriends.layoutManager = LinearLayoutManager(context)

        loadFriendsData()

        return view
    }

    private fun loadFriendsData() {
        if (currentUserId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val friendList = NativeClient.getFriendList(currentUserId)
                withContext(Dispatchers.Main) {
                    if (friendList != null && friendList.isNotEmpty()) {
                        tvEmptyState.visibility = View.GONE
                        rvFriends.visibility = View.VISIBLE
                        rvFriends.adapter = FriendAdapter(friendList)
                        // TODO: Thêm sự kiện LongClick để hủy kết bạn (Task 10)
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
}