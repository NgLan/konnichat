package com.example.konnichat.core.exception

// Exception gốc cho lỗi từ Native
open class NativeException(message: String, val code: Int) : Exception(message)

// 1. Lỗi xác thực (Sai pass, sai email)
class AuthenticationException(code: Int) :
    NativeException("Lỗi xác thực: Sai email hoặc mật khẩu", code)

// 2. Lỗi không tìm thấy người dùng
class UserNotFoundException(code: Int) : NativeException("Tài khoản không tồn tại", code)

// 3. Lỗi tài khoản đã tồn tại (Khi đăng ký)
class UserAlreadyExistsException(code: Int) : NativeException("Email đã được sử dụng", code)

// 4. Lỗi Server/DB (Server crash, DB full)
class ServerInternalException(code: Int) : NativeException("Lỗi nội bộ Server", code)

// 5. Lỗi tham số (Gửi thiếu field, format sai)
class InvalidParameterException(code: Int) : NativeException("Dữ liệu gửi đi không hợp lệ", code)

// 6. Lỗi mạng (Socket closed, timeout)
class NetworkException(message: String) : NativeException(message, -1)

// 7. Lỗi giao thức (Server trả về command lạ)
class ProtocolException(message: String) : NativeException(message, -2)
