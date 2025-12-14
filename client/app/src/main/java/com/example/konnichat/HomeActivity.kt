package com.example.konnichat

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity(), NativeClient.FriendRequestCallback {

    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home) // Đảm bảo layout này có FragmentContainer

        // 1. Nhận UserID
        currentUserId = intent.getIntExtra("USER_ID", -1)

        // 2. Setup Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null

            when (item.itemId) {
                R.id.nav_friends -> selectedFragment = FriendListFragment()
                R.id.nav_search -> selectedFragment = SearchFragment()
                R.id.nav_requests -> selectedFragment = RequestsFragment()
            }

            if (selectedFragment != null) {
                // Truyền UserID sang Fragment
                val bundle = Bundle()
                bundle.putInt("USER_ID", currentUserId)
                selectedFragment.arguments = bundle


                // Thay thế Fragment
                val tag = "FRAGMENT_${item.itemId}"
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
            }
            true
        }

        // 3. Mặc định load tab đầu tiên nếu chưa có trạng thái lưu
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_friends
        }

        NativeClient.startListening()
    }

    // --- ĐĂNG KÝ LẮNG NGHE TẠI ĐÂY (LUÔN SỐNG) ---
    override fun onResume() {
        super.onResume()
        NativeClient.setFriendRequestCallback(this)
    }

    override fun onPause() {
        super.onPause()
        NativeClient.setFriendRequestCallback(null)
    }

    override fun onNewRequestReceived(senderId: Int, senderName: String) {
        runOnUiThread {
            // 1. Luôn hiện thông báo dù đang ở đâu
            Toast.makeText(this, "$senderName vừa gửi lời mời kết bạn!", Toast.LENGTH_LONG).show()

            // 2. Kiểm tra xem đang ở màn hình nào để cập nhật "nóng"
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

            if (currentFragment is RequestsFragment) {
                // Nếu đang coi danh sách lời mời -> Load lại ngay cho nóng
                currentFragment.loadRequests()
            }
            else if (currentFragment is SearchFragment) {
                // Nếu đang tìm kiếm -> Có thể báo fragment cập nhật lại nút bấm (nếu cần)
                // (Tùy chọn, hiện tại SearchFragment tự xử lý khi bấm nút tìm kiếm lại)
            }

            // TODO: Sau này làm Chat, bạn sẽ thêm hàm onMessageReceived tương tự ở đây
        }
    }
}