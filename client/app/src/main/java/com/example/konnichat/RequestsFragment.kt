package com.example.konnichat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RequestsFragment : Fragment() {
    private var currentUserId: Int = -1
    private lateinit var rvRequests: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_requests, container, false)
        currentUserId = arguments?.getInt("USER_ID") ?: -1

        rvRequests = view.findViewById(R.id.rvRequests)
        tvEmpty = view.findViewById(R.id.tvEmptyRequests)
        rvRequests.layoutManager = LinearLayoutManager(context)

        loadRequests()
        return view
    }

    private fun loadRequests() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = NativeClient.getPendingRequests(currentUserId)
            withContext(Dispatchers.Main) {
                if (list != null && list.isNotEmpty()) {
                    tvEmpty.visibility = View.GONE
                    rvRequests.visibility = View.VISIBLE
                    // Cần tạo RequestAdapter. Tạm thời chưa gán adapter
                } else {
                    tvEmpty.visibility = View.VISIBLE
                    rvRequests.visibility = View.GONE
                }
            }
        }
    }
}