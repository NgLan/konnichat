package com.example.konnichat.core.exception

// [QUAN TRỌNG] Dòng này phải có đóng ngoặc đàng hoàng thì bên dưới mới hiểu
open class NativeException(message: String, val code: Int) : Exception(message)

// 1. Lỗi xác thực
// [SỬA ĐỔI]: Đổi input từ Int -> message: String để khớp với lệnh ThrowNew bên C
class AuthenticationException(message: String) :
    NativeException(message, 401)

// 2. Lỗi không tìm thấy người dùng
// [SỬA ĐỔI]: Đổi input từ Int -> message: String
class UserNotFoundException(message: String) :
    NativeException(message, 404)

// 3. Lỗi tài khoản đã tồn tại
// [SỬA ĐỔI]: Đổi input từ Int -> message: String
class UserAlreadyExistsException(message: String) :
    NativeException(message, 409)

// 4. Lỗi Server
// [SỬA ĐỔI]: Đổi input từ Int -> message: String
class ServerInternalException(message: String) :
    NativeException(message, 500)

// -----------------------------------------------------------------------------
// CÁC CLASS DƯỚI ĐÂY GIỮ NGUYÊN
// -----------------------------------------------------------------------------

// 5. Lỗi tham số
class InvalidParameterException(code: Int) : NativeException("Dữ liệu gửi đi không hợp lệ", code)

// 6. Lỗi mạng
class NetworkException(message: String) : NativeException(message, -1)

// 7. Lỗi giao thức
class ProtocolException(message: String) : NativeException(message, -2)