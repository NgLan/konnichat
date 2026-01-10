package com.example.konnichat.ui.group

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konnichat.App
import com.example.konnichat.databinding.ActivityCreateGroupBinding
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private lateinit var viewModel: CreateGroupViewModel
    private lateinit var adapter: SelectFriendAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
        observeData()
    }

    private fun setupViewModel() {
        // Khởi tạo ViewModel Factory thủ công
        val app = application as App
        val factory = CreateGroupViewModelFactory(app.chatRepository, app.userRepository)
        viewModel = ViewModelProvider(this, factory)[CreateGroupViewModel::class.java]
    }

    private fun setupUI() {
        // Setup RecyclerView với Adapter chọn bạn
        adapter = SelectFriendAdapter { count ->
            // Callback khi số lượng chọn thay đổi
            updateCreateButton(count)
        }
        binding.rvFriends.layoutManager = LinearLayoutManager(this)
        binding.rvFriends.adapter = adapter

        binding.btnBack.setOnClickListener {
            finish()
        }
        // Xử lý nút Tạo
        binding.btnCreateGroup.setOnClickListener {
            val groupName = binding.etGroupName.text.toString().trim()
            val selectedIds = adapter.getSelectedUserIds()

            if (groupName.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên nhóm", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedIds.size < 2) {
                Toast.makeText(this, "Chọn ít nhất 2 thành viên", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Gọi ViewModel tạo nhóm
            viewModel.createGroup(groupName, selectedIds)

            Toast.makeText(this, "Đã gửi yêu cầu tạo nhóm...", Toast.LENGTH_SHORT).show()
            finish() // Đóng màn hình, quay về Home
        }

    }

    private fun observeData() {
        viewModel.friends.observe(this) { list ->
            adapter.submitList(list)
        }
    }

    private fun updateCreateButton(count: Int) {
        binding.btnCreateGroup.text = "Tạo nhóm ($count)"
        // Chỉ enable nút khi chọn ít nhất 2 người
        binding.btnCreateGroup.isEnabled = count >= 2
    }
}

// Factory cho ViewModel
class CreateGroupViewModelFactory(
    private val chatRepo: ChatRepository,
    private val userRepo: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateGroupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateGroupViewModel(chatRepo, userRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}