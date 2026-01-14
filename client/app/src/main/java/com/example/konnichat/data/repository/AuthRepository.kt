package com.example.konnichat.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.konnichat.core.Constants
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.dao.UserDao
import com.example.konnichat.data.local.entity.UserEntity
import java.util.Date
import com.example.konnichat.data.remote.ConnectionState
import com.example.konnichat.data.remote.NativeEventListenerImpl
import com.example.konnichat.core.exception.NativeException
import com.example.konnichat.core.state.Resource
import com.example.konnichat.core.utils.SecurityUtils
import com.example.konnichat.data.local.prefs.SessionManager
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository (
    private val userDao: UserDao,
    private val db: AppDatabase,
    private val sessionManager: SessionManager
){

    companion object {
        private const val TAG = "[AuthRepo]"
    }

    // Kết nối Server (Socket)
    suspend fun connectToServer(): Resource<Boolean> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Bắt đầu kết nối đến ${Constants.SERVER_HOST}:${Constants.SERVER_PORT}")
        try {
            // Gọi hàm C: connect. Hàm này trả về 0 nếu thành công.
            val result = NativeClient.connect(Constants.SERVER_HOST, Constants.SERVER_PORT)
            if (result == 0) {
                Log.i(TAG, "Kết nối Socket thành công.")
                NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)
                Resource.Success(true)
            } else {
                Log.e(TAG, "Kết nối thất bại. Mã lỗi Native: $result")
                Resource.Error("Kết nối thất bại. Mã lỗi: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception khi kết nối: ${e.message}")
            Resource.Error("Lỗi kết nối: ${e.message}")
        }
    }

    private fun ensureConnection(): Boolean {
        // Nếu đang CONNECTED thì thôi, không connect lại để tránh đóng socket đang chạy
        if (NativeEventListenerImpl.connectionState.value == ConnectionState.CONNECTED) {
            return true
        }

        // Nếu DISCONNECTED hoặc CONNECTING, thử connect lại
        val res = NativeClient.connect(Constants.SERVER_HOST, Constants.SERVER_PORT)
        return if (res == 0) {
            NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)
            true
        } else {
            false
        }
    }

    // Hàm Đăng nhập
    suspend fun login(email: String, pass: String): Resource<UserDto> = withContext(Dispatchers.IO) {
        try {

            if (!ensureConnection()) {
                return@withContext Resource.Error("Không thể kết nối đến máy chủ.")
            }

            val hashedPass = SecurityUtils.hashSHA256(pass)
            val user = NativeClient.loginUser(email, hashedPass)

            if (user != null) {
                handleLoginSuccess(user, email, hashedPass)
                Resource.Success(user)
            } else {
                Resource.Error("Dữ liệu người dùng trả về bị rỗng")
            }
        } catch (e: Exception) {
            // Xử lý Exception từ C ném lên (đã định nghĩa trong NativeExceptions.kt)
            val message = e.message ?: "Lỗi không xác định"
            Resource.Error(message)
        }
    }

    suspend fun autoLogin(): Boolean = withContext(Dispatchers.IO) {
        val email = sessionManager.getSavedEmail()
        val pass = sessionManager.getSavedPass()

        Log.d(TAG, "AutoLogin: Kiểm tra credentials đã lưu...")

        if (email.isNullOrEmpty() || pass.isNullOrEmpty()) {
            Log.w(TAG, "Không tìm thấy email/pass đã lưu. Hủy AutoLogin.")
            return@withContext false
        } else {
            Log.d(TAG, "Tìm thấy credentials cho: $email. Đang gọi Native Login...")
            try {
                val user = NativeClient.loginUser(email, pass)
                if (user != null) {
                    Log.i(TAG, "AutoLogin THÀNH CÔNG cho User ID: ${user.id}")
                    NativeEventListenerImpl.isUserLoggedOut = false
                    NativeClient.startListening(NativeEventListenerImpl)
                    return@withContext true
                } else {
                    Log.e(TAG, "AutoLogin thất bại: Native trả về null (lỗi logic lạ).")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "AutoLogin Exception: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    // Hàm Đăng ký
    suspend fun register(name: String, email: String, pass: String): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!ensureConnection()) {
                return@withContext Resource.Error("Không thể kết nối đến máy chủ.")
            }
            val hashedPass = SecurityUtils.hashSHA256(pass)
            // Hàm này trả về status code (0 = Success) hoặc ném Exception
            NativeClient.registerUser(name, email, hashedPass)
            NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)
            // Nếu không ném lỗi -> Thành công
            Resource.Success(true)
        } catch (e: Exception) {
            val message = e.message ?: "Đăng ký thất bại"
            Resource.Error(message)
        }
    }

    // Hàm Logout hoàn chỉnh
    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            // 1. Chặn luồng tự động kết nối lại trước khi ngắt socket
            NativeEventListenerImpl.isUserLoggedOut = true

            // 2. Gọi Native để báo Server và đóng socket
            NativeClient.logoutUser()

            // 3. Xóa dữ liệu local
            sessionManager.clearSession()

            // 4. Xóa sạch toàn bộ bảng trong Database Local
            db.clearAllTables()

            // 5. Cập nhật trạng thái kết nối về DISCONNECTED
            NativeEventListenerImpl.connectionState.postValue(ConnectionState.DISCONNECTED)
            Log.i(TAG, "Đăng xuất hoàn tất.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đăng xuất: ${e.message}")
            e.printStackTrace()
        }
    }

    // Helper: Xử lý lưu dữ liệu khi login thành công
    private suspend fun handleLoginSuccess(user: UserDto, email: String, passHash: String) {
        NativeEventListenerImpl.isUserLoggedOut = false
        NativeClient.startListening(NativeEventListenerImpl)
        NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)

        // Lưu vào SessionManager
        sessionManager.saveLoginSession(user.id, user.name, user.email)
        sessionManager.saveCredentials(email, passHash)

        // Lưu thông tin bản thân vào User Table
        db.clearAllTables() // Xóa dữ liệu của user cũ (nếu có)
        val myUserEntity = UserEntity(
            serverId = user.id,
            email = user.email,
            name = user.name,
            age = null,
            status = "active",
            isOnline = true,
            avatarUrl = null,
            relationType = 0,
            createdAt = Date(),
            updatedAt = Date()
        )
        userDao.insertUser(myUserEntity)
    }
}