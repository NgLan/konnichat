# KonniChat - Ứng dụng Chat Real-time (Client-Server)

**KonniChat** là đồ án môn học **Lập trình mạng**, xây dựng hệ thống nhắn tin tức thời theo mô hình Client-Server.
- **Server:** Viết bằng C (Linux), sử dụng Socket TCP, Đa luồng (Pthread) và MySQL.
- **Client:** Ứng dụng Android (Kotlin + Native C/JNI).

---

## 🛠 Yêu cầu hệ thống 

### 1. Phía Server (Linux/WSL)
*   **OS:** Ubuntu 20.04/22.04 (hoặc WSL2 trên Windows).
*   **Compiler:** GCC (`build-essential`).
*   **Build Tool:** Make.
*   **Database:** MySQL Server & Thư viện Client C (`libmysqlclient-dev`).

### 2. Phía Client (Android)
*   **IDE:** Android Studio.
*   **SDK:** Android SDK (Min SDK 24).
*   **NDK:** Android Native Development Kit (bắt buộc để biên dịch code C).
*   **Emulator:** Cần tạo ít nhất **2 thiết bị ảo** (AVD) để test chat qua lại.

---

## 🚀 Hướng dẫn Cài đặt & Chạy Server

### Bước 1: Cài đặt thư viện cần thiết
Mở Terminal (Ubuntu/WSL) và chạy lệnh:
```bash
sudo apt-get update
sudo apt-get install build-essential cmake libmysqlclient-dev mysql-server
```

### Bước 2: Thiết lập Cơ sở dữ liệu
1.  Đăng nhập vào MySQL
2.  Chạy script khởi tạo (File nằm tại `sql/init_db.sql`). Script này sẽ tạo DB, bảng và **dữ liệu mẫu**

### Bước 3: Cấu hình môi trường (.env)
Tại thư mục `server/`, tạo file `.env` từ file mẫu:
```bash
cd server
cp .env.example .env
```
Mở file `.env` và chỉnh sửa thông tin kết nối Database cho khớp với máy bạn:
```env
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=your_database_user
DB_PASS=your_password
DB_NAME=konnichat
```

### Bước 4: Biên dịch và Chạy Server
Tại thư mục `server/`, chạy lệnh:
```bash
make clean
make
./myserver
```
**Thành công:** Màn hình hiện thông báo: `>>> TCP Server started on port 8080`.

---

## 📱 Hướng dẫn Cài đặt & Chạy Client (Android)

### Bước 1: Chuẩn bị Máy ảo (Emulator)
1.  Vào **Device Manager** trong Android Studio.
2.  Tạo 3 máy ảo khác nhau (Ví dụ: Pixel 6 và Pixel 4).
3.  Khởi động cả 3 máy ảo cùng lúc.

### Bước 2: Build và Cài đặt
1.  Nhấn nút **Run** (Tam giác xanh) trên Android Studio.
2.  Chọn cài đặt app lên **Cả 3 máy ảo**.

---

## 🧪 Hướng dẫn Test theo Use Case (Kịch bản kiểm thử)

Sử dụng dữ liệu mẫu có sẵn trong `init_db.sql` để test nhanh.
**Mật khẩu chung cho tất cả tài khoản:** `Pass@1234`

### Danh sách tài khoản mẫu:
1.  `an.nguyen@test.com` (Tên: Nguyễn Văn An)
2.  `bich.tran@test.com` (Tên: Trần Thị Bích)
3.  `cuong.le@test.com` (Tên: Lê Hùng Cường)
4.  `dung.pham@test.com` (Tên: Phạm Thùy Dung)

---

### Kịch bản 1: Đăng nhập & Kết nối (Real-time)
*   **Máy 1:** Đăng nhập bằng `an.nguyen@test.com` / `Pass@1234`.
*   **Máy 2:** Đăng nhập bằng `bich.tran@test.com` / `Pass@1234`.
*   **Máy 3:** Đăng nhập bằng `cuong.le@test.com` / `Pass@1234`.
*   **Kết quả:** Cả 3 máy vào màn hình chính. Trên danh sách bạn bè, trạng thái của người kia phải hiện chấm xanh (**Online**).

### Kịch bản 2: Chat Cá nhân (1-1)
*   **Máy 1 (An):** Chọn "Trần Thị Bích" trong danh sách bạn bè -> Nhắn "Hello Bích".
*   **Máy 2 (Bích):**
    *   Nếu đang mở đoạn chat với An: Tin nhắn hiện ngay lập tức.
    *   Nếu đang ở màn hình ngoài: Hiện thông báo (Notification).
*   **Máy 2 (Bích):** Nhắn lại "Chào An". Máy 1 nhận được ngay.

### Kịch bản 3: Gửi lời mời kết bạn
*   **Máy 1:** Vào tab Tìm kiếm -> Gõ "Cuong" -> Chọn "Lê Hùng Cường" -> Bấm "Kết bạn".
*   **Máy 3 (Cường):**
    *   Vào tab "Thông báo".
    *   Thấy lời mời từ Nguyễn Văn An.
    *   Bấm "Chấp nhận".
*   **Kết quả:** Cường và An xuất hiện trong danh sách bạn bè của nhau.

### Kịch bản 4: Chat Nhóm (Group Chat)
*   **Máy 1 (An):** Bấm dấu `+` -> "Tạo nhóm" -> Chọn Bích và Cường -> Đặt tên "Hội Bàn Tròn" -> Tạo.
*   **Kết quả:**
    *   Máy 1 chuyển vào màn hình chat nhóm.
    *   Máy 2 (Bích) và Máy 3 (Cường) nhận được thông báo được thêm vào nhóm.
*   **Test Chat:** Máy 1 nhắn "Alo mọi người". Tất cả các máy khác trong nhóm đều nhận được tin nhắn cùng lúc.

### Kịch bản 5: Tính năng Offline (Store-and-forward)
1.  **Máy 2 (Bích):** Tắt Wifi/Mạng trên Emulator hoặc Đăng xuất.
2.  **Máy 1 (An):** Nhắn 3 tin cho Bích.
3.  **Máy 2 (Bích):** Bật lại mạng và Đăng nhập lại.
4.  **Kết quả:** 3 tin nhắn An gửi lúc nãy sẽ tự động tải về và hiển thị đầy đủ.

### Kịch bản 6: Thu hồi tin nhắn
1.  **Máy 1:** Nhắn một tin sai cho máy 2.
2.  **Máy 1:** Nhấn giữ tin nhắn đó -> Chọn "Thu hồi".
3.  **Kết quả:** Nội dung tin nhắn trên cả Máy 1 và Máy 2 đều đổi thành *"Tin nhắn đã bị thu hồi"*.

---

## 📂 Cấu trúc Thư mục

```
konnichat/
├── client/                 # Source code Android
│   ├── app/src/main/cpp/   # Code C Native (Network Layer)
│   └── app/src/main/java/  # Code Kotlin (UI Layer)
├── server/                 # Source code Server C
│   ├── src/                # Mã nguồn (.c)
│   ├── include/            # Header files (.h)
│   └── Makefile            # Script biên dịch
└── sql/                    # Script khởi tạo Database
```

## ⚠️ Khắc phục sự cố thường gặp (Troubleshooting)

1.  **Lỗi "Connection Failed" trên Android:**
    *   Kiểm tra xem Server đã chạy chưa (`./server`).

2.  **Lỗi biên dịch Server thiếu thư viện:**
    *   Đảm bảo đã cài `libmysqlclient-dev`.

3.  **Lỗi không nhận tin nhắn:**
    *   Kiểm tra Logcat trong Android Studio (Filter: `KONNI_NATIVE`) để xem log nhận gói tin từ C hoặc kiểm tra file `server.log` trong thư mục server.
