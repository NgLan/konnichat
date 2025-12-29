package com.example.konnichat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.konnichat.core.exception.AuthenticationException
import com.example.konnichat.core.exception.UserAlreadyExistsException
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.NativeEventListener
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.remote.dto.UserSearchDto
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

        const val CMD_SEND_MESSAGE = 20
        const val CMD_SEND_MESSAGE_RESP = 21
        const val CMD_RECEIVE_MESSAGE = 22

        const val CMD_SEND_FRIEND_REQ = 42
        const val CMD_SEND_FRIEND_REQ_RESP = 43

        const val CMD_RESPOND_FRIEND_REQ = 44
        const val CMD_RESPOND_FRIEND_REQ_RESP = 45
        const val CMD_UNFRIEND_RESP = 47

        const val CMD_NOTIFY_FRIEND_REQ = 80
        const val CMD_NOTIFY_REQ_ACCEPTED = 81
        const val CMD_NOTIFY_MSG_DELIVERED = 85

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

    // ==========================================
    // MODULE 4: RESPOND FRIEND REQUEST (ACCEPT/DENY)
    // ==========================================

    @Test
    fun test05_RespondFriendRequest_Flow() {
        val time = System.currentTimeMillis()
        val emailA = "requester_$time@konni.com" // Người gửi
        val emailB = "responder_$time@konni.com" // Người nhận (Sẽ chấp nhận)

        // 1. Đăng ký 2 user
        NativeClient.registerUser("Requester", emailA, "123")
        NativeClient.registerUser("Responder", emailB, "123")

        // 2. Login User B (Người nhận) trước để hứng Notification lấy RequestID
        // (Vì API getPendingRequests chưa có trong bài test này nên ta dùng cách bắt Notif để lấy ID)
        val userB = NativeClient.loginUser(emailB, "123")!!
        val targetId = userB.id

        val latchId = CountDownLatch(1)
        var capturedRequestId = -1

        val listenerB = object : StubNativeEventListener() {
            override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {
                capturedRequestId = requestId
                latchId.countDown()
            }
        }
        NativeClient.startListening(listenerB)
        Thread.sleep(200)

        // 3. Dùng Thread giả lập User A gửi Request cho B
        val threadA = Thread {
            val fakeA = FakeTcpClient(SERVER_IP, SERVER_PORT)
            fakeA.login(emailA, "123")
            Thread.sleep(500)
            fakeA.sendFriendRequest(targetId)
            fakeA.close()
        }
        threadA.start()

        // 4. Chờ B nhận được thông báo có Request mới
        val receivedReq = latchId.await(5, TimeUnit.SECONDS)
        Assert.assertTrue("User B không nhận được thông báo Request để lấy ID", receivedReq)
        Assert.assertTrue("Request ID phải > 0", capturedRequestId > 0)

        // 5. User B thực hiện CHẤP NHẬN kết bạn
        val latchRespond = CountDownLatch(1)
        var respondStatus = -1

        // Đăng ký listener mới để bắt phản hồi của lệnh Respond
        val listenerRespond = object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_RESPOND_FRIEND_REQ_RESP) {
                    respondStatus = status
                    latchRespond.countDown()
                }
            }
        }
        NativeClient.startListening(listenerRespond)

        // Gọi hàm Native (Giả định bạn đã mapping hàm này ở bước trước)
        NativeClient.respondFriendRequest(capturedRequestId, true)

        val responded = latchRespond.await(5, TimeUnit.SECONDS)
        Assert.assertTrue("Không nhận được phản hồi từ Server sau khi Respond", responded)
        Assert.assertEquals("Status chấp nhận phải là SUCCESS (0)", STATUS_SUCCESS, respondStatus)
    }

    @Test
    fun test06_Realtime_Accept_Notification() {
        // Kịch bản:
        // 1. Login B (Native) để hứng Request ID.
        // 2. Fake A gửi Request kết bạn.
        // 3. Login A (Native) để chờ thông báo.
        // 4. Fake B gửi lệnh Chấp nhận (Accept).
        // 5. A nhận thông báo -> Success.

        val time = System.currentTimeMillis()
        val emailA = "userA_$time@konni.com"
        val emailB = "userB_$time@konni.com"
        val pass = "123"

        // 1. Đăng ký
        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // --- BƯỚC 1: Login B (Native) để lấy ID của B và Hứng Request ID ---
        // Cần đảm bảo kết nối mới sạch sẽ
        NativeClient.disconnect()
        Thread.sleep(200)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))

        val userB = NativeClient.loginUser(emailB, pass)!!
        val myIdB = userB.id

        val latchGetId = CountDownLatch(1)
        var reqId = 0

        val listenerB = object : StubNativeEventListener() {
            override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {
                println("TEST: B nhận được request $requestId từ $senderName")
                reqId = requestId
                latchGetId.countDown()
            }
        }
        NativeClient.startListening(listenerB)

        // --- BƯỚC 2: Fake A đăng nhập và gửi Request cho B ---
        val threadFakeA = Thread {
            try {
                val fakeA = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeA.login(emailA, pass)
                Thread.sleep(300) // Chờ server xử lý login
                fakeA.sendFriendRequest(myIdB) // Gửi tới ID của B
                Thread.sleep(300)
                fakeA.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadFakeA.start()

        // Chờ B nhận được Request ID
        val receivedId = latchGetId.await(5, TimeUnit.SECONDS)
        Assert.assertTrue("User B không nhận được thông báo Request", receivedId)
        Assert.assertTrue("Request ID phải > 0", reqId > 0)

        // B thoát ra để nhường sân khấu cho A
        NativeClient.disconnect()
        Thread.sleep(500) // [QUAN TRỌNG] Chờ Socket B đóng hẳn

        // --- BƯỚC 3: Login A (Native) và chờ thông báo Accepted ---
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchNotif = CountDownLatch(1)
        var acceptorName = ""

        val listenerA = object : StubNativeEventListener() {
            override fun onFriendRequestAccepted(user: UserDto) {
                println("TEST: A nhận thông báo chấp nhận từ ${user.name} (ID: ${user.id})")
                acceptorName = user.name
                latchNotif.countDown()
            }
        }
        NativeClient.startListening(listenerA)

        // --- BƯỚC 4: Fake B đăng nhập và gửi lệnh ACCEPT ---
        val threadFakeB = Thread {
            try {
                val fakeB = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeB.login(emailB, pass)
                Thread.sleep(300)
                // Gửi lệnh Accept với reqId đã lấy được ở Bước 1
                fakeB.respondFriendRequest(reqId, true)
                Thread.sleep(300)
                fakeB.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadFakeB.start()

        // --- BƯỚC 5: Kiểm tra A nhận được thông báo ---
        val success = latchNotif.await(8, TimeUnit.SECONDS)
        Assert.assertTrue("User A (Online) không nhận được thông báo Accepted", success)
        Assert.assertTrue("Tên người chấp nhận không được rỗng", acceptorName.isNotEmpty())
    }

    // ==========================================
    // MODULE 5: UNFRIEND FLOW
    // ==========================================

    @Test
    fun test07_Unfriend_Flow_Realtime() {
        val time = System.currentTimeMillis()
        val emailA = "A_$time@konni.com"
        val emailB = "B_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // --- BƯỚC 1: Thiết lập quan hệ bạn bè ---
        // (Để code ngắn gọn, ta dùng FakeClient kết bạn nhanh)

        // 1.1 Login A lấy ID
        NativeClient.disconnect()
        Thread.sleep(200)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userA = NativeClient.loginUser(emailA, pass)!!
        val idA = userA.id

        // Setup listener A để chờ thông báo Unfriend sau này
        val latchUnfriendNotif = CountDownLatch(1)
        var whoUnfriendedMe = 0

        val listenerA = object : StubNativeEventListener() {
            override fun onFriendRemoved(exFriendId: Int) {
                println("TEST: Nhận thông báo bị hủy kết bạn bởi ID: $exFriendId")
                whoUnfriendedMe = exFriendId
                latchUnfriendNotif.countDown()
            }
        }
        NativeClient.startListening(listenerA)

        // 1.2 Fake B login -> Gửi request -> A accept (Giả lập nhanh)
        // (Vì bài test trước đã verify flow kết bạn, ở đây ta có thể "cheat" bằng cách insert DB trực tiếp nếu có API test,
        // nhưng để chuẩn flow ta vẫn làm: B gửi -> A accept).

        // Login B tạm để lấy ID B
        val fakeB = FakeTcpClient(SERVER_IP, SERVER_PORT)
        fakeB.login(emailB, pass)
        Thread.sleep(200)
        fakeB.sendFriendRequest(idA) // B gửi cho A
        fakeB.close()

        // A (đang online) nhận request -> Accept
        // (Để đơn giản, ta assume ID request tăng dần hoặc lấy từ DB,
        // ở đây ta sẽ cheat bằng cách gọi API accept với RequestID "đoán" hoặc bỏ qua bước verify ID chính xác
        // mà tập trung vào bước Unfriend. Nhưng để chạy được phải có friend.
        // -> Ta dùng NativeClient login B để làm flow chuẩn nhanh hơn).

        NativeClient.disconnect() // A out ra
        Thread.sleep(500)

        // Login B (Native)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userB = NativeClient.loginUser(emailB, pass)!!
        val idB = userB.id

        // Fake A gửi request cho B
        val fakeA = FakeTcpClient(SERVER_IP, SERVER_PORT)
        fakeA.login(emailA, pass)
        Thread.sleep(200)
        fakeA.sendFriendRequest(idB)
        fakeA.close()

        // B (Native) hứng request và accept
        val latchReq = CountDownLatch(1)
        var reqId = 0
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onFriendRequestReceived(requestId: Int, senderId: Int, senderName: String) {
                reqId = requestId
                latchReq.countDown()
            }
        })
        latchReq.await(3, TimeUnit.SECONDS)

        // B Accept
        NativeClient.respondFriendRequest(reqId, true)
        Thread.sleep(200) // Đợi DB commit

        // --- BƯỚC 2: A Online trở lại ---
        NativeClient.disconnect() // B out
        Thread.sleep(500)

        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        // A lắng nghe sự kiện bị Unfriend
        NativeClient.startListening(listenerA)

        // --- BƯỚC 3: Fake B login và thực hiện UNFRIEND A ---
        val threadAction = Thread {
            try {
                val fb = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fb.login(emailB, pass)
                Thread.sleep(300)

                // Gửi lệnh Unfriend (CMD 46)
                fb.unfriendUser(idA)

                Thread.sleep(300)
                fb.close()
            } catch(e: Exception) { e.printStackTrace() }
        }
        threadAction.start()

        // --- BƯỚC 4: Kiểm tra A nhận được thông báo ---
        val success = latchUnfriendNotif.await(5, TimeUnit.SECONDS)
        Assert.assertTrue("A không nhận được thông báo bị hủy kết bạn", success)
        Assert.assertEquals("Người hủy phải là B", idB, whoUnfriendedMe)
    }

    // ==========================================
    // MODULE 6: SEARCH USERS (PAGINATION & TRIM)
    // ==========================================

    @Test
    fun test08_SearchUsers_Pagination_Trim() {
        val time = System.nanoTime()
        val searcherEmail = "searcher_$time@konni.com"
        val pass = "123"

        // 1. Đăng ký người đi tìm (Searcher)
        NativeClient.registerUser("Searcher", searcherEmail, pass)

        // 2. Đăng ký 25 người dùng mục tiêu để test phân trang
        // Tên sẽ là: "TargetUser 00", "TargetUser 01", ... "TargetUser 24"
        // Keyword chung là "TargetUser"
        val prefix = "TargetUser_$time"
        for (i in 0 until 25) {
            val email = "target_${i}_$time@konni.com"
            val name = "$prefix $i"
            NativeClient.registerUser(name, email, pass)
        }

        // 3. Login Searcher
        NativeClient.disconnect()
        Thread.sleep(200)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(searcherEmail, pass)

        // --- TEST CASE 1: PAGINATION PAGE 1 (Offset 0, Limit 10) ---
        val latchPage1 = CountDownLatch(1)
        var resultsPage1: Array<UserSearchDto>? = null

        val listener = object : StubNativeEventListener() {
            override fun onSearchResult(results: Array<UserSearchDto>) {
                if (resultsPage1 == null) {
                    resultsPage1 = results
                    latchPage1.countDown()
                } else {
                    // Xử lý cho các lần gọi sau (Page 2, Trim...)
                }
            }
        }
        NativeClient.startListening(listener)

        // Gọi tìm kiếm Page 1
        NativeClient.searchUsers(prefix, 0, 10)

        Assert.assertTrue("Timeout Page 1", latchPage1.await(5, TimeUnit.SECONDS))
        Assert.assertNotNull(resultsPage1)
        Assert.assertEquals("Page 1 phải trả về 10 kết quả", 10, resultsPage1!!.size)
        // Kiểm tra tên có chứa keyword không
        Assert.assertTrue(resultsPage1!![0].name.contains(prefix))

        // --- TEST CASE 2: PAGINATION PAGE 2 (Offset 10, Limit 10) ---
        // Khi lướt xuống, lấy tiếp 10 người nữa
        val latchPage2 = CountDownLatch(1)
        var resultsPage2: Array<UserSearchDto>? = null

        // Update listener hoặc dùng logic check biến flag, ở đây ta gán đè listener mới cho gọn
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onSearchResult(results: Array<UserSearchDto>) {
                resultsPage2 = results
                latchPage2.countDown()
            }
        })

        // Gọi tìm kiếm Page 2 (Offset = 10)
        NativeClient.searchUsers(prefix, 10, 10)

        Assert.assertTrue("Timeout Page 2", latchPage2.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Page 2 phải trả về 10 kết quả", 10, resultsPage2!!.size)

        // Kiểm tra Page 2 không trùng Page 1 (So sánh ID phần tử đầu tiên)
        Assert.assertNotEquals("Page 2 phải khác Page 1",
            resultsPage1!![0].id, resultsPage2!![0].id)

        // --- TEST CASE 3: PAGINATION PAGE 3 (Offset 20, Limit 10) ---
        // Chỉ còn 5 người (Tổng 25, đã lấy 20) -> Phải trả về 5
        val latchPage3 = CountDownLatch(1)
        var countPage3 = 0

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onSearchResult(results: Array<UserSearchDto>) {
                countPage3 = results.size
                latchPage3.countDown()
            }
        })

        NativeClient.searchUsers(prefix, 20, 10)
        Assert.assertTrue("Timeout Page 3", latchPage3.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Page 3 phải trả về 5 kết quả còn lại", 5, countPage3)

        // --- TEST CASE 4: TRIM STRING ---
        // Gửi chuỗi có khoảng trắng: "  TargetUser...  "
        // Nếu Client không trim, Server sẽ tìm chính xác và không ra kết quả (vì tên trong DB không có space ở đầu/cuối).
        // Nếu Client trim đúng, Server sẽ tìm ra kết quả.

        val latchTrim = CountDownLatch(1)
        var countTrim = 0

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onSearchResult(results: Array<UserSearchDto>) {
                countTrim = results.size
                latchTrim.countDown()
            }
        })

        val dirtyKeyword = "   $prefix   " // Thêm space đầu cuối
        NativeClient.searchUsers(dirtyKeyword, 0, 10)

        Assert.assertTrue("Timeout Trim Test", latchTrim.await(5, TimeUnit.SECONDS))
        Assert.assertTrue("Phải tìm thấy kết quả dù keyword có space (Client phải trim)", countTrim > 0)
    }

    // ==========================================
    // MODULE 6: CHAT 1-1 (ONLINE & OFFLINE)
    // ==========================================

    @Test
    fun test09_SendMessage_Online_Flow() {
        // Kịch bản: A gửi tin cho B (B đang Online).
        // Mong đợi:
        // 1. A nhận ACK (Sent) -> Room update Sent.
        // 2. A nhận Delivered Notif (Vì B online nhận được ngay).

        val time = System.currentTimeMillis()
        val emailA = "chatA_$time@konni.com"
        val emailB = "chatB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // 1. Lấy ID của B
        NativeClient.disconnect()
        Thread.sleep(200)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userB = NativeClient.loginUser(emailB, pass)!!
        val idB = userB.id
        NativeClient.disconnect()
        Thread.sleep(500)

        // 2. FakeClient B đăng nhập và treo đó (Simulate Online)
        val latchFakeBReady = CountDownLatch(1)
        val threadFakeB = Thread {
            try {
                val fb = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fb.login(emailB, pass)
                latchFakeBReady.countDown()
                Thread.sleep(3000) // Giữ kết nối
                fb.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadFakeB.start()
        latchFakeBReady.await(2, TimeUnit.SECONDS)

        // 3. Login A (Native)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchSent = CountDownLatch(1)
        val latchDelivered = CountDownLatch(1)
        var serverMsgId = 0
        val tempMsgId = 12345 // Giả lập ID Room

        val listenerA = object : StubNativeEventListener() {
            override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
                println("TEST: onMessageSent ACK. Temp=$tempId, ServerID=$serverId")
                if (tempId == tempMsgId) {
                    serverMsgId = serverId
                    latchSent.countDown()
                }
            }
            override fun onMessageDelivered(serverId: Int) {
                println("TEST: onMessageDelivered. ServerID=$serverId")
                if (serverId == serverMsgId) latchDelivered.countDown()
            }
        }
        NativeClient.startListening(listenerA)

        // 4. Gửi tin (Thêm tham số "private")
        NativeClient.sendMessage(idB, "Hello B Online", tempMsgId, "private")

        // 5. Verify
        Assert.assertTrue("A không nhận được ACK Sent", latchSent.await(5, TimeUnit.SECONDS))
        Assert.assertTrue("A không nhận được báo Delivered", latchDelivered.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun test10_ReceiveMessage_Flow() {
        // Kịch bản: B (FakeClient) gửi tin cho A (Native - Online).
        // Mong đợi: A nhận được tin nhắn qua callback onMessageReceived.

        val time = System.currentTimeMillis()
        val emailA = "recvA_$time@konni.com"
        val emailB = "sendB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // 1. Login A
        NativeClient.disconnect()
        Thread.sleep(200)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userA = NativeClient.loginUser(emailA, pass)!!

        val latchReceived = CountDownLatch(1)
        var receivedContent = ""
        var receivedChatType = ""

        val listenerA = object : StubNativeEventListener() {
            override fun onMessageReceived(msg: MessageDto) {
                println("TEST: Received Msg: ${msg.content}, Type: ${msg.chatType}")
                receivedContent = msg.content
                receivedChatType = msg.chatType
                latchReceived.countDown()
            }
        }
        NativeClient.startListening(listenerA)

        // 2. FakeClient B gửi tin
        val msgContent = "Greetings from FakeClient"
        val threadFakeB = Thread {
            try {
                val fb = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fb.login(emailB, pass)
                Thread.sleep(500)
                // Gửi tin với chatType "private"
                fb.sendMessage(userA.id, msgContent, "private")
                Thread.sleep(500)
                fb.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadFakeB.start()

        Assert.assertTrue("A không nhận được tin nhắn", latchReceived.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(msgContent, receivedContent)
        Assert.assertEquals("private", receivedChatType)
    }

    @Test
    fun test11_OfflineMessage_Flow_Safe() {
        val time = System.currentTimeMillis()
        val emailA = "offA_$time@konni.com"
        val emailB = "offB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // 1. Setup B Offline
        NativeClient.disconnect()
        Thread.sleep(200)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userB = NativeClient.loginUser(emailB, pass)!!
        val idB = userB.id
        NativeClient.disconnect()
        Thread.sleep(500)

        // 2. A gửi tin cho B
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchSent = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
                latchSent.countDown()
            }
        })

        val offlineContent = "Safe Offline Msg"
        NativeClient.sendMessage(idB, offlineContent, 7777, "private")
        latchSent.await(5, TimeUnit.SECONDS)

        NativeClient.disconnect()
        Thread.sleep(500)

        // 3. B Login -> Start Listening -> Fetch Offline
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))

        // 3.1 Login (Synchronous) - Server chưa gửi tin offline lúc này
        NativeClient.loginUser(emailB, pass)

        // 3.2 Start Listening (Sẵn sàng nhận tin)
        val latchRecv = CountDownLatch(1)
        var msgContent = ""

        val listenerB = object : StubNativeEventListener() {
            override fun onMessageReceived(msg: MessageDto) {
                println("TEST: B received: ${msg.content}")
                if (msg.content == offlineContent) {
                    msgContent = msg.content
                    latchRecv.countDown()
                }
            }
        }
        NativeClient.startListening(listenerB)

        // 3.3 [QUAN TRỌNG] Chủ động gọi lấy tin offline
        // Lúc này Client đã có luồng đọc, nên Server đẩy tin về là nhận được ngay
        NativeClient.fetchOfflineMessages()

        // 4. Verify
        Assert.assertTrue("Timeout receiving offline msg", latchRecv.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(offlineContent, msgContent)
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
    override fun onFriendRequestAccepted(user: UserDto) {}
    override fun onFriendRemoved(exFriendId: Int) {}
    override fun onSearchResult(results: Array<UserSearchDto>) {}
    override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {}
    override fun onMessageReceived(msg: MessageDto) {}
    override fun onMessageDelivered(serverId: Int) {}
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

    private val CMD_SEND_MESSAGE = 20
    private val CMD_SEND_FRIEND_REQ = 42

    private val CMD_RESPOND_FRIEND_REQ = 44
    private val CMD_UNFRIEND = 46

    // Sizes
    private val MAX_EMAIL_LEN = 256
    private val MAX_PASS_LEN = 128
    private val MAX_CONTENT_LEN = 1024

    // Payload Size Calculation
    // LoginPayload: email[256] + pass[128] = 384 bytes
    private val LOGIN_PAYLOAD_SIZE = MAX_EMAIL_LEN + MAX_PASS_LEN
    // FriendReqPayload: target_id (int32) = 4 bytes
    private val FRIEND_REQ_PAYLOAD_SIZE = 4
    // Payload FriendRespondPayload: request_id(4) + is_accepted(1) = 5 bytes
    private val RESPOND_PAYLOAD_SIZE = 5
    // struct ChatPayload {
    //   int32 message_id;  (4)
    //   int32 sender_id;   (4)
    //   int32 receiver_id; (4)
    //   int32 msg_type;    (4)
    //   char chat_type[16];(16)
    //   char content[1024];(1024)
    //   uint64 created_at; (8)
    // }
    // Total = 4+4+4+4+16+1024+8 = 1064
    private val CHAT_PAYLOAD_SIZE = 1064

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

    fun respondFriendRequest(requestId: Int, isAccepted: Boolean) {
        val totalSize = PACKET_HEADER_SIZE + RESPOND_PAYLOAD_SIZE
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. Header
        writeHeader(buffer, CMD_RESPOND_FRIEND_REQ, RESPOND_PAYLOAD_SIZE)

        // 2. Payload
        buffer.putInt(requestId)
        buffer.put((if (isAccepted) 1 else 0).toByte())

        output.write(buffer.array())
        output.flush()
    }
    fun unfriendUser(targetId: Int) {
        // FriendReqPayload: target_id (4 bytes)
        val payloadSize = 4
        val totalSize = PACKET_HEADER_SIZE + payloadSize
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        writeHeader(buffer, CMD_UNFRIEND, payloadSize)
        buffer.putInt(targetId)

        output.write(buffer.array())
        output.flush()
    }

    fun sendMessage(receiverId: Int, content: String, chatType: String = "private") {
        val totalSize = PACKET_HEADER_SIZE + CHAT_PAYLOAD_SIZE
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. Header
        writeHeader(buffer, CMD_SEND_MESSAGE, CHAT_PAYLOAD_SIZE)

        // 2. Body (ChatPayload)
        buffer.putInt(0)            // message_id (Server sinh)
        buffer.putInt(0)            // sender_id (Server tự điền)
        buffer.putInt(receiverId)   // receiver_id
        buffer.putInt(1)            // msg_type (1=Text)

        writeFixedString(buffer, chatType, 16) // chat_type (MỚI)
        writeFixedString(buffer, content, MAX_CONTENT_LEN) // content

        buffer.putLong(System.currentTimeMillis()) // created_at

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
