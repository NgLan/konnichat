package com.example.konnichat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

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
    }
}