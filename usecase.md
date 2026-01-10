```mermaid
graph LR
    %% --- ACTORS ---
    U(("User<br>(Người dùng)"))
    A(("Admin<br>(Quản trị viên)"))

    %% Quan hệ thừa kế
    A -.-|> U

    %% --- SYSTEM ---
    %% Thay đổi: Bỏ 'subgraph ID [...]', chỉ dùng 'subgraph "Tên"'
    subgraph "Hệ thống Chat Client-Server"
        direction TB
        
        subgraph "Nhóm: Tài khoản"
            UC1(Đăng ký)
            UC2(Đăng nhập)
            UC3(Đăng xuất)
        end

        subgraph "Nhóm: Bạn bè"
            UC4(Gửi lời mời KB)
            UC5(Phản hồi KB)
            UC6(Hủy kết bạn)
            UC7(Xem DS bạn bè)
        end

        subgraph "Nhóm: Chat"
            UC8(Chat 1-1)
            UC9(Chat nhóm)
            UC10(Thu hồi tin nhắn)
            UC11(React tin nhắn)
            UC12(Bật/Tắt thông báo)
        end

        subgraph "Nhóm: Quản lý Group"
            UC13(Tạo nhóm)
            UC14(Thêm thành viên)
            UC15(Rời nhóm)
            UC16(Xóa thành viên)
            UC17(Giải tán nhóm)
        end
    end

    %% --- CONNECTIONS ---
    U --> UC1
    U --> UC2
    U --> UC3
    
    U --> UC4
    U --> UC5
    U --> UC6
    U --> UC7

    U --> UC8
    U --> UC9
    U --> UC10
    U --> UC11
    U --> UC12

    U --> UC13
    U --> UC14
    U --> UC15

    %% Admin
    A ==> UC16
    A ==> UC17
    
    %% Style đơn giản
    classDef plain fill:#fff,stroke:#333,stroke-width:1px;
    class UC1,UC2,UC3,UC4,UC5,UC6,UC7,UC8,UC9,UC10,UC11,UC12,UC13,UC14,UC15,UC16,UC17 plain;
```