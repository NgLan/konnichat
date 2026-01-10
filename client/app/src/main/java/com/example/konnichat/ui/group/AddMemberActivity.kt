package com.example.konnichat.ui.group

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konnichat.App
import com.example.konnichat.databinding.ActivityCreateGroupBinding // Tái sử dụng layout
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import com.example.konnichat.ui.base.BaseActivity

class AddMemberActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private lateinit var viewModel: AddMemberViewModel
    private lateinit var adapter: SelectFriendAdapter
    private var groupId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy GroupID từ Intent truyền sang
        groupId = intent.getIntExtra("GROUP_ID", -1)
        if (groupId == -1) {
            finish()
            return
        }

        setupViewModel()

        viewModel.loadData(groupId)

        setupUI()
    }

    private fun setupViewModel() {
        val app = application as App
        val factory = AddMemberViewModelFactory(app.chatRepository, app.userRepository)
        viewModel = ViewModelProvider(this, factory)[AddMemberViewModel::class.java]

        // Observe dữ liệu
        viewModel.friends.observe(this) { list ->
            adapter.submitList(list)
        }
    }

    private fun setupUI() {
        // Tùy biến lại giao diện cho việc "Thêm thành viên"
        binding.tvTitle.text = "Thêm thành viên mới"
        binding.etGroupName.visibility = View.GONE // Ẩn ô nhập tên nhóm
        binding.btnCreateGroup.text = "Thêm (0)"   // Đổi tên nút

        adapter = SelectFriendAdapter { count ->
            binding.btnCreateGroup.text = "Thêm ($count)"
            binding.btnCreateGroup.isEnabled = count > 0
        }
        binding.rvFriends.layoutManager = LinearLayoutManager(this)
        binding.rvFriends.adapter = adapter

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCreateGroup.setOnClickListener {
            val selectedIds = adapter.getSelectedUserIds()
            if (selectedIds.isNotEmpty()) {
                viewModel.addMembers(groupId, selectedIds)
                Toast.makeText(this, "Đã gửi yêu cầu thêm thành viên", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

// Factory
class AddMemberViewModelFactory(
    private val chatRepo: ChatRepository,
    private val userRepo: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddMemberViewModel(chatRepo, userRepo) as T
    }
}