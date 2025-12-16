package com.example.konnichat

object NativeClient {
    init {
        System.loadLibrary("konnichat")
    }

    external fun connect(ip: String, port: Int): Boolean
    external fun login(email: String, pass: String)
    external fun sendMessage(receiverId: Int, content: String)
    external fun readPacket(): String // Hàm này sẽ chặn (block) thread
    external fun disconnect()

    external fun waitPacket(): ServerResponse
}
