package com.example.konnichat.ui.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.konnichat.databinding.FragmentSearchBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FriendRequestFragment : Fragment() {

    class Factory(private val repo: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FriendRequestViewModel(repo) as T
    }

    private val viewModel: FriendRequestViewModel by viewModels {
        Factory((requireActivity().application as App).userRepository)
    }

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ẩn thanh tìm kiếm vì đây là màn hình thông báo
        binding.etSearchQuery.visibility = View.GONE
        binding.btnSubmitSearch.visibility = View.GONE

        binding.rvSearchResults.layoutManager = LinearLayoutManager(context)

        val adapter = FriendRequestAdapter(
            onAccept = { req ->
                viewModel.respond(
                    req.requestId,
                    req.senderId,
                    req.senderName,
                    true
                )
            },

            onDeny = { req ->
                viewModel.respond(
                    req.requestId,
                    req.senderId,
                    req.senderName,
                    false
                )
            }
        )
        binding.rvSearchResults.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.requests.collectLatest { adapter.submitList(it) }
        }

        viewModel.loadRequests()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}