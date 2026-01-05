package com.example.konnichat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.konnichat.core.exception.AuthenticationException
import com.example.konnichat.core.exception.UserAlreadyExistsException
import com.example.konnichat.data.remote.NativeClient
import com.example.konnichat.data.remote.NativeEventListener
import com.example.konnichat.data.remote.dto.GroupDto
import com.example.konnichat.data.remote.dto.MessageDto
import com.example.konnichat.data.remote.dto.PendingRequestDto
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

        const val CMD_CREATE_GROUP = 30
        const val CMD_CREATE_GROUP_RESP = 31
        const val CMD_ADD_MEMBER = 32
        const val CMD_ADD_MEMBER_RESP = 33
        const val CMD_REMOVE_MEMBER = 34
        const val CMD_REMOVE_MEMBER_RESP = 35
        const val CMD_LEAVE_GROUP = 36
        const val CMD_LEAVE_GROUP_RESP = 37
        const val CMD_DISSOLVE_GROUP = 38
        const val CMD_DISSOLVE_GROUP_RESP = 39

        const val CMD_GET_GROUP_LIST = 50
        const val CMD_GET_GROUP_LIST_RESP = 51

        const val CMD_SEND_FRIEND_REQ = 42
        const val CMD_SEND_FRIEND_REQ_RESP = 43

        const val CMD_RESPOND_FRIEND_REQ = 44
        const val CMD_RESPOND_FRIEND_REQ_RESP = 45
        const val CMD_UNFRIEND_RESP = 47

        const val CMD_GET_HISTORY = 70
        const val CMD_GET_HISTORY_RESP = 71

        const val CMD_NOTIFY_FRIEND_REQ = 80
        const val CMD_NOTIFY_REQ_ACCEPTED = 81
        const val CMD_NOTIFY_MSG_DELIVERED = 85
        const val CMD_NOTIFY_GROUP_CREATED = 86
        const val CMD_NOTIFY_MEMBERS_ADDED = 87
        const val CMD_NOTIFY_MEMBER_LEFT = 88
        const val CMD_NOTIFY_MEMBER_REMOVED = 89
        const val CMD_NOTIFY_GROUP_DISSOLVED = 90

        // --- Status Codes ---
        const val STATUS_SUCCESS = 0
        const val STATUS_ERR_AUTH = 2
        const val STATUS_ERR_USER_NOT_FOUND = 3
        const val STATUS_ERR_INVALID_PARAM = 5
        const val STATUS_ERR_ALREADY_EXIST = 6
        const val STATUS_ERR_ALREADY_FRIEND = 7
        const val STATUS_ERR_REQ_PENDING = 8
        const val STATUS_ERROR_NOT_GROUP_ADMIN = 11
        const val STATUS_ERROR_CANNOT_REMOVE_SELF = 12
        // sizes
        const val MAX_GROUP_NAME = 100

        // Message Type
        const val MSG_TYPE_SYSTEM = 9
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
            resultsPage1!![0].userId, resultsPage2!![0].userId)

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
        val idA = helperGetUserId(emailA, pass)

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
        NativeClient.sendMessage(idA,idB, "Hello B Online", tempMsgId, "private")

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
        val idA = helperGetUserId(emailA, pass)

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
        NativeClient.sendMessage(idA,idB, offlineContent, 7777, "private")
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

    @Test
    fun test11_b_Offline_Ignore_Group_Messages() {
        val time = System.currentTimeMillis()
        val emailA = "ignA_$time@konni.com"
        val emailB = "ignB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        val idA = helperGetUserId(emailA, pass)
        val idB = helperGetUserId(emailB, pass)

        // 1. Tạo nhóm có A và B
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchGroup = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchGroup.countDown()
            }
        })
        NativeClient.createGroup("Ignore Group", intArrayOf(idB))
        latchGroup.await(5, TimeUnit.SECONDS)

        // 2. A gửi tin khi B đang OFFLINE (Vì B chưa login sau khi A connect lại)
        val latchSent = CountDownLatch(2)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
                latchSent.countDown()
            }
        })

        // Gửi 1 tin Private (Mong đợi: Sẽ nhận được khi fetch offline)
        NativeClient.sendMessage(idA, idB, "PRIVATE_MSG", 1, "private")

        // Gửi 1 tin Group (Mong đợi: KHÔNG nhận được khi fetch offline)
        NativeClient.sendMessage(idA, groupId, "GROUP_MSG", 2, "group")

        Assert.assertTrue(latchSent.await(5, TimeUnit.SECONDS))
        NativeClient.disconnect()
        Thread.sleep(500)

        // 3. B Online và Fetch Offline
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailB, pass)

        val latchRecv = CountDownLatch(1)
        val receivedMsgs = mutableListOf<String>()

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMessageReceived(msg: MessageDto) {
                synchronized(receivedMsgs) {
                    receivedMsgs.add(msg.content)
                }
                // Chỉ đếm xuống khi nhận tin PRIVATE
                if (msg.chatType == "private") {
                    latchRecv.countDown()
                }
            }
        })

        // Gọi lệnh lấy tin offline
        NativeClient.fetchOfflineMessages()

        // 4. Verify
        // Chờ tin Private về
        Assert.assertTrue("Không nhận được tin Private Offline", latchRecv.await(5, TimeUnit.SECONDS))

        // Chờ thêm 1 chút để chắc chắn không có tin rác nào khác về
        Thread.sleep(500)

        synchronized(receivedMsgs) {
            // Kiểm tra danh sách tin nhận được
            Assert.assertTrue("Phải nhận được tin Private", receivedMsgs.contains("PRIVATE_MSG"))
            Assert.assertFalse("Không được nhận tin Group qua fetchOffline", receivedMsgs.contains("GROUP_MSG"))
        }
    }

    // ==========================================
    // MODULE 7: CHAT HISTORY (COMPREHENSIVE)
    // ==========================================

    /**
     * Test 12: Private Chat History (1-1)
     * - Kiểm tra tính năng phân trang (Pagination).
     * - Kiểm tra thứ tự sắp xếp (Mới nhất trước).
     * - Kiểm tra nội dung và timestamp.
     */
    @Test
    fun test12_GetHistory_Private_Pagination() {
        val time = System.currentTimeMillis()
        val emailA = "histA_$time@konni.com"
        val emailB = "histB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        val idB = helperGetUserId(emailB, pass)
        val idA = helperGetUserId(emailA, pass) // Lấy luôn ID của A

        // --- SEEDING: A gửi và B gửi ---
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        // Bỏ CountDownLatch vì nó không đáng tin cậy trong kịch bản này
        // A gửi 3 tin
        for (i in 1..3) {
            NativeClient.sendMessage(idA, idB, "Msg_$i", i, "private")
            Thread.sleep(50)
        }

        // B dùng FakeClient gửi 3 tin
        val threadB = Thread {
            try {
                val fb = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fb.login(emailB, pass)
                for (i in 4..6) {
                    fb.sendMessage(idA, "Msg_$i", "private")
                    Thread.sleep(50)
                }
                fb.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadB.start()
        threadB.join() // Chờ B gửi xong

        // A gửi tiếp 4 tin
        for (i in 7..10) {
            NativeClient.sendMessage(idA, idB, "Msg_$i", i, "private")
            Thread.sleep(50)
        }

        // Chờ 1 giây để server chắc chắn xử lý hết
        Thread.sleep(1000)

        // --- VERIFY PAGINATION ---
        val historyList = mutableListOf<MessageDto>()
        var historyLatch = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                synchronized(historyList) {
                    historyList.clear()
                    historyList.addAll(messages)
                }
                historyLatch.countDown()
            }
        })

        // PAGE 1: Lấy 4 tin mới nhất
        NativeClient.getChatHistory(idB, false, 0, 4)
        Assert.assertTrue("Timeout Page 1", historyLatch.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(4, historyList.size)
        Assert.assertEquals("Msg_10", historyList[0].content)
        Assert.assertEquals("Msg_7", historyList[3].content)

        // PAGE 2: Lấy 4 tin tiếp theo
        historyLatch = CountDownLatch(1)
        NativeClient.getChatHistory(idB, false, 4, 4)
        Assert.assertTrue("Timeout Page 2", historyLatch.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(4, historyList.size)
        Assert.assertEquals("Msg_6", historyList[0].content)
        Assert.assertEquals("Msg_3", historyList[3].content)

        // PAGE 3: Lấy phần còn lại (2 tin)
        historyLatch = CountDownLatch(1)
        NativeClient.getChatHistory(idB, false, 8, 4)
        Assert.assertTrue("Timeout Page 3", historyLatch.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(2, historyList.size)
        Assert.assertEquals("Msg_2", historyList[0].content)
        Assert.assertEquals("Msg_1", historyList[1].content)
    }

    /**
     * Test 13: Group Chat History
     * - Kiểm tra lấy lịch sử chat nhóm (isGroup = true).
     * - Đảm bảo tin nhắn có chatType = "group".
     */
    @Test
    fun test13_GetHistory_Group_Flow() {
        val time = System.currentTimeMillis()
        val emailA = "gHistA_$time@konni.com"
        val emailB = "gHistB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)
        val idA = helperGetUserId(emailA, pass)
        val idB = helperGetUserId(emailB, pass)

        // 1. A tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchGroup = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchGroup.countDown()
            }
        })
        NativeClient.createGroup("History Group", intArrayOf(idB))
        latchGroup.await(5, TimeUnit.SECONDS)

        // 2. Seeding: Gửi 5 tin vào nhóm
        val latchSeed = CountDownLatch(5)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
                latchSeed.countDown()
            }
        })

        for (i in 1..5) {
            // Lưu ý: gửi vào groupId, chatType="group"
            NativeClient.sendMessage(idA,groupId, "G_Msg_$i", i, "group")
            Thread.sleep(50)
        }
        Assert.assertTrue(latchSeed.await(5, TimeUnit.SECONDS))

        // 3. Get Group History
        val latchHist = CountDownLatch(1)
        val list = mutableListOf<MessageDto>()

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                list.addAll(messages)
                latchHist.countDown()
            }
        })

        // Gọi hàm với isGroup = true
        NativeClient.getChatHistory(groupId, true, 0, 10)

        Assert.assertTrue(latchHist.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Phải lấy được 5 tin nhắn nhóm", 5, list.size)

        // Kiểm tra tin mới nhất
        Assert.assertEquals("G_Msg_5", list[0].content)
        // Kiểm tra Chat Type
        Assert.assertEquals("group", list[0].chatType)
        // Kiểm tra Receiver ID phải là GroupID
        Assert.assertEquals(groupId, list[0].receiverId)
    }

    /**
     * Test 23: Data Isolation (QUAN TRỌNG NHẤT)
     * - Chat Private không được lẫn vào Chat Group.
     * - Chat Group không được lẫn vào Chat Private.
     */
    @Test
    fun test23_GetHistory_Isolation_PrivateVsGroup() {
        val time = System.currentTimeMillis()
        val emailA = "isoA_$time@konni.com"
        val emailB = "isoB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)
        val idA = helperGetUserId(emailA, pass)
        val idB = helperGetUserId(emailB, pass)

        // 1. Login A và Tạo nhóm với B
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Isolation Group", intArrayOf(idB))
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. Seeding Dữ liệu hỗn hợp
        val latchSeed = CountDownLatch(2)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {
                latchSeed.countDown()
            }
        })

        // Tin 1: Gửi Private cho B
        NativeClient.sendMessage(idA,idB, "SECRET_PRIVATE_MSG", 1, "private")
        Thread.sleep(100)
        // Tin 2: Gửi Group (nơi có B là thành viên)
        NativeClient.sendMessage(idA,groupId, "PUBLIC_GROUP_MSG", 2, "group")

        Assert.assertTrue(latchSeed.await(5, TimeUnit.SECONDS))

        // 3. VERIFY: Lấy lịch sử Private (với B)
        // Mong đợi: Chỉ thấy tin PRIVATE, không thấy tin GROUP
        val latchPriv = CountDownLatch(1)
        var privList: Array<MessageDto>? = null

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                privList = messages
                latchPriv.countDown()
            }
        })

        NativeClient.getChatHistory(idB, false, 0, 10) // isGroup = false
        latchPriv.await(5, TimeUnit.SECONDS)

        Assert.assertNotNull(privList)
        Assert.assertEquals("Private history chỉ có 1 tin", 1, privList!!.size)
        Assert.assertEquals("SECRET_PRIVATE_MSG", privList!![0].content)

        // 4. VERIFY: Lấy lịch sử Group
        // Mong đợi: Chỉ thấy tin GROUP, không thấy tin PRIVATE
        val latchGroup = CountDownLatch(1)
        var groupList: Array<MessageDto>? = null

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                groupList = messages
                latchGroup.countDown()
            }
        })

        NativeClient.getChatHistory(groupId, true, 0, 10) // isGroup = true
        latchGroup.await(5, TimeUnit.SECONDS)

        Assert.assertNotNull(groupList)
        Assert.assertEquals("Group history chỉ có 1 tin", 1, groupList!!.size)
        Assert.assertEquals("PUBLIC_GROUP_MSG", groupList!![0].content)
    }

    /**
     * Test 24: Empty & Edge Cases
     * - Lấy lịch sử với người chưa từng chat.
     * - Lấy lịch sử với ID không tồn tại.
     */
    @Test
    fun test24_GetHistory_Empty_And_EdgeCases() {
        val time = System.currentTimeMillis()
        val email = "edge_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("EdgeUser", email, pass)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(email, pass)

        val latch = CountDownLatch(1)
        var msgCount = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                msgCount = messages.size
                latch.countDown()
            }
        })

        // Case 1: Lấy lịch sử với user ID 999999 (Không tồn tại)
        NativeClient.getChatHistory(999999, false, 0, 10)

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Lịch sử phải rỗng", 0, msgCount)

        // Case 2: Lấy lịch sử Group ID 999999 (Không tồn tại)
        val latch2 = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                msgCount = messages.size
                latch2.countDown()
            }
        })
        NativeClient.getChatHistory(999999, true, 0, 10)
        Assert.assertTrue(latch2.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Lịch sử group rỗng", 0, msgCount)
    }

    // ==========================================
    // MODULE 8: GROUP MANAGEMENT
    // ==========================================

    @Test
    fun test14_CreateGroup_Success_Flow() {
        val time = System.currentTimeMillis()
        val nameA = "Owner_$time"
        val emailA = "owner_$time@konni.com"
        val emailB = "memberB_$time@konni.com"
        val emailC = "memberC_$time@konni.com"
        val pass = "123"

        // 1. Đăng ký các thành viên (Dùng kết nối từ @Before)
        NativeClient.registerUser(nameA, emailA, pass)
        NativeClient.registerUser("MemberB", emailB, pass)
        NativeClient.registerUser("MemberC", emailC, pass)

        // 2. Lấy ID của User B
        val userB = NativeClient.loginUser(emailB, pass)!!
        val idB = userB.id
        NativeClient.disconnect() // Ngắt kết nối để làm sạch session B

        // --- PHẦN SỬA LỖI 1: Phải connect lại cho User C ---
        Assert.assertEquals("Kết nối lại cho User C thất bại", 0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userC = NativeClient.loginUser(emailC, pass)!!
        val idC = userC.id
        NativeClient.disconnect() // Ngắt kết nối để làm sạch session C

        // 3. Login User A (Người tạo)
        // --- PHẦN SỬA LỖI 2: Đảm bảo kết nối lại cho User A ---
        Assert.assertEquals("Kết nối lại cho User A thất bại", 0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val groupName = "Konnichiwa Group"
        val members = intArrayOf(idB, idC)

        val latch = CountDownLatch(1)
        var capturedGroupId = -1
        var capturedGroupName = ""

        val listener = object : StubNativeEventListener() {
            override fun onGroupCreated(groupId: Int, groupName: String) {
                capturedGroupId = groupId
                capturedGroupName = groupName
                latch.countDown()
            }
        }
        NativeClient.startListening(listener)

        // 4. Thực hiện tạo nhóm
        NativeClient.createGroup(groupName, members)

        // 5. Verify
        val success = latch.await(5, TimeUnit.SECONDS)
        Assert.assertTrue("Timeout: Không nhận được CMD_CREATE_GROUP_RESP", success)
        Assert.assertTrue("Group ID phải > 0", capturedGroupId > 0)
        Assert.assertEquals("Tên nhóm trả về không khớp", groupName, capturedGroupName)
    }

    @Test
    fun test14_b_CreateGroup_Persistence() {
        // Kịch bản:
        // 1. A tạo nhóm.
        // 2. Ngay sau đó A lấy lịch sử chat của nhóm này.
        // 3. Phải thấy dòng "đã tạo nhóm" nằm trong lịch sử.

        val time = System.currentTimeMillis()
        val emailA = "cre_persist_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        // 1. Tạo nhóm
        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })

        NativeClient.createGroup("Persistence Group", intArrayOf()) // Nhóm 1 mình cũng được
        Assert.assertTrue(latchCreate.await(5, TimeUnit.SECONDS))

        // 2. Lấy lịch sử nhóm ngay lập tức
        val latchHist = CountDownLatch(1)
        var msgType = -1
        var msgContent = ""

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                if (messages.isNotEmpty()) {
                    // Lấy tin đầu tiên (mới nhất)
                    msgType = messages[0].type
                    msgContent = messages[0].content
                    latchHist.countDown()
                }
            }
        })

        // Gọi getChatHistory cho Group (isGroup = true)
        NativeClient.getChatHistory(groupId, true, 0, 10)

        // 3. Verify
        Assert.assertTrue("Timeout lấy History sau khi tạo nhóm", latchHist.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Tin nhắn đầu tiên phải là System Message", MSG_TYPE_SYSTEM, msgType)
        Assert.assertTrue("Nội dung phải chứa 'tạo nhóm'", msgContent.contains("tạo nhóm"))
    }

    @Test
    fun test15_CreateGroup_Broadcast_Realtime() {
        // Kịch bản: B tạo nhóm có A. A đang Online phải nhận được:
        // 1. Thông báo có nhóm mới (onGroupCreated)
        // 2. Tin nhắn hệ thống "đã tạo nhóm" (onMessageReceived)

        val time = System.currentTimeMillis()
        val emailA = "onlineA_$time@konni.com"
        val emailB = "creatorB_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // 1. Login A (Native) và chờ đợi
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val userA = NativeClient.loginUser(emailA, pass)!!
        val idA = userA.id

        val latchNotify = CountDownLatch(1)
        val latchMsg = CountDownLatch(1) // <--- MỚI: Chờ tin nhắn

        var notifyGroupName = ""
        var sysMsgContent = ""
        var sysMsgType = -1

        val listener = object : StubNativeEventListener() {
            // 1. Bắt sự kiện tạo nhóm
            override fun onGroupCreated(groupId: Int, groupName: String) {
                println("TEST: A nhận được broadcast nhóm mới: $groupName")
                notifyGroupName = groupName
                latchNotify.countDown()
            }

            // 2. Bắt tin nhắn hệ thống (MỚI)
            override fun onMessageReceived(msg: MessageDto) {
                println("TEST: A nhận được tin nhắn: ${msg.content}, type: ${msg.type}")
                // Server gửi msgType 9 cho system
                if (msg.type == MSG_TYPE_SYSTEM) {
                    sysMsgContent = msg.content
                    sysMsgType = msg.type
                    latchMsg.countDown()
                }
            }
        }
        NativeClient.startListening(listener)

        // 2. FakeClient B tạo nhóm có chứa A
        val groupName = "Realtime Broadcast Group"
        val threadB = Thread {
            try {
                val fb = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fb.login(emailB, pass)
                Thread.sleep(500)
                // Tạo nhóm chứa ID của A
                fb.createGroup(groupName, intArrayOf(idA))
                Thread.sleep(500)
                fb.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadB.start()

        // 3. Verify
        Assert.assertTrue("User A không nhận được Broadcast tạo nhóm", latchNotify.await(7, TimeUnit.SECONDS))
        Assert.assertEquals(groupName, notifyGroupName)

        // Verify System Message (MỚI)
        Assert.assertTrue("User A không nhận được System Message 'đã tạo nhóm'", latchMsg.await(7, TimeUnit.SECONDS))
        Assert.assertEquals(MSG_TYPE_SYSTEM, sysMsgType)
        Assert.assertTrue(sysMsgContent.contains("tạo nhóm"))
    }

    @Test
    fun test16_CreateGroup_EmptyName_Error() {
        // Kịch bản: Tên nhóm rỗng. Server phải trả về STATUS_ERROR_INVALID_PARAM
        val time = System.currentTimeMillis()
        val email = "fail_$time@konni.com"
        NativeClient.registerUser("FailUser", email, "123")

        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(email, "123")

        val latch = CountDownLatch(1)
        var errorStatus = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_CREATE_GROUP_RESP) {
                    errorStatus = status
                    latch.countDown()
                }
            }
        })

        // Gửi tên nhóm rỗng
        NativeClient.createGroup("", intArrayOf(1, 2, 3))

        latch.await(5, TimeUnit.SECONDS)
        // Dựa trên logic Server, nếu name empty -> trả về INVALID_PARAM
        Assert.assertEquals("Phải trả về lỗi INVALID_PARAM", STATUS_ERR_INVALID_PARAM, errorStatus)
    }

    // ==========================================
    // MODULE 9: ADD MEMBERS TO GROUP
    // ==========================================

    @Test
    fun test17_AddMembers_Success_Logic() {
        val time = System.currentTimeMillis()
        val ownerEmail = "owner_add_$time@konni.com"
        val mem1Email = "mem1_$time@konni.com"
        val newbieEmail = "newbie_$time@konni.com"
        val pass = "123"

        // 1. Đăng ký 3 user
        NativeClient.registerUser("Owner", ownerEmail, pass)
        NativeClient.registerUser("Member1", mem1Email, pass)
        NativeClient.registerUser("Newbie", newbieEmail, pass)

        // 2. Lấy ID của Member1 và Newbie
        val idMem1 = helperGetUserId(mem1Email, pass)
        val idNewbie = helperGetUserId(newbieEmail, pass)

        // 3. Owner tạo nhóm với Member1
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(ownerEmail, pass)

        // Tạo nhóm
        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Add Member Test Group", intArrayOf(idMem1))
        Assert.assertTrue(latchCreate.await(5, TimeUnit.SECONDS))
        Assert.assertTrue(groupId > 0)

        // 4. Owner thêm Newbie vào nhóm
        val latchAdd = CountDownLatch(1)
        var addStatus = -1

        // Thay đổi listener để bắt phản hồi add member
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_ADD_MEMBER_RESP) {
                    addStatus = status
                    latchAdd.countDown()
                }
            }
        })

        NativeClient.addMembersToGroup(groupId, intArrayOf(idNewbie))

        // 5. Verify
        Assert.assertTrue("Timeout chờ phản hồi Add Member", latchAdd.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Thêm thành viên phải trả về SUCCESS", STATUS_SUCCESS, addStatus)
    }

    @Test
    fun test17_b_AddMember_Persistence() {
        // Kịch bản:
        // 1. A tạo nhóm.
        // 2. A thêm B vào nhóm.
        // 3. A lấy lịch sử chat -> Phải thấy dòng "đã thêm B".

        val time = System.currentTimeMillis()
        val emailA = "A_persist_$time@konni.com"
        val emailB = "B_added_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)
        val idB = helperGetUserId(emailB, pass)

        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        // 1. Tạo nhóm
        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Add Persist Group", intArrayOf())
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. Thêm B
        val latchAdd = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_ADD_MEMBER_RESP && status == STATUS_SUCCESS) {
                    latchAdd.countDown()
                }
            }
        })
        NativeClient.addMembersToGroup(groupId, intArrayOf(idB))
        latchAdd.await(5, TimeUnit.SECONDS)

        // 3. Lấy lịch sử
        val latchHist = CountDownLatch(1)
        var msgContent = ""
        var msgType = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                if (messages.isNotEmpty()) {
                    // Tin mới nhất (index 0)
                    msgContent = messages[0].content
                    msgType = messages[0].type
                    latchHist.countDown()
                }
            }
        })

        // Gọi getHistory cho Group
        NativeClient.getChatHistory(groupId, true, 0, 10)

        // 4. Verify
        Assert.assertTrue("Timeout lấy History", latchHist.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(MSG_TYPE_SYSTEM, msgType)
        Assert.assertTrue("Nội dung phải chứa 'đã thêm'", msgContent.contains("đã thêm"))
    }

    @Test
    fun test18_AddMembers_Realtime_Notification() {
        // Kịch bản: A thêm C vào nhóm. B (Online) nhận được:
        // 1. Notify Members Added (update list)
        // 2. System Message (update chat timeline)

        val time = System.currentTimeMillis()
        val emailA = "A_add_$time@konni.com"
        val emailB = "B_listen_$time@konni.com"
        val emailC = "C_new_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)
        NativeClient.registerUser("UserC", emailC, pass)

        val idA = helperGetUserId(emailA, pass)
        val idC = helperGetUserId(emailC, pass)

        // 1. B tạo nhóm có A
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailB, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Group Broadcast Add", intArrayOf(idA))
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. B lắng nghe sự kiện
        val latchNotify = CountDownLatch(1)
        val latchMsg = CountDownLatch(1) // <--- MỚI

        var notifyAddedBy = ""
        var sysMsgContent = ""
        var sysMsgType = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            // Sự kiện 1: Update List
            override fun onGroupMembersAdded(gid: Int, addedBy: String, newMemberIds: IntArray) {
                if (gid == groupId) {
                    notifyAddedBy = addedBy
                    latchNotify.countDown()
                }
            }
            // Sự kiện 2: Chat Message
            override fun onMessageReceived(msg: MessageDto) {
                if (msg.receiverId == groupId && msg.type == MSG_TYPE_SYSTEM) {
                    sysMsgContent = msg.content
                    sysMsgType = msg.type
                    latchMsg.countDown()
                }
            }
        })

        // 3. Fake Client A login và thêm C
        val threadA = Thread {
            try {
                val fakeA = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeA.login(emailA, pass)
                Thread.sleep(500)
                fakeA.addMembersToGroup(groupId, intArrayOf(idC))
                Thread.sleep(500)
                fakeA.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadA.start()

        // 4. Verify
        Assert.assertTrue("B không nhận được Notify Member Added", latchNotify.await(8, TimeUnit.SECONDS))
        Assert.assertTrue("Người thêm phải là UserA", notifyAddedBy.contains("UserA"))

        // Verify System Message
        Assert.assertTrue("B không nhận được System Message", latchMsg.await(8, TimeUnit.SECONDS))
        Assert.assertEquals(MSG_TYPE_SYSTEM, sysMsgType)
        Assert.assertTrue(sysMsgContent.contains("đã thêm"))
    }

    @Test
    fun test19_AddMembers_Security_NotMember() {
        // Kịch bản: D (người lạ) cố tình thêm E vào nhóm của A&B.
        // Server phải chặn và trả về lỗi Auth.

        val time = System.currentTimeMillis()
        val emailA = "owner_secure_$time@konni.com"
        val emailD = "hacker_$time@konni.com"
        val emailE = "victim_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("HackerD", emailD, pass)
        NativeClient.registerUser("VictimE", emailE, pass)

        val idE = helperGetUserId(emailE, pass)

        // 1. A tạo nhóm (chỉ có một mình)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Private Group", intArrayOf()) // Nhóm không member
        latchCreate.await(5, TimeUnit.SECONDS)
        NativeClient.disconnect()
        Thread.sleep(200)

        // 2. D (Hacker) Login
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailD, pass)

        val latchFail = CountDownLatch(1)
        var failStatus = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_ADD_MEMBER_RESP) {
                    failStatus = status
                    latchFail.countDown()
                }
            }
        })

        // 3. D cố thêm E vào nhóm của A
        NativeClient.addMembersToGroup(groupId, intArrayOf(idE))

        // 4. Verify
        latchFail.await(5, TimeUnit.SECONDS)
        Assert.assertEquals("Người lạ thêm thành viên phải bị lỗi AUTH", STATUS_ERR_AUTH, failStatus)
    }

    // ==========================================
    // MODULE 10: LEAVE GROUP & SYSTEM MESSAGES
    // ==========================================

    @Test
    fun test20_LeaveGroup_Realtime_SystemMsg() {
        // Kịch bản:
        // 1. A tạo nhóm với B.
        // 2. B Login (NativeClient) và lắng nghe.
        // 3. A (FakeClient) gửi lệnh Rời nhóm.
        // 4. B nhận được:
        //    - Callback onMemberLeft (để update list member)
        //    - Callback onMessageReceived (để hiện timeline "A đã rời nhóm")

        val time = System.currentTimeMillis()
        val emailA = "leaver_$time@konni.com"
        val emailB = "stayer_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        val idA = helperGetUserId(emailA, pass)
        val idB = helperGetUserId(emailB, pass)

        // 1. B tạo nhóm có A (Để tiện lấy GroupID khi B đang login)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailB, pass)

        val latchGroup = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchGroup.countDown()
            }
        })
        NativeClient.createGroup("System Msg Test Group", intArrayOf(idA))
        latchGroup.await(5, TimeUnit.SECONDS)

        // 2. B lắng nghe sự kiện Rời nhóm và Tin nhắn mới
        val latchLeft = CountDownLatch(1)
        val latchMsg = CountDownLatch(1)

        var leftMemberId = -1
        var sysMsgContent = ""
        var sysMsgType = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            // Sự kiện 1: Cập nhật danh sách thành viên
            override fun onMemberLeft(gid: Int, memberId: Int, memberName: String) {
                if (gid == groupId && memberId == idA) {
                    leftMemberId = memberId
                    latchLeft.countDown()
                }
            }
            // Sự kiện 2: Tin nhắn hệ thống hiện lên khung chat
            override fun onMessageReceived(msg: MessageDto) {
                // Kiểm tra đúng Group và đúng Type System
                if (msg.receiverId == groupId && msg.type == MSG_TYPE_SYSTEM) {
                    sysMsgContent = msg.content
                    sysMsgType = msg.type
                    latchMsg.countDown()
                }
            }
        })

        // 3. Fake Client A login và Rời nhóm
        val threadA = Thread {
            try {
                val fakeA = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeA.login(emailA, pass)
                Thread.sleep(500)
                fakeA.leaveGroup(groupId)
                Thread.sleep(500)
                fakeA.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadA.start()

        // 4. Verify
        Assert.assertTrue("Timeout chờ sự kiện onMemberLeft", latchLeft.await(8, TimeUnit.SECONDS))
        Assert.assertEquals("ID người rời phải là A", idA, leftMemberId)

        Assert.assertTrue("Timeout chờ Tin nhắn hệ thống", latchMsg.await(8, TimeUnit.SECONDS))
        Assert.assertEquals("MsgType phải là SYSTEM (9)", MSG_TYPE_SYSTEM, sysMsgType)
        Assert.assertTrue("Nội dung tin nhắn phải chứa 'rời nhóm'", sysMsgContent.contains("rời nhóm"))

        println("TEST PASSED: Nhận được tin nhắn hệ thống: '$sysMsgContent'")
    }

    @Test
    fun test21_LeaveGroup_History_Persistence() {
        // Kịch bản:
        // 1. A và B trong nhóm.
        // 2. B Offline.
        // 3. A rời nhóm.
        // 4. B Online -> Get History -> Phải thấy dòng "A đã rời nhóm".

        val time = System.currentTimeMillis()
        val emailA = "gone_$time@konni.com"
        val emailB = "offline_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)

        // --- SỬA LỖI TẠI ĐÂY: Lấy ID của B trước ---
        val idB = helperGetUserId(emailB, pass)

        // 1. A tạo nhóm với B
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })

        // Truyền idB đã lấy từ trước vào
        NativeClient.createGroup("History Test Group", intArrayOf(idB))
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. A rời nhóm (NativeClient đang là A)
        val latchLeave = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_LEAVE_GROUP_RESP && status == STATUS_SUCCESS) {
                    latchLeave.countDown()
                }
            }
        })
        NativeClient.leaveGroup(groupId)
        Assert.assertTrue(latchLeave.await(5, TimeUnit.SECONDS))
        NativeClient.disconnect() // A out
        Thread.sleep(500)

        // 3. B Login và lấy History
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailB, pass)

        val latchHist = CountDownLatch(1)
        var lastMsgType = -1
        var lastMsgContent = ""

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                if (messages.isNotEmpty()) {
                    // Tin mới nhất nằm đầu tiên (index 0)
                    lastMsgType = messages[0].type
                    lastMsgContent = messages[0].content
                    latchHist.countDown()
                }
            }
        })

        NativeClient.getChatHistory(groupId, true, 0, 10)

        // 4. Verify
        Assert.assertTrue("Timeout lấy History", latchHist.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Tin mới nhất trong lịch sử phải là System Msg", MSG_TYPE_SYSTEM, lastMsgType)
        Assert.assertTrue("Nội dung phải chứa 'rời nhóm'", lastMsgContent.contains("rời nhóm"))
    }

    @Test
    fun test22_Rejoin_Group_Flow() {
        // Kịch bản:
        // 1. A tạo nhóm với B.
        // 2. B rời nhóm.
        // 3. A thêm lại B vào nhóm.
        // 4. Kiểm tra xem DB có cho phép không và B có nhận được thông báo vào nhóm lại không.

        val time = System.currentTimeMillis()
        val emailA = "admin_$time@konni.com"
        val emailB = "return_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("UserA", emailA, pass)
        NativeClient.registerUser("UserB", emailB, pass)
        val idB = helperGetUserId(emailB, pass)

        // 1. A login tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Rejoin Group", intArrayOf(idB))
        latchCreate.await(5, TimeUnit.SECONDS)
        NativeClient.disconnect()
        Thread.sleep(200)

        // 2. FakeClient B vào và Rời nhóm
        val threadB = Thread {
            val fb = FakeTcpClient(SERVER_IP, SERVER_PORT)
            fb.login(emailB, pass)
            Thread.sleep(200)
            fb.leaveGroup(groupId)
            Thread.sleep(200)
            fb.close()
        }
        threadB.start()
        threadB.join() // Chờ B rời xong

        // 3. A Login lại và thêm B vào lại
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchAdd = CountDownLatch(1)
        var addStatus = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_ADD_MEMBER_RESP) { // CMD 33
                    addStatus = status
                    latchAdd.countDown()
                }
            }
        })

        // Gọi lệnh thêm thành viên (Server sẽ chạy ON DUPLICATE KEY UPDATE status='active')
        NativeClient.addMembersToGroup(groupId, intArrayOf(idB))

        // 4. Verify
        Assert.assertTrue("Timeout chờ Add Member", latchAdd.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Thêm lại người đã rời phải thành công (SUCCESS)", STATUS_SUCCESS, addStatus)
    }

    // ==========================================
    // MODULE 11: GET GROUP LIST
    // ==========================================

    @Test
    fun test25_GetGroupList_BasicFlow() {
        val time = System.currentTimeMillis()
        val email = "list_$time@konni.com"
        val pass = "123"

        // 1. Register & Login
        NativeClient.registerUser("ListUser", email, pass)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val user = NativeClient.loginUser(email, pass)!!

        // 2. Tạo 2 nhóm
        // Helper tạo nhóm nhanh (sử dụng lại listener createGroup)
        val createLatch = CountDownLatch(2)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(groupId: Int, groupName: String) {
                createLatch.countDown()
            }
        })

        NativeClient.createGroup("Group Alpha", intArrayOf())
        Thread.sleep(100) // Sleep để timestamp khác nhau
        NativeClient.createGroup("Group Beta", intArrayOf())

        Assert.assertTrue("Timeout creating groups", createLatch.await(5, TimeUnit.SECONDS))

        // 3. Lấy danh sách nhóm
        val listLatch = CountDownLatch(1)
        var receivedGroups: Array<GroupDto>? = null

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupListReceived(groups: Array<GroupDto>) {
                receivedGroups = groups
                listLatch.countDown()
            }
        })

        NativeClient.getGroupList(0, 10)

        // 4. Verify
        Assert.assertTrue("Timeout waiting for group list", listLatch.await(5, TimeUnit.SECONDS))
        Assert.assertNotNull(receivedGroups)
        Assert.assertEquals("Phải nhận được 2 nhóm", 2, receivedGroups!!.size)

        // Kiểm tra thứ tự: Mới tạo (Beta) lên đầu, Cũ (Alpha) xuống dưới
        Assert.assertEquals("Group Beta", receivedGroups!![0].name)
        Assert.assertEquals("Group Alpha", receivedGroups!![1].name)
    }

    @Test
    fun test26_GetGroupList_Pagination() {
        val time = System.currentTimeMillis()
        val email = "pager_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("PagerUser", email, pass)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(email, pass)

        // 1. Tạo 5 nhóm: G1, G2, G3, G4, G5
        val createLatch = CountDownLatch(5)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                createLatch.countDown()
            }
        })

        for (i in 1..5) {
            NativeClient.createGroup("G$i", intArrayOf())
            Thread.sleep(50)
        }
        Assert.assertTrue(createLatch.await(8, TimeUnit.SECONDS))

        // 2. Test Page 1: Limit 3 -> Mong đợi: G5, G4, G3 (Mới nhất lên đầu)
        val latchPage1 = CountDownLatch(1)
        var page1: Array<GroupDto>? = null

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupListReceived(groups: Array<GroupDto>) {
                page1 = groups
                latchPage1.countDown()
            }
        })
        NativeClient.getGroupList(0, 3)
        Assert.assertTrue(latchPage1.await(5, TimeUnit.SECONDS))

        Assert.assertEquals(3, page1!!.size)
        Assert.assertEquals("G5", page1!![0].name)
        Assert.assertEquals("G3", page1!![2].name)

        // 3. Test Page 2: Offset 3, Limit 3 -> Mong đợi: G2, G1 (Chỉ còn 2)
        val latchPage2 = CountDownLatch(1)
        var page2: Array<GroupDto>? = null

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupListReceived(groups: Array<GroupDto>) {
                page2 = groups
                latchPage2.countDown()
            }
        })
        NativeClient.getGroupList(3, 3)
        Assert.assertTrue(latchPage2.await(5, TimeUnit.SECONDS))

        Assert.assertEquals(2, page2!!.size)
        Assert.assertEquals("G2", page2!![0].name)
        Assert.assertEquals("G1", page2!![1].name)
    }

    @Test
    fun test27_GetGroupList_Exclude_LeftGroups() {
        // Kịch bản: Tạo nhóm -> Kiểm tra có -> Rời nhóm -> Kiểm tra mất
        val time = System.currentTimeMillis()
        val email = "leaver_list_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("LeaverList", email, pass)
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(email, pass)

        // 1. Tạo nhóm
        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Temp Group", intArrayOf())
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. Verify có trong list
        val latchCheck1 = CountDownLatch(1)
        var count1 = 0
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupListReceived(groups: Array<GroupDto>) {
                count1 = groups.size
                latchCheck1.countDown()
            }
        })
        NativeClient.getGroupList(0, 10)
        latchCheck1.await(5, TimeUnit.SECONDS)
        Assert.assertEquals("Ban đầu phải có 1 nhóm", 1, count1)

        // 3. Rời nhóm
        val latchLeave = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_LEAVE_GROUP_RESP && status == STATUS_SUCCESS) latchLeave.countDown()
            }
        })
        NativeClient.leaveGroup(groupId)
        latchLeave.await(5, TimeUnit.SECONDS)

        // 4. Verify list rỗng
        val latchCheck2 = CountDownLatch(1)
        var count2 = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupListReceived(groups: Array<GroupDto>) {
                count2 = groups.size
                latchCheck2.countDown()
            }
        })
        NativeClient.getGroupList(0, 10)
        latchCheck2.await(5, TimeUnit.SECONDS)

        Assert.assertEquals("Rời nhóm xong thì list phải rỗng", 0, count2)
    }

    // ==========================================
    // MODULE 12: KICK MEMBER (REMOVE)
    // ==========================================

    @Test
    fun test28_KickMember_Success_Realtime() {
        // Kịch bản:
        // 1. Admin tạo nhóm có Victim và Observer.
        // 2. Observer (Online) lắng nghe.
        // 3. Admin thực hiện Kick Victim.
        // 4. Observer phải nhận được:
        //    - onMemberRemoved (để update list)
        //    - onMessageReceived (msgType=9: "Admin đã mời Victim...")

        val time = System.currentTimeMillis()
        val emailAdmin = "admin_k_$time@konni.com"
        val emailVictim = "victim_$time@konni.com"
        val emailObs = "observer_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("Admin", emailAdmin, pass)
        NativeClient.registerUser("Victim", emailVictim, pass)
        NativeClient.registerUser("Observer", emailObs, pass)

        val idVictim = helperGetUserId(emailVictim, pass)
        val idObs = helperGetUserId(emailObs, pass)

        // 1. Admin Login và Tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailAdmin, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Kick Test Group", intArrayOf(idVictim, idObs))
        latchCreate.await(5, TimeUnit.SECONDS)

        NativeClient.disconnect() // Admin out để Observer login (giả lập 2 máy)
        Thread.sleep(500)

        // 2. Observer Login và Lắng nghe
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailObs, pass)

        val latchNotify = CountDownLatch(1)
        val latchMsg = CountDownLatch(1)

        var removedId = -1
        var sysMsgContent = ""

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMemberRemoved(gid: Int, memberId: Int, memberName: String, adminId: Int, adminName: String) {
                if (gid == groupId && memberId == idVictim) {
                    removedId = memberId
                    latchNotify.countDown()
                }
            }
            override fun onMessageReceived(msg: MessageDto) {
                if (msg.receiverId == groupId && msg.type == MSG_TYPE_SYSTEM) {
                    sysMsgContent = msg.content
                    latchMsg.countDown()
                }
            }
        })

        // 3. Admin (Dùng FakeClient để gửi lệnh Kick từ thread khác mà không cần Observer logout)
        val threadAdmin = Thread {
            try {
                val fakeAdmin = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeAdmin.login(emailAdmin, pass)
                Thread.sleep(500)
                fakeAdmin.kickMember(groupId, idVictim)
                Thread.sleep(500)
                fakeAdmin.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadAdmin.start()

        // 4. Verify Observer nhận được thông tin
        Assert.assertTrue("Observer không nhận được Notify Member Removed", latchNotify.await(8, TimeUnit.SECONDS))
        Assert.assertEquals("ID người bị kick không đúng", idVictim, removedId)

        Assert.assertTrue("Observer không nhận được System Message", latchMsg.await(8, TimeUnit.SECONDS))
        Assert.assertTrue("Nội dung tin nhắn sai", sysMsgContent.contains("mời Victim ra khỏi nhóm"))
    }

    @Test
    fun test29_KickMember_Failure_Permissions() {
        val time = System.currentTimeMillis()
        val emailAdmin = "boss_$time@konni.com"
        val emailHacker = "hacker_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("Boss", emailAdmin, pass)
        NativeClient.registerUser("Hacker", emailHacker, pass)

        // --- SỬA LỖI: Lấy ID trước ---
        val idHacker = helperGetUserId(emailHacker, pass)
        val idAdmin = helperGetUserId(emailAdmin, pass)

        // 1. Admin tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailAdmin, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })

        // Truyền idHacker đã lấy
        NativeClient.createGroup("Secure Group", intArrayOf(idHacker))
        latchCreate.await(5, TimeUnit.SECONDS)

        NativeClient.disconnect()
        Thread.sleep(200)

        // 2. Hacker Login
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailHacker, pass)

        val latchFail = CountDownLatch(1)
        var failStatus = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_REMOVE_MEMBER_RESP) {
                    failStatus = status
                    latchFail.countDown()
                }
            }
        })

        // 3. Hacker cố kick Admin
        NativeClient.kickMember(groupId, idAdmin)

        // 4. Verify
        // Lưu ý: Hacker gửi lệnh lên, Server sẽ trả về CMD_REMOVE_MEMBER_RESP kèm status lỗi.
        // Nên latchFail sẽ đếm xuống. Dòng AssertTrue ở đây là đúng logic.
        Assert.assertTrue("Không nhận được phản hồi Kick", latchFail.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Member thường kick Admin phải trả về lỗi AUTH", STATUS_ERROR_NOT_GROUP_ADMIN, failStatus)
    }

    @Test
    fun test30_KickMember_Failure_SelfKick() {
        // Kịch bản: Admin tự kick chính mình -> Phải lỗi INVALID_PARAM
        val time = System.currentTimeMillis()
        val email = "selfie_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("Selfie", email, pass)
        val idSelf = helperGetUserId(email, pass)

        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(email, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Self Kick Group", intArrayOf())
        latchCreate.await(5, TimeUnit.SECONDS)

        val latchFail = CountDownLatch(1)
        var status = -1

        // Reset listener
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, statusIn: Int) {
                if (cmd == CMD_REMOVE_MEMBER_RESP) {
                    status = statusIn
                    latchFail.countDown()
                }
            }
        })

        // Admin tự kick mình
        NativeClient.kickMember(groupId, idSelf)

        Assert.assertTrue(latchFail.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Tự kick mình phải trả về INVALID_PARAM", STATUS_ERROR_CANNOT_REMOVE_SELF, status)
    }

    @Test
    fun test31_KickMember_Persistence() {
        // Kịch bản: Admin kick B. Sau đó Admin lấy lại lịch sử chat -> Phải thấy dòng thông báo.
        val time = System.currentTimeMillis()
        val emailA = "admin_p_$time@konni.com"
        val emailB = "target_p_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("AdminP", emailA, pass)
        NativeClient.registerUser("TargetP", emailB, pass)
        val idB = helperGetUserId(emailB, pass)

        // 1. Login A tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailA, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Persist Kick Group", intArrayOf(idB))
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. Kick B
        val latchKick = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_REMOVE_MEMBER_RESP && status == STATUS_SUCCESS) latchKick.countDown()
            }
        })
        NativeClient.kickMember(groupId, idB)
        latchKick.await(5, TimeUnit.SECONDS)

        // 3. Lấy lịch sử
        val latchHist = CountDownLatch(1)
        var msgContent = ""
        var msgType = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                if (messages.isNotEmpty()) {
                    // Tin mới nhất (index 0)
                    msgContent = messages[0].content
                    msgType = messages[0].type
                    latchHist.countDown()
                }
            }
        })

        NativeClient.getChatHistory(groupId, true, 0, 10)

        // 4. Verify
        Assert.assertTrue("Timeout lấy History", latchHist.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Type phải là System (9)", MSG_TYPE_SYSTEM, msgType)
        Assert.assertTrue("Content phải chứa 'đã mời'", msgContent.contains("đã mời"))
    }

    // ==========================================
    // MODULE 13: DISSOLVE GROUP (DELETE)
    // ==========================================

    @Test
    fun test32_DissolveGroup_Success_Realtime() {
        // Kịch bản:
        // 1. Admin tạo nhóm có Member.
        // 2. Member (Online) lắng nghe sự kiện.
        // 3. Admin giải tán nhóm.
        // 4. Member nhận được onGroupDissolved.

        val time = System.currentTimeMillis()
        val emailAdmin = "boss_d_$time@konni.com"
        val emailMem = "mem_d_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("BossD", emailAdmin, pass)
        NativeClient.registerUser("MemD", emailMem, pass)
        val idMem = helperGetUserId(emailMem, pass)

        // 1. Admin tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailAdmin, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Dissolve Test Group", intArrayOf(idMem))
        latchCreate.await(5, TimeUnit.SECONDS)

        NativeClient.disconnect() // Admin out
        Thread.sleep(200)

        // 2. Member Login lắng nghe
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailMem, pass)

        val latchDissolve = CountDownLatch(1)
        var dissolvedGid = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupDissolved(gid: Int) {
                if (gid == groupId) {
                    dissolvedGid = gid
                    latchDissolve.countDown()
                }
            }
        })

        // 3. Admin (FakeClient) gửi lệnh Giải tán
        val threadAdmin = Thread {
            try {
                val fakeAdmin = FakeTcpClient(SERVER_IP, SERVER_PORT)
                fakeAdmin.login(emailAdmin, pass)
                Thread.sleep(300)
                fakeAdmin.dissolveGroup(groupId)
                Thread.sleep(300)
                fakeAdmin.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
        threadAdmin.start()

        // 4. Verify
        Assert.assertTrue("Member không nhận được thông báo giải tán", latchDissolve.await(8, TimeUnit.SECONDS))
        Assert.assertEquals("ID nhóm giải tán không khớp", groupId, dissolvedGid)
    }

    @Test
    fun test33_DissolveGroup_Failure_Permission() {
        // Kịch bản: Member thường cố tình giải tán nhóm -> Lỗi Auth
        val time = System.currentTimeMillis()
        val emailAdmin = "admin_sec_$time@konni.com"
        val emailHacker = "hacker_d_$time@konni.com"
        val pass = "123"

        NativeClient.registerUser("AdminSec", emailAdmin, pass)
        NativeClient.registerUser("HackerD", emailHacker, pass)

        val idHacker = helperGetUserId(emailHacker, pass)

        // 1. Admin tạo nhóm
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailAdmin, pass)

        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Secure Group D", intArrayOf(idHacker))
        latchCreate.await(5, TimeUnit.SECONDS)
        NativeClient.disconnect()
        Thread.sleep(200)

        // 2. Hacker Login
        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(emailHacker, pass)

        val latchFail = CountDownLatch(1)
        var failStatus = -1

        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_DISSOLVE_GROUP_RESP) { // 309
                    failStatus = status
                    latchFail.countDown()
                }
            }
        })

        // 3. Hacker gửi lệnh giải tán
        NativeClient.dissolveGroup(groupId)

        // 4. Verify
        Assert.assertTrue("Không nhận được phản hồi Dissolve", latchFail.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("Member thường giải tán nhóm phải bị lỗi AUTH", STATUS_ERROR_NOT_GROUP_ADMIN, failStatus)
    }

    @Test
    fun test34_DissolveGroup_Failure_InvalidGroup() {
        // Kịch bản: Admin giải tán một nhóm không tồn tại (ID = 999999) -> Lỗi Auth hoặc DB
        // (Server sẽ check role trong DB, không thấy -> return -1 -> Auth Error)

        val time = System.currentTimeMillis()
        val email = "fail_d_$time@konni.com"
        val pass = "123"
        NativeClient.registerUser("FailD", email, pass)

        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        NativeClient.loginUser(email, pass)

        val latchFail = CountDownLatch(1)
        var status = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, s: Int) {
                if (cmd == CMD_DISSOLVE_GROUP_RESP) {
                    status = s
                    latchFail.countDown()
                }
            }
        })

        NativeClient.dissolveGroup(999999) // ID fake

        Assert.assertTrue(latchFail.await(5, TimeUnit.SECONDS))
        Assert.assertNotEquals("Không được trả về SUCCESS", STATUS_SUCCESS, status)
    }

    @Test
    fun test35_DissolveGroup_DataCleanup() {
        // Kịch bản quan trọng:
        // 1. Tạo nhóm, gửi tin nhắn vào nhóm.
        // 2. Giải tán nhóm.
        // 3. Cố gắng lấy lịch sử chat của nhóm đó -> Phải trả về rỗng (0 tin).
        // (Điều này chứng minh DB đã xóa sạch tin nhắn của nhóm).

        val time = System.currentTimeMillis()
        val email = "clean_$time@konni.com"
        val pass = "123"
        NativeClient.registerUser("Cleaner", email, pass)

        Assert.assertEquals(0, NativeClient.connect(SERVER_IP, SERVER_PORT))
        val user = NativeClient.loginUser(email, pass)!!
        val myId = user.id

        // 1. Tạo nhóm
        val latchCreate = CountDownLatch(1)
        var groupId = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onGroupCreated(gid: Int, name: String) {
                groupId = gid
                latchCreate.countDown()
            }
        })
        NativeClient.createGroup("Cleanup Group", intArrayOf())
        latchCreate.await(5, TimeUnit.SECONDS)

        // 2. Gửi tin nhắn (để tạo rác trong DB)
        val latchMsg = CountDownLatch(2)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onMessageSent(tempId: Int, serverId: Int, ts: Long) {
                latchMsg.countDown()
            }
        })
        NativeClient.sendMessage(myId, groupId, "Msg 1 to delete", 1, "group")
        NativeClient.sendMessage(myId, groupId, "Msg 2 to delete", 2, "group")
        latchMsg.await(5, TimeUnit.SECONDS)

        // 3. Giải tán nhóm
        val latchDissolve = CountDownLatch(1)
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onRequestResponse(cmd: Int, status: Int) {
                if (cmd == CMD_DISSOLVE_GROUP_RESP && status == STATUS_SUCCESS) latchDissolve.countDown()
            }
        })
        NativeClient.dissolveGroup(groupId)
        latchDissolve.await(5, TimeUnit.SECONDS)

        // 4. Kiểm tra sạch sẽ: Get History của nhóm vừa xóa
        // Mong đợi: Server trả về 0 tin nhắn (vì đã bị DELETE CASCADE hoặc DELETE thủ công)
        val latchHist = CountDownLatch(1)
        var msgCount = -1
        NativeClient.startListening(object : StubNativeEventListener() {
            override fun onHistoryReceived(messages: Array<MessageDto>) {
                msgCount = messages.size
                latchHist.countDown()
            }
        })

        NativeClient.getChatHistory(groupId, true, 0, 10) // isGroup=true
        latchHist.await(5, TimeUnit.SECONDS)

        Assert.assertEquals("Tin nhắn nhóm phải bị xóa sạch sau khi giải tán", 0, msgCount)
    }

    // --- Helper Utility để lấy nhanh ID User ---
    private fun helperGetUserId(email: String, pass: String): Int {
        NativeClient.disconnect()
        Thread.sleep(100)
        NativeClient.connect(SERVER_IP, SERVER_PORT)
        val user = NativeClient.loginUser(email, pass)
        val id = user!!.id
        NativeClient.disconnect()
        Thread.sleep(100)
        return id
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
    override fun onPendingRequestsReceived(requests: Array<PendingRequestDto>) {}
    override fun onFriendRequestAccepted(user: UserDto) {}
    override fun onFriendRemoved(exFriendId: Int) {}
    override fun onSearchResult(results: Array<UserSearchDto>) {}
    override fun onMessageSent(tempId: Int, serverId: Int, serverTime: Long) {}
    override fun onMessageReceived(msg: MessageDto) {}
    override fun onMessageDelivered(serverId: Int) {}
    override fun onHistoryReceived(messages: Array<MessageDto>) {}

    override fun onConnectionClosed(reason: String) {}
    override fun onGroupCreated(groupId: Int, groupName: String) {}
    override fun onGroupMembersAdded(
        groupId: Int,
        addedBy: String,
        newMemberIds: IntArray
    ) {}

    override fun onMemberLeft(
        groupId: Int,
        memberId: Int,
        memberName: String
    ) {}

    override fun onGroupListReceived(groups: Array<GroupDto>) {}
    override fun onMemberRemoved(
        groupId: Int,
        memberId: Int,
        memberName: String,
        adminId: Int,
        adminName: String
    ) {}

    override fun onGroupDissolved(groupId: Int) {}
}

/**
 * FakeTcpClient
 * Giả lập Client gửi gói tin Binary thô khớp hoàn toàn với struct C trong protocol.h
 */
class FakeTcpClient(ip: String, port: Int) {
    private val socket = Socket(ip, port)
    private val output = DataOutputStream(socket.getOutputStream())

    // Protocol Constants
    private val SERVER_PROTOCOL_VERSION = 1
    private val PACKET_HEADER_SIZE = 28 // 4*5 + 8

    private val CMD_LOGIN = 12

    private val CMD_SEND_MESSAGE = 20
    private val CMD_DISSOLVE_GROUP = 38
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

    fun createGroup(name: String, memberIds: IntArray) {
        // Payload size = name[100] + count[4] + members[N*4]
        val payloadSize = 100 + 4 + (memberIds.size * 4)
        val totalSize = 28 + payloadSize

        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. Header
        writeHeader(buffer, 30, payloadSize) // CMD_CREATE_GROUP = 30

        // 2. Body
        // Group Name (100 bytes)
        writeFixedString(buffer, name, 100)

        // Member Count (4 bytes)
        buffer.putInt(memberIds.size)

        // Member IDs (N * 4 bytes)
        for (id in memberIds) {
            buffer.putInt(id)
        }

        output.write(buffer.array())
        output.flush()
    }

    fun addMembersToGroup(groupId: Int, memberIds: IntArray) {
        // Struct AddGroupMemberPayload:
        // int32 group_id;          (4)
        // int32 count;             (4)
        // int32 added_by_user;     (4) - Client gửi lên để 0
        // char added_by_name[64];  (64) - Client gửi lên để trống
        // --------------------------------
        // Total Fixed Size = 76 bytes

        val fixedPayloadSize = 76
        val arraySize = memberIds.size * 4
        val totalPayloadSize = fixedPayloadSize + arraySize
        val totalPacketSize = PACKET_HEADER_SIZE + totalPayloadSize

        val buffer = ByteBuffer.allocate(totalPacketSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. Header
        writeHeader(buffer, 32, totalPayloadSize) // CMD_ADD_MEMBER = 32

        // 2. Fixed Payload
        buffer.putInt(groupId)
        buffer.putInt(memberIds.size)
        buffer.putInt(0) // added_by_user (Ignored)
        writeFixedString(buffer, "", 64) // added_by_name (Ignored)

        // 3. Array IDs
        for (id in memberIds) {
            buffer.putInt(id)
        }

        output.write(buffer.array())
        output.flush()
    }

    fun leaveGroup(groupId: Int) {
        // Struct: group_id (4 bytes)
        val payloadSize = 4
        val totalSize = PACKET_HEADER_SIZE + payloadSize
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // CMD_LEAVE_GROUP = 36
        writeHeader(buffer, 36, payloadSize)
        buffer.putInt(groupId)

        output.write(buffer.array())
        output.flush()
    }

    fun kickMember(groupId: Int, targetId: Int) {
        // RemoveMemberReqPayload: group_id (4) + target_id (4) = 8 bytes
        val payloadSize = 8
        val totalSize = 28 + payloadSize // Header + Payload
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // CMD_REMOVE_MEMBER = 34
        writeHeader(buffer, 34, payloadSize)

        buffer.putInt(groupId)
        buffer.putInt(targetId)

        output.write(buffer.array())
        output.flush()
    }

    fun dissolveGroup(groupId: Int) {
        // Payload: group_id (4 bytes)
        val payloadSize = 4
        val totalSize = 28 + payloadSize
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        writeHeader(buffer, CMD_DISSOLVE_GROUP, payloadSize)
        buffer.putInt(groupId)

        output.write(buffer.array())
        output.flush()
    }

    private fun writeHeader(buffer: ByteBuffer, cmd: Int, payloadSize: Int) {
        // Struct PacketHeader: version, cmd, size, reqId, status, timestamp
        buffer.putInt(SERVER_PROTOCOL_VERSION)
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
