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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.databinding.FragmentSearchBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    class SearchViewModelFactory(private val repo: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(repo) as T
        }
    }

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as App).userRepository)
    }

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        // 3. Setup Listener (Sự kiện bấm nút)
        binding.btnSubmitSearch.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                viewModel.search(query)
            } else {
                Toast.makeText(context, getString(R.string.msg_search_empty), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        observeData()
    }

    private fun setupRecyclerView() {
        adapter = SearchUserAdapter(
            onAddFriendClick = { userId ->
                viewModel.sendFriendRequest(userId)
            },
            onChatClick = {
                Toast.makeText(context, getString(R.string.msg_chat_developing), Toast.LENGTH_SHORT)
                    .show()
            }
        )
        binding.rvSearchResults.layoutManager = LinearLayoutManager(context)
        binding.rvSearchResults.adapter = adapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            //viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.searchResults.collectLatest { results ->
                adapter.submitList(results)

                val currentQuery = binding.etSearchQuery.text.toString().trim()
                if (results.isEmpty() && currentQuery.isNotEmpty()) {
                    Toast.makeText(context, "Không tìm thấy kết quả nào", Toast.LENGTH_SHORT).show()
                }
            }
            //}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}