package com.example.konnichat.data.repository

import com.example.konnichat.core.Constants
import com.example.konnichat.core.exception.NativeException
import com.example.konnichat.core.state.Resource
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    // Hàm kết nối đến Server (Dùng cho Splash Screen)
    // Chạy trên luồng IO để không chặn UI
    suspend fun connectToServer(): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Gọi hàm C: connect. Hàm này trả về 0 nếu thành công.
            val result = NativeClient.connect(Constants.SERVER_IP, Constants.SERVER_PORT)
            if (result == 0) {
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

    // Hàm Đăng ký
    suspend fun register(name: String, email: String, pass: String): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Hàm này trả về status code (0 = Success) hoặc ném Exception
            NativeClient.registerUser(name, email, pass)
            // Nếu không ném lỗi -> Thành công
            Resource.Success(true)
        } catch (e: Exception) {
            val message = e.message ?: "Đăng ký thất bại"
            Resource.Error(message)
        }
    }
}