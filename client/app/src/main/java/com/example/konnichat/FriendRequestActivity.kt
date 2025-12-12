package com.example.konnichat

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konnichat.adapter.PendingRequestAdapter
import com.example.konnichat.databinding.ActivityFriendRequestBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FriendRequestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFriendRequestBinding
    private lateinit var adapter: PendingRequestAdapter
    private val currentUserId = 1 // TODO: Lấy ID thật

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadPendingRequests()
    }

    private fun setupRecyclerView() {
        adapter = PendingRequestAdapter(arrayListOf()) { requestId, isAccepted ->
            respondToRequest(requestId, isAccepted)
        }
        binding.rvFriendRequests.layoutManager = LinearLayoutManager(this)
        binding.rvFriendRequests.adapter = adapter
    }

    private fun loadPendingRequests() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = NativeClient.getPendingRequests(currentUserId)
            withContext(Dispatchers.Main) {
                adapter.updateData(list?: arrayListOf())
                if (list?.isEmpty() == true) {
                    Toast.makeText(this@FriendRequestActivity, "Không có lời mời nào.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun respondToRequest(requestId: Int, isAccepted: Boolean) {
        val statusInt = if (isAccepted) 1 else 0

        CoroutineScope(Dispatchers.IO).launch {
            val success = NativeClient.respondFriendRequest(requestId, statusInt)

            withContext(Dispatchers.Main) {
                if (success == 1) {
                    val msg = if (isAccepted) "Đã đồng ý kết bạn!" else "Đã từ chối."
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    // Load lại danh sách để xóa item vừa xử lý
                    loadPendingRequests()
                } else {
                    Toast.makeText(applicationContext, "Lỗi xử lý!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}