package com.example.konnichat.core

class Constants {
    companion object {
        // IP 10.0.2.2 là localhost của máy tính khi chạy trên Emulator Android
        // Nếu chạy trên điện thoại thật, hãy đổi thành IP LAN của máy tính (VD: 192.168.1.5)
         const val SERVER_HOST = "10.0.2.2"
         const val SERVER_PORT = 8080

//        const val SERVER_HOST = "0.tcp.ap.ngrok.io"
//        const val SERVER_PORT = 16934
    }
}