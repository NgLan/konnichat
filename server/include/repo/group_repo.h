#ifndef GROUP_REPO_H
#define GROUP_REPO_H

#include <stdint.h>
#include "../../include/protocol.h"

/**
 * @brief Tạo một nhóm chat mới và thêm các thành viên ban đầu.
 * 
 * Hàm này thực hiện trong một Database Transaction:
 * 1. Thêm bản ghi vào bảng `groups`.
 * 2. Thêm người tạo (creator) vào bảng `group_members` với vai trò 'admin'.
 * 3. Thêm danh sách bạn bè được chọn vào bảng `group_members` với vai trò 'member'.
 * 
 * @param name Tên nhóm chat.
 * @param creator_id ID của người thực hiện tạo nhóm.
 * @param member_ids Mảng chứa ID của các thành viên được mời.
 * @param member_count Số lượng thành viên trong mảng member_ids.
 * @return int ID của nhóm vừa tạo (>0) nếu thành công, -1 nếu thất bại.
 */
int db_create_group(const char* name, int32_t creator_id, const int32_t* member_ids, int member_count);

/**
 * @brief Lấy danh sách ID của tất cả thành viên trong một nhóm.
 * 
 * Dùng để Server biết cần broadcast tin nhắn/thông báo đến những socket nào.
 * 
 * @param group_id ID của nhóm cần truy vấn.
 * @param out_member_ids Mảng để chứa kết quả trả về.
 * @param max_count Kích thước tối đa của mảng out_member_ids.
 * @return int Số lượng thành viên thực tế tìm thấy, -1 nếu có lỗi.
 */
int db_get_group_member_ids(int32_t group_id, int32_t* out_member_ids, int max_count);

/**
 * @brief Kiểm tra một User có phải là thành viên của nhóm hay không.
 * 
 * Dùng để phân quyền trước khi cho phép gửi tin nhắn vào nhóm hoặc thực hiện các hành động nhóm.
 * 
 * @param group_id ID nhóm.
 * @param user_id ID người dùng.
 * @return int 1 nếu là thành viên, 0 nếu không phải, -1 nếu lỗi DB.
 */
int db_is_group_member(int32_t group_id, int32_t user_id);

/**
 * @brief Thêm thành viên mới vào nhóm đã tồn tại.
 * 
 * @param group_id ID nhóm.
 * @param target_user_id ID người được thêm.
 * @return int 1 nếu thành công, 0 nếu thất bại.
 */
int db_add_group_member(int32_t group_id, int32_t target_user_id);

/**
 * @brief Xóa (hoặc rời) khỏi nhóm.
 * 
 * @param group_id ID nhóm.
 * @param user_id ID người rời nhóm.
 * @return int 1 nếu thành công, 0 nếu thất bại.
 */
int db_leave_group(int32_t group_id, int32_t user_id);

#endif // GROUP_REPO_H
