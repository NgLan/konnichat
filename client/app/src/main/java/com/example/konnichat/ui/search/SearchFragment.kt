package com.example.konnichat.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.data.repository.UserRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    // Factory để inject Repo cho SearchViewModel
    class SearchViewModelFactory(private val repo: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(repo) as T
        }
    }

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as App).userRepository)
    }

    private lateinit var adapter: SearchUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch = view.findViewById<EditText>(R.id.etSearchQuery)
        val btnSearch = view.findViewById<ImageButton>(R.id.btnSubmitSearch)
        val rvResults = view.findViewById<RecyclerView>(R.id.rvSearchResults)

        adapter = SearchUserAdapter(
            onAddFriendClick = { userId ->
                viewModel.sendFriendRequest(userId)
                Toast.makeText(context, "Đã gửi lời mời kết bạn!", Toast.LENGTH_SHORT).show()
            },
            onChatClick = { userId ->
                Toast.makeText(context, "Chức năng Chat đang phát triển", Toast.LENGTH_SHORT).show()
            }
        )

        rvResults.layoutManager = LinearLayoutManager(context)
        rvResults.adapter = adapter

        // Bắt sự kiện bấm nút tìm kiếm
        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                viewModel.search(query)
            }
        }

        // Lắng nghe kết quả trả về
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collectLatest { results ->
                adapter.submitList(results)
                if (results.isEmpty()) {
                    Toast.makeText(context, "Không tìm thấy kết quả nào", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}