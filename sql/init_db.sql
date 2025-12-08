DROP DATABASE IF EXISTS konnichat;
CREATE DATABASE konnichat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE konnichat;

-- 1. Bảng USERS
CREATE TABLE Users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) CHARACTER SET utf8mb4 NOT NULL,
    age INT,
    -- Status: 'active', 'banned'
    status VARCHAR(20) DEFAULT 'active',
    -- IsOnline: 'online', 'offline'
    is_online VARCHAR(10) DEFAULT 'offline', 
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Bảng FRIEND_REQUESTS (Lời mời kết bạn)
CREATE TABLE FriendRequests (
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
CREATE TABLE Friends (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    -- Notification: 'on', 'off'
    notification VARCHAR(10) DEFAULT 'on',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES Users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_friendship (user_id, friend_id)
);

-- 4. Bảng MESSAGES (Chat 1-1)
CREATE TABLE Messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    content TEXT CHARACTER SET utf8mb4,
    -- Status: 'sent', 'delivered', 'read', 'revoked', 'deleted'
    status VARCHAR(20) DEFAULT 'sent',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES Users(id) ON DELETE CASCADE
);

-- 5. Bảng GROUPS (Nhóm)
CREATE TABLE `Groups` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) CHARACTER SET utf8mb4 NOT NULL,
    avatar_url TEXT,
    -- Notification: 'on', 'off'
    notification VARCHAR(10) DEFAULT 'on',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Bảng GROUP_MEMBERS
CREATE TABLE GroupMembers (
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

-- 7. Bảng GROUP_MESSAGES
CREATE TABLE GroupMessages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NOT NULL,
    sender_id INT NOT NULL,
    content TEXT CHARACTER SET utf8mb4,
    -- Status: 'sent', 'delivered', 'revoked', 'deleted'
    status VARCHAR(20) DEFAULT 'sent',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES `Groups`(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES Users(id) ON DELETE CASCADE
);

CREATE TABLE Icons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    -- icon: (ví dụ: 'like', 'love', 'haha')
    icon VARCHAR(50) NOT NULL UNIQUE, 
    -- image_url: Đường dẫn ảnh hoặc tên file resource trong Android (ví dụ: 'ic_emoji_like')
    image_url VARCHAR(255), 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE MessageReactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL, -- Người thả reaction
    icon_id INT NOT NULL, -- Loại icon 
    
    -- Link tới tin nhắn 1-1 (Có thể NULL nếu đây là reaction nhóm)
    message_id INT DEFAULT NULL,
    
    -- Link tới tin nhắn Nhóm (Có thể NULL nếu đây là reaction 1-1)
    group_message_id INT DEFAULT NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Khóa ngoại
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (icon_id) REFERENCES Icons(id) ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES Messages(id) ON DELETE CASCADE,
    FOREIGN KEY (group_message_id) REFERENCES GroupMessages(id) ON DELETE CASCADE,

    -- Ràng buộc logic: Một dòng chỉ được phép thuộc về 1 loại tin nhắn
    -- (Hoặc là message_id có dữ liệu, hoặc là group_message_id có dữ liệu, không được cả 2 cùng có)
    CONSTRAINT check_reaction_target CHECK (
        (message_id IS NOT NULL AND group_message_id IS NULL) OR 
        (message_id IS NULL AND group_message_id IS NOT NULL)
    ),

    -- Ràng buộc logic: Một người chỉ được thả 1 reaction cho 1 tin nhắn
    -- (Nếu thả lại sẽ update dòng cũ chứ không tạo dòng mới)
    UNIQUE KEY unique_user_p2p_react (user_id, message_id),
    UNIQUE KEY unique_user_group_react (user_id, group_message_id)
);

-- INSERT DỮ LIỆU MẪU
-- 1. Tạo 10 User
INSERT INTO Users (email, password, name, is_online, avatar_url) VALUES 
('an.nguyen@test.com', 'Pass@1234', 'Nguyễn Văn An', 'online', 'default_avt'),
('bich.tran@test.com', 'Pass@1234', 'Trần Thị Bích', 'offline', 'default_avt'),
('cuong.le@test.com', 'Pass@1234', 'Lê Hùng Cường', 'online', 'default_avt'),
('dung.pham@test.com', 'Pass@1234', 'Phạm Thùy Dung', 'online', 'default_avt'),
('em.hoang@test.com', 'Pass@1234', 'Hoàng Văn Em', 'offline', 'default_avt'),
('phuong.vu@test.com', 'Pass@1234', 'Vũ Lan Phương', 'online', 'default_avt'),
('giang.dang@test.com', 'Pass@1234', 'Đặng Trường Giang', 'offline', 'default_avt'),
('hoa.bui@test.com', 'Pass@1234', 'Bùi Thị Hoa', 'offline', 'default_avt'),
('khanh.do@test.com', 'Pass@1234', 'Đỗ Duy Khánh', 'online', 'default_avt'),
('lan.nguyen@test.com', 'Pass@1234', 'Nguyễn Ngọc Lan', 'offline', 'default_avt'),
('linh.ngo@test.com', 'Pass@1234', 'Ngô Mỹ Linh', 'online', 'default_avt');

-- 2. Kết bạn
-- (Câu lệnh này tự động lấy ID của các email vừa tạo để add vào bảng Friends)
INSERT INTO Friends (user_id, friend_id)
SELECT 1, id FROM Users 
WHERE email IN (
    'bich.tran@test.com', 'cuong.le@test.com', 
    'dung.pham@test.com', 'em.hoang@test.com', 'phuong.vu@test.com', 
    'giang.dang@test.com', 'hoa.bui@test.com', 'khanh.do@test.com', 
    'linh.ngo@test.com', 'lan.nguyen@test.com'
);

INSERT INTO Friends (user_id, friend_id)
SELECT 2, id FROM Users 
WHERE email IN (
    'an.nguyen@test.com', 'cuong.le@test.com', 
    'dung.pham@test.com', 'em.hoang@test.com', 'phuong.vu@test.com', 
    'giang.dang@test.com'
);

-- Thêm bộ icon cơ bản
-- INSERT INTO Icons (code, image_url) VALUES 
-- ('like', 'ic_reaction_like'),
-- ('love', 'ic_reaction_love'),
-- ('haha', 'ic_reaction_haha'),
-- ('sad', 'ic_reaction_sad'),
-- ('angry', 'ic_reaction_angry'); 

-- -- Giả sử User 1 thả tim (love - ID 2) vào tin nhắn số 1 (Chat 1-1)
-- INSERT INTO MessageReactions (user_id, icon_id, message_id) VALUES (1, 2, 1);

-- -- Giả sử User 2 thả haha (haha - ID 3) vào tin nhắn nhóm số 5
-- INSERT INTO MessageReactions (user_id, icon_id, group_message_id) VALUES (2, 3, 5);
