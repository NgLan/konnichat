package com.example.konnichat

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konnichat.adapter.UserSearchAdapter
import com.example.konnichat.databinding.ActivitySearchUserBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchUserActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchUserBinding
    private lateinit var adapter: UserSearchAdapter
    private val currentUserId = 1 // TODO: Lấy ID thật từ Session/Lưu trữ đăng nhập

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnSearch.setOnClickListener {
            val keyword = binding.etSearch.text.toString()
            if (keyword.isNotEmpty()) {
                performSearch(keyword)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = UserSearchAdapter(arrayListOf()) { targetId ->
            sendFriendRequest(targetId)
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = adapter
    }

    private fun performSearch(keyword: String) {
        // Chạy Networking trên luồng IO
        CoroutineScope(Dispatchers.IO).launch {
            val results = NativeClient.searchUsers(keyword, currentUserId)

            // Cập nhật UI trên luồng chính
            withContext(Dispatchers.Main) {
                if (results?.isEmpty() == true) {
                    Toast.makeText(this@SearchUserActivity, "Không tìm thấy ai!", Toast.LENGTH_SHORT).show()
                }
                adapter.updateData(results ?: arrayListOf())
            }
        }
    }

    private fun sendFriendRequest(targetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            // NativeClient trả về > 0 là thành công (RequestID), mã lỗi nếu <= 0
            val result = NativeClient.sendFriendRequest(currentUserId, targetId)

            withContext(Dispatchers.Main) {
                when {
                    result > 0 -> Toast.makeText(applicationContext, "Đã gửi lời mời!", Toast.LENGTH_SHORT).show()
                    result == -1 -> Toast.makeText(applicationContext, "Đã là bạn bè rồi!", Toast.LENGTH_SHORT).show()
                    result == -2 -> Toast.makeText(applicationContext, "Đã gửi lời mời trước đó!", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(applicationContext, "Lỗi gửi yêu cầu!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}