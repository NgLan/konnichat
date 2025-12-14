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
import com.example.konnichat.adapter.PendingRequestAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RequestsFragment : Fragment() {
    private var currentUserId: Int = -1
    private lateinit var rvRequests: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: PendingRequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_requests, container, false)
        currentUserId = arguments?.getInt("USER_ID") ?: -1

        rvRequests = view.findViewById(R.id.rvRequests)
        tvEmpty = view.findViewById(R.id.tvEmptyRequests)

        setupRecyclerView()

        // Gọi load lần đầu khi tạo view
        loadRequests()

        return view
    }

    // --- GIỮ LẠI onResume ĐỂ TỰ UPDATE KHI CHUYỂN TAB ---
    override fun onResume() {
        super.onResume()
        // Không đăng ký lắng nghe ở đây nữa (HomeActivity lo rồi)
        // Nhưng vẫn gọi loadRequests để đảm bảo dữ liệu luôn mới nhất khi user bấm vào tab này
        loadRequests()
    }

    // --- ĐÃ XÓA onPause VÌ KHÔNG CẦN THIẾT NỮA ---

    private fun setupRecyclerView() {
        rvRequests.layoutManager = LinearLayoutManager(context)
        adapter = PendingRequestAdapter(arrayListOf()) { requestId, isAccepted ->
            respondToRequest(requestId, isAccepted)
        }
        rvRequests.adapter = adapter
    }

    // Đã thêm fix lỗi Crash "UninitializedPropertyAccessException"
    fun loadRequests() {
        if (currentUserId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val list = NativeClient.getPendingRequests(currentUserId)

            withContext(Dispatchers.Main) {
                // Kiểm tra xem adapter đã được khởi tạo chưa trước khi dùng
                if (::adapter.isInitialized) {
                    if (list != null && list.isNotEmpty()) {
                        tvEmpty.visibility = View.GONE
                        rvRequests.visibility = View.VISIBLE
                        adapter.updateData(list)
                    } else {
                        tvEmpty.visibility = View.VISIBLE
                        rvRequests.visibility = View.GONE
                        tvEmpty.text = "Không có lời mời nào."
                    }
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
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    loadRequests()
                } else {
                    Toast.makeText(context, "Lỗi xử lý!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}