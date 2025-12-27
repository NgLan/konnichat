package com.example.konnichat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.konnichat.core.exception.AuthenticationException
import com.example.konnichat.core.exception.UserAlreadyExistsException
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.NativeEventListener
import com.example.konnichat.data.remote.dto.UserDto
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * NativeClientTest
 * Kiểm thử tích hợp (Integration Test) với Server C thực tế.
 * Server IP: 10.0.2.2 (Localhost nhìn từ Android Emulator)
 * Protocol: protocol.h version 1
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NativeClientTest {

    companion object {
        const val SERVER_IP = "10.0.2.2"
        const val SERVER_PORT = 8080

        // --- Cập nhật Command ID theo protocol.h ---
        const val CMD_REGISTER = 10
        const val CMD_LOGIN = 12
        const val CMD_SEND_FRIEND_REQ = 42
        const val CMD_SEND_FRIEND_REQ_RESP = 43
        const val CMD_NOTIFY_FRIEND_REQ = 80

        // --- Status Codes ---
        const val STATUS_SUCCESS = 0
        const val STATUS_ERR_AUTH = 2
        const val STATUS_ERR_USER_NOT_FOUND = 3
        const val STATUS_ERR_INVALID_PARAM = 5
        const val STATUS_ERR_ALREADY_EXIST = 6
        const val STATUS_ERR_ALREADY_FRIEND = 7
        const val STATUS_ERR_REQ_PENDING = 8
    }

    @Before
    fun setup() {
        // Luôn đảm bảo kết nối mới
        val result = NativeClient.connect(SERVER_IP, SERVER_PORT)
        Assert.assertEquals("Setup: Kết nối Server thất bại", 0, result)
    }

    @After
    fun tearDown() {
        NativeClient.disconnect()
    }

    // ==========================================
    // MODULE 1: AUTHENTICATION
    // ==========================================

    @Test
    fun test01_Registration_FullFlow() {
        val uniqueId = System.currentTimeMillis()
        val validEmail = "reg_$uniqueId@konni.com"
        val validPass = "Pass123"

        // CASE 1: Đăng ký thành công
        val res = NativeClient.registerUser("TestUser", validEmail, validPass)
        Assert.assertEquals("Đăng ký mới phải trả về 0 (SUCCESS)", 0, res)

        // CASE 2: Đăng ký trùng (Test Server phản hồi lỗi ALREADY_EXIST)
        try {
            NativeClient.registerUser("TestDuplicate", validEmail, validPass)
            Assert.fail("Phải ném UserAlreadyExistsException")
        } catch (e: Exception) {
            Assert.assertTrue("Lỗi phải là UserAlreadyExistsException", e is UserAlreadyExistsException)
        }
    }

    @Test
    fun test02_Login_FullFlow() {
        val email = "login_${System.currentTimeMillis()}@konni.com"
        val pass = "Pass123"
        NativeClient.registerUser("LoginUser", email, pass)

        // CASE 1: Login thành công
        val user = NativeClient.loginUser(email, pass)
        Assert.assertNotNull("UserDto không được null", user)
        Assert.assertEquals(email, user?.email)

        // CASE 2: Sai Password (STATUS_ERROR_AUTH = 2)
        try {
            NativeClient.loginUser(email, "WrongPass")
            Assert.fail("Phải ném AuthenticationException")
        } catch (e: Exception) {
            Assert.assertTrue(e is AuthenticationException)
        }

        // CASE 3: Email không tồn tại (STATUS_ERROR_USER_NOT_FOUND = 3)
        try {
            NativeClient.loginUser("ghost@konni.com", "any")
            Assert.fail("Phải ném Exception khi user không tồn tại")
        } catch (e: Exception) {
            Assert.assertNotNull(e.message)
        }
    }

    // ==========================================
    // MODULE 2: FRIEND REQUEST
    // ==========================================

    @Test
    fun test03_SendFriendRequest_Logic() {
        val time = System.nanoTime()
        val emailA = "A_$time@konni.com"
        val emailB = "B_$time@konni.com"

        // Đăng ký 2 user
        NativeClient.registerUser("UserA", emailA, "123")
        NativeClient.registerUser("UserB", emailB, "123")

        // Mẹo: Login B để lấy ID, sau đó disconnect
        val userB = NativeClient.loginUser(emailB, "123")!!
        val targetId = userB.id
        NativeClient.disconnect()

        // Login A để thực hiện gửi kết bạn
        NativeClient.connect(SERVER_IP, SERVER_PORT)
        val userA = NativeClient.loginUser(emailA, "123")!!

        val responses = mutableListOf<Pair<Int, Int>>() // List of (cmd, status)
        val latch = CountDownLatch(4) // Chờ 4 response

        val listener = object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_SEND_FRIEND_REQ_RESP) {
                    synchronized(responses) {
                        responses.add(Pair(cmd, status))
                    }
                    latch.countDown()
                }
            }
        }

        NativeClient.startListening(listener)

        Thread.sleep(200)

        // Gửi 4 request liên tiếp
        NativeClient.sendFriendRequest(targetId)       // Request 1: Lần đầu → SUCCESS (0)
        NativeClient.sendFriendRequest(targetId)       // Request 2: Lặp lại → PENDING (8)
        NativeClient.sendFriendRequest(targetId)       // Request 3: Lặp lại → PENDING (8)
        NativeClient.sendFriendRequest(userA.id)       // Request 4: Tự kết bạn → INVALID_PARAM (5)

        // Chờ tất cả response
        val success = latch.await(5, TimeUnit.SECONDS)
        Assert.assertTrue("Timeout: Không nhận đủ 4 response", success)

        // Kiểm tra kết quả
        Assert.assertEquals("Phải nhận đủ 4 response", 4, responses.size)

        // Response 1: SUCCESS
        Assert.assertEquals("Request 1 phải trả về SUCCESS", STATUS_SUCCESS, responses[0].second)

        // Response 2: PENDING
        Assert.assertEquals("Request 2 phải trả về PENDING", STATUS_ERR_REQ_PENDING, responses[1].second)

        // Response 3: PENDING
        Assert.assertEquals("Request 3 phải trả về PENDING", STATUS_ERR_REQ_PENDING, responses[2].second)

        // Response 4: INVALID_PARAM
        Assert.assertEquals("Request 4 (tự kết bạn) phải trả về INVALID_PARAM",
            STATUS_ERR_INVALID_PARAM, responses[3].second)
    }

    // ==========================================
    // MODULE 3: REAL-TIME NOTIFICATION (FAKE CLIENT)
    // ==========================================

    @Test
    fun test04_ReceiveRealtimeNotification() {
        val time = System.currentTimeMillis()
        val emailReceiver = "recv_$time@konni.com" // User A (Native)
        val emailSender = "send_$time@konni.com"   // User B (Fake TCP)
        val password = "123"

        // 1. Register both
        NativeClient.registerUser("Receiver", emailReceiver, password)
        NativeClient.registerUser("Sender", emailSender, password)

        // 2. Login User A (Native Client)
        val userA = NativeClient.loginUser(emailReceiver, password)!!

        val notifyLatch = CountDownLatch(1)
        var incomingSenderName = ""

        val listener = object : StubNativeEventListener() {
            override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {
                println("TEST: Nhận Request từ $senderName")
                incomingSenderName = senderName
                notifyLatch.countDown()
            }
        }
        NativeClient.startListening(listener)

        Thread.sleep(300)

        // 3. Fake Client User B login và gửi request
        val threadB = Thread {
            try {
                Thread.sleep(500)
                val fakeB = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeB.login(emailSender, password)

                // Đợi 1 chút để server xử lý login xong
                Thread.sleep(500)

                // B gửi request kết bạn tới A (ID của A)
                fakeB.sendFriendRequest(userA.id)
                Thread.sleep(500)

                fakeB.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        threadB.start()

        // 4. Kiểm tra User A có nhận được không
        val received = notifyLatch.await(10, TimeUnit.SECONDS)
        Assert.assertTrue("Timeout: Không nhận được Realtime Notification", received)
        // Lưu ý: Tên User đăng ký ở bước 1 là "Sender", check xem trả về có đúng không
        Assert.assertTrue(incomingSenderName.contains("Sender"))
    }
}

// =========================================================
// HELPER CLASSES
// =========================================================

open class StubNativeEventListener : NativeEventListener {
    override fun onFriendListReceived(friends: Array<UserDto>) {}
    override fun onFriendStatusChanged(friendId: Int, isOnline: Boolean) {}
    override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {}
    override fun onRequestResponse(cmd: Int, status: Int) {}
    override fun onConnectionClosed(reason: String) {}
}

/**
 * FakeTcpClient
 * Giả lập Client gửi gói tin Binary thô khớp hoàn toàn với struct C trong protocol.h
 */
class FakeTcpClient(ip: String, port: Int) {
    private val socket = Socket(ip, port)
    private val output = DataOutputStream(socket.getOutputStream())

    // Protocol Constants
    private val PROTOCOL_VERSION = 1
    private val PACKET_HEADER_SIZE = 28 // 4*5 + 8

    private val CMD_LOGIN = 12
    private val CMD_SEND_FRIEND_REQ = 42

    private val MAX_EMAIL_LEN = 256
    private val MAX_PASS_LEN = 128

    // Payload Size Calculation
    // LoginPayload: email[256] + pass[128] = 384 bytes
    private val LOGIN_PAYLOAD_SIZE = MAX_EMAIL_LEN + MAX_PASS_LEN
    // FriendReqPayload: target_id (int32) = 4 bytes
    private val FRIEND_REQ_PAYLOAD_SIZE = 4

    fun login(email: String, pass: String) {
        val totalSize = PACKET_HEADER_SIZE + LOGIN_PAYLOAD_SIZE
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN) // Quan trọng: C Server dùng Little Endian

        // 1. Header
        writeHeader(buffer, CMD_LOGIN, LOGIN_PAYLOAD_SIZE)

        // 2. Body (LoginPayload)
        // Write Email (Fixed 256 bytes)
        writeFixedString(buffer, email, MAX_EMAIL_LEN)
        // Write Password (Fixed 128 bytes)
        writeFixedString(buffer, pass, MAX_PASS_LEN)

        output.write(buffer.array())
        output.flush()
    }

    fun sendFriendRequest(targetId: Int) {
        val totalSize = PACKET_HEADER_SIZE + FRIEND_REQ_PAYLOAD_SIZE
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. Header
        writeHeader(buffer, CMD_SEND_FRIEND_REQ, FRIEND_REQ_PAYLOAD_SIZE)

        // 2. Body (FriendReqPayload)
        buffer.putInt(targetId)

        output.write(buffer.array())
        output.flush()
    }

    private fun writeHeader(buffer: ByteBuffer, cmd: Int, payloadSize: Int) {
        // Struct PacketHeader: version, cmd, size, reqId, status, timestamp
        buffer.putInt(PROTOCOL_VERSION)
        buffer.putInt(cmd)
        buffer.putInt(payloadSize)
        buffer.putInt(0) // Request ID (Fake = 0)
        buffer.putInt(0) // Status Code (Request gửi đi ko quan trọng status)
        buffer.putLong(System.currentTimeMillis()) // Timestamp
    }

    private fun writeFixedString(buffer: ByteBuffer, str: String, length: Int) {
        val strBytes = str.toByteArray(Charsets.UTF_8)
        // Ghi bytes của chuỗi
        val writeLen = Math.min(strBytes.size, length)
        buffer.put(strBytes, 0, writeLen)

        // Ghi padding (số 0) cho phần còn thiếu
        for (i in 0 until (length - writeLen)) {
            buffer.put(0.toByte())
        }
    }

    fun close() {
        socket.close()
    }
}
