package com.example.konnichat.data.remote

import com.example.konnichat.data.remote.dto.UserDto

object NativeClient {
    init {
        System.loadLibrary("konnichat")
    }

    // Kết nối đến server socket
    external fun connect(ip: String, port: Int): Int

    // Đóng kết nối
    external fun disconnect()

    // Đăng ký
    external fun registerUser(name: String, email: String, pass: String): Int

    // Đăng nhập: Trả về UserDto nếu OK, ném Exception nếu lỗi
    @Throws(Exception::class)
    external fun loginUser(email: String, pass: String): UserDto?
}
