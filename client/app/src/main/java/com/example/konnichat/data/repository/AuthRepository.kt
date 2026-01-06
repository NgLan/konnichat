package com.example.konnichat.data.repository

import android.content.SharedPreferences
import com.example.konnichat.core.Constants
import com.example.konnichat.data.local.AppDatabase
import com.example.konnichat.data.local.dao.UserDao
import com.example.konnichat.data.local.entity.UserEntity
import java.util.Date
import com.example.konnichat.data.remote.ConnectionState
import com.example.konnichat.data.remote.NativeEventListenerImpl
import com.example.konnichat.core.exception.NativeException
import com.example.konnichat.core.state.Resource
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository (
    private val userDao: UserDao,
    private val db: AppDatabase,
    private val prefs: SharedPreferences
){

    // Hàm kết nối đến Server (Dùng cho Splash Screen)
    // Chạy trên luồng IO để không chặn UI
    suspend fun connectToServer(): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Gọi hàm C: connect. Hàm này trả về 0 nếu thành công.
            val result = NativeClient.connect(Constants.SERVER_HOST, Constants.SERVER_PORT)
            if (result == 0) {
                NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)

                Resource.Success(true)
            } else {
                Resource.Error("Kết nối thất bại. Mã lỗi: $result")
            }
        } catch (e: Exception) {
            Resource.Error("Lỗi kết nối: ${e.message}")
        }
    }

    // Hàm Đăng nhập
    suspend fun login(email: String, pass: String): Resource<UserDto> = withContext(Dispatchers.IO) {
        try {
            // NativeClient.loginUser là hàm blocking, nó sẽ chờ server trả lời hoặc ném Exception
            val user = NativeClient.loginUser(email, pass)
            if (user != null) {
                NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)
                NativeEventListenerImpl.isUserLoggedOut = false // Reset cờ logout
                prefs.edit()
                    .putString("SAVED_EMAIL", email)
                    .putString("SAVED_PASS", pass)
                    .putInt("USER_ID", user.id) // Lưu luôn ID để tiện dùng
                    .apply()

                db.clearAllTables()
                val myUserEntity = UserEntity(
                    serverId = user.id,
                    email = user.email,
                    name = user.name,
                    age = null,
                    status = "active",
                    isOnline = true, // Vừa login xong chắc chắn online
                    avatarUrl = null,
                    relationType = 0,
                    createdAt = Date(),
                    updatedAt = Date()
                )
                userDao.insertUser(myUserEntity)
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
        val email = prefs.getString("SAVED_EMAIL", null)
        val pass = prefs.getString("SAVED_PASS", null)

        if (email != null && pass != null) {
            try {
                // Gọi login native nhưng không cần xóa DB hay insert lại User (vì đã có rồi)
                val user = NativeClient.loginUser(email, pass)
                return@withContext user != null
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
        return@withContext false
    }

    // Hàm Đăng ký
    suspend fun register(name: String, email: String, pass: String): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Hàm này trả về status code (0 = Success) hoặc ném Exception
            NativeClient.registerUser(name, email, pass)
            NativeEventListenerImpl.connectionState.postValue(ConnectionState.CONNECTED)
            // Nếu không ném lỗi -> Thành công
            Resource.Success(true)
        } catch (e: Exception) {
            val message = e.message ?: "Đăng ký thất bại"
            Resource.Error(message)
        }
    }
}