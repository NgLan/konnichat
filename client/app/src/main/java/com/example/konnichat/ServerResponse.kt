package com.example.konnichat

// Class này dùng để hứng dữ liệu từ C trả về
data class ServerResponse(
    val cmd: Int,       // Command Type (Ví dụ: 99 là Response)
    val status: Int,    // Status Code (0 là Success)
    val data: String    // Nội dung text (nếu có) để in log
)
