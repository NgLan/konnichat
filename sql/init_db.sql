DROP DATABASE IF EXISTS konnichat;
CREATE DATABASE konnichat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE konnichat;

-- 1. Bảng USERS
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) CHARACTER SET utf8mb4 NOT NULL,
    age INT,
    -- Status: 'active', 'banned'
    status VARCHAR(20) DEFAULT 'active',
    -- IsOnline: TRUE: 'online', FALSE: 'offline'
    is_online BOOLEAN DEFAULT FALSE, 
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Bảng FRIEND_REQUESTS (Lời mời kết bạn)
CREATE TABLE friend_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    -- Status: 'waiting', 'approved', 'denied'
    status VARCHAR(20) DEFAULT 'waiting',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES Users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_request (sender_id, receiver_id)
);

-- 3. Bảng FRIENDS (Bạn bè chính thức)
CREATE TABLE friends (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES Users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_friendship (user_id, friend_id)
);

-- 4. Bảng MESSAGES (Chat 1-1)
CREATE TABLE messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL, -- Lưu UserID (nếu private) hoặc GroupID (nếu group)
    chat_type VARCHAR(10) NOT NULL, -- Phân loại: 'private' (Chat đơn), 'group' (Chat nhóm)
    content TEXT CHARACTER SET utf8mb4 NOT NULL,
    status VARCHAR(20) DEFAULT 'sent', -- Status: 'sent', 'delivered', 'read', 'revoked', 'deleted'
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (sender_id) REFERENCES Users(id) ON DELETE CASCADE
);
ALTER TABLE messages ADD COLUMN msg_type INT DEFAULT 1 AFTER chat_type; -- 1: Text, 2: Image, 3: File,...
CREATE INDEX idx_msg_receiver_type ON messages(receiver_id, chat_type);

-- 5. Bảng GROUPS (Nhóm)
CREATE TABLE `groups` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) CHARACTER SET utf8mb4 NOT NULL,
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Bảng GROUP_MEMBERS
CREATE TABLE group_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NOT NULL,
    member_id INT NOT NULL,
    -- Status: 'active', 'left', 'kicked'
    status VARCHAR(20) DEFAULT 'active',
    -- Role: 'admin', 'member'
    role VARCHAR(20) DEFAULT 'member',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES `Groups`(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES Users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_group_member (group_id, member_id)
);

-- INSERT DỮ LIỆU MẪU
-- 1. Tạo 10 User
-- Tất cả mật khẩu là Pass@1234
INSERT INTO Users (email, password, name, is_online, avatar_url) VALUES 
('an.nguyen@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Nguyễn Văn An', 1, 'default_avt'),
('bich.tran@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Trần Thị Bích', 0, 'default_avt'),
('cuong.le@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Lê Hùng Cường', 1, 'default_avt'),
('dung.pham@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Phạm Thùy Dung', 1, 'default_avt'),
('em.hoang@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Hoàng Văn Em', 0, 'default_avt'),
('phuong.vu@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Vũ Lan Phương', 1, 'default_avt'),
('giang.dang@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Đặng Trường Giang', 0, 'default_avt'),
('hoa.bui@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Bùi Thị Hoa', 0, 'default_avt'),
('khanh.do@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Đỗ Duy Khánh', 1, 'default_avt'),
('lan.nguyen@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Nguyễn Ngọc Lan', 0, 'default_avt'),
('linh.ngo@test.com', '1d95a4c6d681ede5b18c89b21ceb46bfea7b8e4d8f824107615a2ee297493710', 'Ngô Mỹ Linh', 1, 'default_avt');

-- 2. Kết bạn
-- (Câu lệnh này tự động lấy ID của các email vừa tạo để add vào bảng Friends)
INSERT INTO Friends (user_id, friend_id)
SELECT 1, id FROM Users 
WHERE email IN (
    'bich.tran@test.com', 
    'dung.pham@test.com', 'em.hoang@test.com', 'phuong.vu@test.com', 
    'giang.dang@test.com', 'hoa.bui@test.com', 'khanh.do@test.com', 
    'linh.ngo@test.com', 'lan.nguyen@test.com'
);

INSERT INTO Friends (user_id, friend_id)
SELECT 2, id FROM Users 
WHERE email IN (
    'cuong.le@test.com', 
    'dung.pham@test.com', 'em.hoang@test.com', 'phuong.vu@test.com', 
    'giang.dang@test.com'
);

INSERT INTO Friends (user_id, friend_id)
SELECT 10, id FROM Users 
WHERE email IN (
    'an.nguyen@test.com', 'cuong.le@test.com', 'khanh.do@test.com', 
    'linh.ngo@test.com'
);
