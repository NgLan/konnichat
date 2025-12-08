package com.example.konnichat

import com.example.konnichat.data.dto.NativeFriendDto
import com.example.konnichat.data.dto.NativeMessageDto
import com.example.konnichat.data.dto.NativeUserDto
import com.example.konnichat.data.dto.SocketEventDto

object NativeClient {
    init {
        System.loadLibrary("konnichat")
    }

    // --- CÁC HÀM JNI ---

    // 1. Kết nối & Auth
    external fun connectToServer(ipAddress: String, port: Int): String
    external fun loginUser(user: String, pass: String): NativeUserDto?
    external fun registerUser(user: String, pass: String): Int

    // 2. Bạn bè
    external fun getFriendList(userId: Int): ArrayList<NativeFriendDto>?

    // 3. Chat
    external fun sendMessage(senderId: Int, receiverId: Int, content: String)
    external fun receiveMessage(): NativeMessageDto?
    external fun fetchOfflineMessages(userId: Int): ArrayList<NativeMessageDto>?
    external fun getChatHistory(myId: Int, friendId: Int): ArrayList<NativeMessageDto>?

    external fun readSocketEvent(): SocketEventDto?
}
