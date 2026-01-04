package com.example.konnichat.ui.chat

import android.content.Context
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

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    private var myUserId: Int = -1
    private var targetUserId: Int = -1

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

        // Kiểm tra dữ liệu hợp lệ
        if (myUserId == -1 || targetUserId == -1) {
            Toast.makeText(this, "Lỗi xác thực người dùng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 3. Khởi tạo ViewModel (Manual DI injection)
        val app = application as App
        val factory = ChatViewModelFactory(app.chatRepository, app.userRepository)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

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
                if (!rv.canScrollVertically(-1)) {
                    viewModel.loadMoreHistory(targetUserId, adapter.itemCount)
                }
            }
        })

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
        viewModel.getMessages(myUserId, targetUserId).observe(this) { msgs ->
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
    }

    override fun onResume() {
        super.onResume()
        // Đánh dấu đang chat với người này để NativeEventListener chặn thông báo
        sCurrentTargetId = targetUserId
    }

    override fun onPause() {
        super.onPause()
        // Rời màn hình chat -> Reset ID để cho phép hiện thông báo lại
        sCurrentTargetId = -1
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