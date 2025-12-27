package com.example.konnichat

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tvConsole: TextView
    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private lateinit var etTargetId: EditText
    private lateinit var etMessage: EditText

    companion object {
        private const val TAG = "KonniChatTest"
        init {
            System.loadLibrary("konnichat")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Init UI
        tvConsole = findViewById(R.id.tvConsole)
        etEmail = findViewById(R.id.etEmail)
        etPass = findViewById(R.id.etPass)
        etTargetId = findViewById(R.id.etTargetId)
        etMessage = findViewById(R.id.etMessage)

        // Init JNI
        initNative()

        // --- BUTTON LISTENERS ---

        // 1. CONNECT
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            hideKeyboard();
            Thread {
                writeLog("Connecting to 10.0.2.2:8080...")
                val success = connectServer("10.0.2.2", 8080)
                if(success) writeLog("CONNECTED SUCCESS!") else writeLog("CONNECT FAILED!")
            }.start()
        }

        // 2. REGISTER
        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            hideKeyboard();
            val email = etEmail.text.toString()
            val pass = etPass.text.toString()
            val name = email.substringBefore("@") // Tự lấy tên từ email cho nhanh
            registerUser(name, email, pass)
        }

        // 3. LOGIN
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            hideKeyboard();
            val email = etEmail.text.toString()
            val pass = etPass.text.toString()
            loginUser(email, pass)
        }

        // 4. FRIEND MANAGEMENT
        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            hideKeyboard();
            val keyword = etMessage.text.toString() // Mượn ô message để nhập từ khóa
            if(keyword.isEmpty()) { toast("Nhập tên vào ô nội dung chat để tìm"); return@setOnClickListener }
            searchUser(keyword)
        }

        findViewById<Button>(R.id.btnGetFriends).setOnClickListener { hideKeyboard(); getFriendList() }

        findViewById<Button>(R.id.btnSendReq).setOnClickListener {
            hideKeyboard();
            val id = etTargetId.text.toString().toIntOrNull()
            if (id != null) {
                // Tận dụng struct FriendReqPayload chỉ cần ID
                sendMessage(id, "FRIEND_REQ_SIGNAL") // Hack logic 1 tí: dùng hàm sendReq riêng bên dưới
                // Thực tế nên gọi hàm riêng, ở đây ta gọi qua JNI
                sendFriendRequest(id)
            } else toast("Nhập Target ID")
        }

        findViewById<Button>(R.id.btnGetPending).setOnClickListener { getPendingRequests() }

        findViewById<Button>(R.id.btnAccept).setOnClickListener { respondReq(true) }
        findViewById<Button>(R.id.btnDeny).setOnClickListener { respondReq(false) }

        // 5. CHAT & OFFLINE
        findViewById<Button>(R.id.btnChat).setOnClickListener {
            hideKeyboard();
            val id = etTargetId.text.toString().toIntOrNull()
            val msg = etMessage.text.toString()
            if (id != null && msg.isNotEmpty()) {
                sendMessage(id, msg)
                writeLog("Me -> $id: $msg")
                etMessage.setText("")
            } else toast("Thiếu ID hoặc nội dung")
        }

        findViewById<Button>(R.id.btnFetchOffline).setOnClickListener {
            hideKeyboard();
            fetchOfflineMessages()
        }
    }

    fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        // Tìm view đang được focus (thường là EditText)
        var view = getCurrentFocus()
        // Nếu không có view nào focus, tạo view ảo để tránh lỗi
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0)
    }

    private fun respondReq(accept: Boolean) {
        val reqId = etTargetId.text.toString().toIntOrNull()
        if (reqId != null) {
            respondFriendRequest(reqId, accept)
        } else toast("Nhập Request ID vào ô Target ID")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // Hàm update log lên màn hình (Thread-safe)
    fun updateLog(msg: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            tvConsole.append("\n[$time] $msg")
            // Auto scroll down
            val scrollAmount = tvConsole.layout.getLineTop(tvConsole.lineCount) - tvConsole.height
            if (scrollAmount > 0) tvConsole.scrollTo(0, scrollAmount)
        }
    }

    // Helper gọi từ background thread
    private fun writeLog(msg: String) {
        updateLog(msg)
    }

    // JNI Callback
    fun onNativeMessage(msg: String) {
        updateLog(msg)
    }

    // --- NATIVE DECLARATIONS ---
    external fun initNative()
    external fun connectServer(ip: String, port: Int): Boolean
    external fun registerUser(name: String, email: String, password: String)
    external fun loginUser(email: String, password: String)
    external fun getFriendList()
    external fun searchUser(keyword: String)
    external fun sendMessage(receiverId: Int, content: String)
    external fun fetchOfflineMessages()

    // Thêm mấy hàm mới cho đủ bộ Friend
    external fun sendFriendRequest(targetId: Int)
    external fun getPendingRequests()
    external fun respondFriendRequest(requestId: Int, isAccepted: Boolean)
}