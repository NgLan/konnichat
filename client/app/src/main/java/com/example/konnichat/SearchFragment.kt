package com.example.konnichat

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {
    private var currentUserId: Int = -1
    private lateinit var etKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var rvResults: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        currentUserId = arguments?.getInt("USER_ID") ?: -1

        etKeyword = view.findViewById(R.id.etSearchKeyword)
        btnSearch = view.findViewById(R.id.btnSearch)
        rvResults = view.findViewById(R.id.rvSearchResults)
        rvResults.layoutManager = LinearLayoutManager(context)

        btnSearch.setOnClickListener {
            val keyword = etKeyword.text.toString()
            if (keyword.isNotEmpty()) searchUsers(keyword)
        }
        return view
    }

    private fun searchUsers(keyword: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val results = NativeClient.searchUsers(keyword, currentUserId)
            withContext(Dispatchers.Main) {
                if (results != null && results.isNotEmpty()) {
                    // Hiển thị kết quả tìm kiếm
                    // Lưu ý: Cần tạo SearchAdapter. Tạm thời hiển thị Toast test
                    Toast.makeText(context, "Tìm thấy ${results.size} người", Toast.LENGTH_SHORT).show()

                    // Logic gửi kết bạn nhanh (Test): Gửi luôn cho người đầu tiên tìm thấy
                    showConfirmAddFriend(results[0])
                } else {
                    Toast.makeText(context, "Không tìm thấy ai!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showConfirmAddFriend(user: UserSearchInfo) {
        AlertDialog.Builder(context)
            .setTitle("Kết bạn")
            .setMessage("Gửi lời mời tới ${user.name}?")
            .setPositiveButton("Gửi") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val res = NativeClient.sendFriendRequest(currentUserId, user.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đã gửi yêu cầu (Mã: $res)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}