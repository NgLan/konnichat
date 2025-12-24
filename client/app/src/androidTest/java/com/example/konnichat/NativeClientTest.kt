package com.example.konnichat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.konnichat.data.remote.NativeClient
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeClientTest {

    private val SERVER_IP = "10.0.2.2" // IP đặc biệt để máy ảo Android gọi về localhost của PC
    private val SERVER_PORT = 8080

    @Before
    fun setup() {
        // Khởi tạo kết nối trước mỗi bài test
        val result = NativeClient.connect(SERVER_IP, SERVER_PORT)
        Assert.assertEquals("Kết nối Server thất bại!", 0, result)
    }

    @After
    fun tearDown() {
        // Đóng kết nối sau khi test xong
        NativeClient.disconnect()
    }

    @Test
    fun testRegistrationAndLoginFlow() {
        val testEmail = "dev_${System.currentTimeMillis()}@test.com"
        val testPass = "123456"

        // 1. Test Register
        val regStatus = NativeClient.registerUser("Developer", testEmail, testPass)
        Assert.assertEquals("Đăng ký phải thành công (0)", 0, regStatus)

        // 2. Test Login thành công
        val user = NativeClient.loginUser(testEmail, testPass)
        Assert.assertNotNull("UserDto không được null", user)
        Assert.assertEquals("Email login phải khớp", testEmail, user?.email)

        // 3. Test Login sai mật khẩu (Mong đợi ném AuthenticationException)
        try {
            NativeClient.loginUser(testEmail, "wrong_password")
            Assert.fail("Lẽ ra phải ném AuthenticationException")
        } catch (e: Exception) {
            Assert.assertTrue(
                "Phải là lỗi Auth",
                e.message?.contains("Sai email hoặc mật khẩu") == true
            )
        }
    }
}