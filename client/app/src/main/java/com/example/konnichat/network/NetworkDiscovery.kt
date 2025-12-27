package com.example.konnichat.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NetworkDiscovery {
    private const val UDP_PORT = 8888
    private const val BROADCAST_MSG = "TIM_SERVER_KONNICHAT" // Phải khớp với Server C
    private const val EXPECTED_RESPONSE = "SERVER_DAY_NE"    // Phải khớp với Server C

    suspend fun findServerIp(): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            if (Build.PRODUCT.contains("sdk") || Build.MODEL.contains("Emulator")) {
                return@withContext "10.0.2.2" // Trả về luôn IP ảo nếu đang chạy máy ảo
            }

            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 2000 // Chờ tối đa 2 giây, không tìm thấy thì thôi

            // 1. Gửi tin nhắn hét lên toàn mạng
            val sendData = BROADCAST_MSG.toByteArray()
            val broadcastIp = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastIp, UDP_PORT)
            socket.send(sendPacket)

            // 2. Chờ Server trả lời
            val recvBuf = ByteArray(1024)
            val receivePacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(receivePacket) // Code sẽ dừng ở đây chờ tin nhắn

            // 3. Kiểm tra tin nhắn phản hồi
            val msg = String(receivePacket.data, 0, receivePacket.length)
            if (msg == EXPECTED_RESPONSE) {
                // Lấy IP của người vừa trả lời (Chính là Server)
                return@withContext receivePacket.address.hostAddress
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
        }
        return@withContext null
    }
}