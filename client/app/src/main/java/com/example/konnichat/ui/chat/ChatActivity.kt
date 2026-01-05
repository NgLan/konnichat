package com.example.konnichat.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konnichat.App
import com.example.konnichat.databinding.ActivityChatBinding
import com.example.konnichat.data.repository.ChatRepository
import com.example.konnichat.data.repository.UserRepository
import android.view.Menu
import android.view.MenuItem
import com.example.konnichat.R
import com.example.konnichat.ui.group.AddMemberActivity
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    private var myUserId: Int = -1
    private var targetUserId: Int = -1

    private var chatType: String = "private"

    // Biến static để NativeEventListener kiểm tra trạng thái (chặn thông báo khi đang chat)
    companion object {
        var sCurrentTargetId: Int = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Lấy thông tin User hiện tại từ SharedPreferences (Đã lưu lúc Login)
        val prefs = getSharedPreferences("konnichat_prefs", Context.MODE_PRIVATE)
        myUserId = prefs.getInt("USER_ID", -1)

        // 2. Lấy thông tin người chat cùng từ Intent
        targetUserId = intent.getIntExtra("TARGET_ID", -1)
        val targetName = intent.getStringExtra("TARGET_NAME") ?: "Người dùng"

        chatType = intent.getStringExtra("CHAT_TYPE") ?: "private"


        // Kiểm tra dữ liệu hợp lệ
        if (myUserId == -1 || targetUserId == -1) {
            Toast.makeText(this, "Lỗi xác thực người dùng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatType = intent.getStringExtra("CHAT_TYPE") ?: "private"

        // 3. Khởi tạo ViewModel (Manual DI injection)
        val app = application as App
        val factory = ChatViewModelFactory(app.chatRepository, app.userRepository)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        viewModel.currentChatType = chatType
        setupUI(targetName)
        setupObserver()
    }

    private fun setupUI(name: String) {
        // Gán tên người chat
        binding.tvChatTitle.text = name

        // Setup RecyclerView
        adapter = ChatAdapter(myUserId)
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Tin nhắn mới nhất nằm dưới cùng
            // reverseLayout = false // Để false cho dễ hình dung, stackFromEnd lo việc cuộn xuống
        }
        binding.rvChat.layoutManager = layoutManager
        binding.rvChat.adapter = adapter

        // Pagination: Lắng nghe sự kiện cuộn để load thêm tin cũ
        binding.rvChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                // Nếu cuộn lên đỉnh (vị trí 0) -> Load thêm lịch sử
                if (dy < 0 && !rv.canScrollVertically(-1) && adapter.itemCount > 0) {
                    val currentCount = adapter.itemCount
                    // Log để kiểm tra (Optional)
                    // Log.d("ChatActivity", "Scrolled to top, loading more from offset $currentCount")
                    viewModel.loadMoreHistory(targetUserId, currentCount)
                }
            }
        })

        binding.btnMute.setOnClickListener {
            viewModel.toggleMute(targetUserId)
        }

        // Xử lý nút Gửi
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                viewModel.sendMessage(myUserId, targetUserId, content)
                binding.etMessage.text.clear()
            }
        }

        // Xử lý nút Kết Bạn (Dành cho người lạ)
        binding.btnAddFriend.setOnClickListener {
            viewModel.sendFriendRequest(targetUserId)
            binding.btnAddFriend.isEnabled = false
            binding.btnAddFriend.text = "Đang gửi..."
        }



        // Nút Back
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupObserver() {
        // Quan sát danh sách tin nhắn từ DB (Reactive)
        viewModel.getMessages(myUserId, targetUserId, chatType).observe(this) { msgs ->
            adapter.submitList(msgs) {
                // Khi list update xong, cuộn xuống dưới cùng nếu đang ở gần đáy
                // (Logic đơn giản: cứ có tin mới là cuộn xuống)
                if (msgs.isNotEmpty()) {
                    binding.rvChat.scrollToPosition(msgs.size - 1)
                }
            }
        }

        // Quan sát trạng thái bạn bè để ẩn/hiện khung chat
        viewModel.isFriend.observe(this) { isFriend ->
            if (isFriend) {
                // Là bạn bè: Hiện khung chat, ẩn khung block
                binding.layoutInput.visibility = View.VISIBLE
                binding.layoutBlock.visibility = View.GONE
            } else {
                // Người lạ: Ẩn khung chat, hiện khung block/kết bạn
                binding.layoutInput.visibility = View.GONE
                binding.layoutBlock.visibility = View.VISIBLE

                // Reset lại trạng thái nút kết bạn nếu cần thiết
                binding.tvBlockMessage.text = "Hai bạn chưa phải bạn bè. Kết bạn để nhắn tin."
            }
        }

        // Quan sát kết quả gửi lời mời kết bạn (Optional)
        viewModel.friendReqStatus.observe(this) { success ->
            if (success) {
                binding.btnAddFriend.text = "Đã gửi lời mời"
                Toast.makeText(this, "Đã gửi lời mời kết bạn", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnAddFriend.isEnabled = true
                binding.btnAddFriend.text = "Kết bạn"
                Toast.makeText(this, "Gửi thất bại", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isMuted.observe(this) { isMuted ->
            if (isMuted) {
                // Đã tắt thông báo -> Icon im lặng
                binding.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
                // Optional: Toast thông báo
                // Toast.makeText(this, "Đã tắt thông báo cuộc trò chuyện này", Toast.LENGTH_SHORT).show()
            } else {
                // Đang bật -> Icon thông báo
                binding.btnMute.setImageResource(android.R.drawable.stat_notify_chat)
                // Toast.makeText(this, "Đã bật thông báo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Đánh dấu đang chat với người này để NativeEventListener chặn thông báo
        sCurrentTargetId = targetUserId
    }

    override fun onPause() {
        super.onPause()
        // Rời mà         n hình chat -> Reset ID để cho phép hiện thông báo lại
        sCurrentTargetId = -1
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Chỉ hiện menu thêm người nếu là Group
        if (chatType == "group") {
            menuInflater.inflate( R.menu.chat_group_menu, menu)
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_member -> {
                // Mở màn hình thêm thành viên, truyền GroupID (targetUserId chính là GroupId trong chat nhóm)
                val intent = Intent(this, AddMemberActivity::class.java)
                intent.putExtra("GROUP_ID", targetUserId)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// Factory để khởi tạo ViewModel có tham số
class ChatViewModelFactory(
    private val chatRepo: ChatRepository,
    private val userRepo: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatRepo, userRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}