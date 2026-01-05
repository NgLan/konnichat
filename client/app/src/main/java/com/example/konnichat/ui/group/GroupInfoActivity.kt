package com.example.konnichat.ui.group

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konnichat.App
import com.example.konnichat.databinding.ActivityGroupInfoBinding

class GroupInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupInfoBinding
    private lateinit var viewModel: GroupInfoViewModel
    private lateinit var adapter: GroupMemberAdapter
    private var groupId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getIntExtra("GROUP_ID", -1)
        val groupName = intent.getStringExtra("GROUP_NAME") ?: "Thông tin nhóm"
        binding.tvTitle.text = groupName

        if (groupId == -1) {
            finish()
            return
        }

        setupViewModel()
        setupUI()
    }

    private fun setupViewModel() {
        val app = application as App
        val factory = GroupInfoViewModelFactory(app.chatRepository, groupId)
        viewModel = ViewModelProvider(this, factory)[GroupInfoViewModel::class.java]

        viewModel.members.observe(this) { list ->
            adapter.submitList(list)
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        adapter = GroupMemberAdapter()
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter
    }
}