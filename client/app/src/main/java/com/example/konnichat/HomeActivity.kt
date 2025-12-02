package com.example.konnichat

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    // --- KHAI BÁO NATIVE ---
    // Hàm này phải khớp với bên native-lib.cpp
    external fun getFriendList(userId: Int): ArrayList<Friend>?

    companion object {
        init {
            System.loadLibrary("konnichat")
        }
    }

    // --- BIẾN ---
    private var currentUserId: Int = -1
    private lateinit var rvFriends: RecyclerView
    private lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1. Lấy UserID được truyền từ LoginActivity
        currentUserId = intent.getIntExtra("USER_ID", -1)

        if (currentUserId == -1) {
            Toast.makeText(this, "Lỗi: Không tìm thấy User ID", Toast.LENGTH_SHORT).show()
            finish() // Đóng app nếu lỗi
            return
        }

        // 2. Ánh xạ View
        rvFriends = findViewById(R.id.rvFriends)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        // 3. Cấu hình RecyclerView
        rvFriends.layoutManager = LinearLayoutManager(this)

        // 4. Load dữ liệu từ Server
        loadFriendsData()
    }

    private fun loadFriendsData() {
        // Chạy trên luồng IO để không treo giao diện khi đợi mạng
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Gọi hàm C Native
                val friendList = getFriendList(currentUserId)

                // Cập nhật giao diện trên luồng Main
                withContext(Dispatchers.Main) {
                    if (friendList != null && friendList.isNotEmpty()) {
                        // Có dữ liệu -> Hiển thị list
                        tvEmptyState.visibility = View.GONE
                        rvFriends.visibility = View.VISIBLE

                        val adapter = FriendAdapter(friendList)
                        rvFriends.adapter = adapter
                    } else {
                        // Không có dữ liệu hoặc lỗi -> Hiển thị thông báo rỗng
                        rvFriends.visibility = View.GONE
                        tvEmptyState.visibility = View.VISIBLE
                        tvEmptyState.text = "Bạn chưa có bạn bè nào.\nHãy kết bạn thêm nhé!"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Lỗi kết nối Server!", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }
}
