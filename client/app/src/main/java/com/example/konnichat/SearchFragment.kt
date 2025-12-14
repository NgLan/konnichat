package com.example.konnichat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.adapter.UserSearchAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 1. Kế thừa Interface lắng nghe (FriendRequestCallback)
class SearchFragment : Fragment() {

    private var currentUserId: Int = -1
    private lateinit var etKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var rvResults: RecyclerView
    private lateinit var adapter: UserSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        currentUserId = arguments?.getInt("USER_ID") ?: -1

        etKeyword = view.findViewById(R.id.etSearchKeyword)
        btnSearch = view.findViewById(R.id.btnSearch)
        rvResults = view.findViewById(R.id.rvSearchResults)

        setupRecyclerView()

        btnSearch.setOnClickListener {
            val keyword = etKeyword.text.toString().trim()
            if (keyword.isNotEmpty()) {
                performSearch(keyword)
            }
        }
        return view
    }

    // ------------------------------------------

    private fun setupRecyclerView() {
        rvResults.layoutManager = LinearLayoutManager(context)
        adapter = UserSearchAdapter(arrayListOf(), hashSetOf()) { targetId ->
            sendFriendRequest(targetId)
        }
        rvResults.adapter = adapter
    }

    private fun performSearch(keyword: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val searchResults = NativeClient.searchUsers(keyword, currentUserId)

            // Lấy danh sách bạn bè để check trạng thái nút bấm
            val friends = NativeClient.getFriendList(currentUserId)
            val friendIdsSet = HashSet<Int>()
            if (friends != null) {
                for (friend in friends) {
                    friendIdsSet.add(friend.id)
                }
            }

            withContext(Dispatchers.Main) {
                if (searchResults != null && searchResults.isNotEmpty()) {
                    rvResults.visibility = View.VISIBLE
                    adapter.updateData(searchResults, friendIdsSet)
                } else {
                    rvResults.visibility = View.GONE
                    Toast.makeText(context, "Không tìm thấy ai tên '$keyword'", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendFriendRequest(targetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = NativeClient.sendFriendRequest(currentUserId, targetId)

            withContext(Dispatchers.Main) {
                when {
                    result > 0 -> Toast.makeText(context, "Đã gửi lời mời!", Toast.LENGTH_SHORT).show()
                    result == -1 -> Toast.makeText(context, "Đã là bạn bè rồi!", Toast.LENGTH_SHORT).show()
                    result == -2 -> Toast.makeText(context, "Đã gửi lời mời trước đó!", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}