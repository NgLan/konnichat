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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FriendRequestFragment : Fragment() {

    class Factory(private val repo: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FriendRequestViewModel(repo) as T
    }

    private val viewModel: FriendRequestViewModel by viewModels {
        Factory((requireActivity().application as App).userRepository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false) // Tái sử dụng layout có RecyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ẩn thanh search đi vì ta tái sử dụng layout
        view.findViewById<View>(R.id.etSearchQuery)?.visibility = View.GONE
        view.findViewById<View>(R.id.btnSubmitSearch)?.visibility = View.GONE

        val rv = view.findViewById<RecyclerView>(R.id.rvSearchResults)
        rv.layoutManager = LinearLayoutManager(context)

        val adapter = FriendRequestAdapter(
            onAccept = { req -> viewModel.respond(req.requestId, true) },
            onDeny = { req -> viewModel.respond(req.requestId, false) }
        )
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.requests.collectLatest { adapter.submitList(it) }
        }

        viewModel.loadRequests()
    }
}