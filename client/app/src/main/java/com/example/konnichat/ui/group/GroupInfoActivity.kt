package com.example.konnichat.ui.group

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konnichat.App
import com.example.konnichat.databinding.ActivityGroupInfoBinding
import com.example.konnichat.ui.base.BaseActivity
import android.view.Menu
import android.view.MenuItem
import com.example.konnichat.R

class GroupInfoActivity : BaseActivity() {

    private lateinit var binding: ActivityGroupInfoBinding
    private lateinit var viewModel: GroupInfoViewModel
    private lateinit var adapter: GroupMemberAdapter
    private var groupId: Int = -1
    private var myUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("konnichat_prefs", Context.MODE_PRIVATE)
        myUserId = prefs.getInt("USER_ID", -1)

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
        val factory = GroupInfoViewModelFactory(app.chatRepository, groupId, myUserId)
        viewModel = ViewModelProvider(this, factory)[GroupInfoViewModel::class.java]

        viewModel.members.observe(this) { list ->
            adapter.submitList(list)
        }

        viewModel.isAdmin.observe(this) { isAdmin ->
            adapter.setAdminStatus(isAdmin)
            invalidateOptionsMenu()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        adapter = GroupMemberAdapter(myUserId) { targetId ->
            // Khi bấm nút Kick -> Hiện Dialog xác nhận
            showKickConfirmation(targetId)
        }

        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter
    }

    private fun showKickConfirmation(targetId: Int) {
        AlertDialog.Builder(this)
            .setTitle("Mời ra khỏi nhóm")
            .setMessage("Bạn có chắc chắn muốn mời thành viên này ra khỏi nhóm không?")
            .setPositiveButton("Đồng ý") { _, _ ->
                viewModel.kickMember(targetId)
                Toast.makeText(this, "Đã gửi lệnh mời ra khỏi nhóm", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // --- [THÊM MỚI] 1. Khởi tạo Menu ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_group_menu, menu)
        return true
    }

    // --- [THÊM MỚI] 2. Điều chỉnh menu dựa trên quyền Admin ---
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // Chỉ hiện nút Giải tán nếu là Admin
        menu?.findItem(R.id.action_dissolve_group)?.isVisible = viewModel.isAdmin.value == true

        // Ẩn nút "Thêm thành viên" và "Xem thành viên" vì đang ở trong màn hình Info rồi
        menu?.findItem(R.id.action_add_member)?.isVisible = false
        menu?.findItem(R.id.action_view_members)?.isVisible = false

        return super.onPrepareOptionsMenu(menu)
    }

    // --- [THÊM MỚI] 3. Xử lý click ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_dissolve_group -> {
                showDissolveConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDissolveConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Giải tán nhóm")
            .setMessage("Bạn có chắc chắn muốn giải tán nhóm này không? Mọi tin nhắn và thành viên sẽ bị xóa.")
            .setPositiveButton("Giải tán") { _, _ ->
                viewModel.dissolveGroup()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}

