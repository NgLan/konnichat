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
 * @brief Rời khỏi nhóm.
 * 
 * @param group_id ID nhóm.
 * @param user_id ID người rời nhóm.
 * @return int 1 nếu thành công, 0 nếu thất bại.
 */
int db_leave_group(int32_t group_id, int32_t user_id);

/**
 * @brief Thêm nhiều thành viên vào nhóm.
 * 
 * @param group_id ID nhóm.
 * @param user_ids Mảng ID người dùng cần thêm.
 * @param count Số lượng người.
 * @return int Số lượng người thực tế thêm được (>=0), hoặc -1 nếu lỗi DB, -2 nếu nhóm chat full người.
 */
int db_add_group_members(int32_t group_id, const int32_t* user_ids, int count);

/**
 * @brief Lấy danh sách nhóm mà user đang tham gia (status = 'active')
 * 
 * @param user_id ID người dùng
 * @param groups_out Pointer đến mảng struct để hứng dữ liệu
 * @param limit Số lượng tối đa
 * @param offset Vị trí bắt đầu
 * @return int Số lượng nhóm lấy được
 */
int db_get_joined_groups(int user_id, GroupInfoPayload* groups_out, int limit, int offset);

/**
 * @brief Lấy vai trò của user trong nhóm.
 * @return Chuỗi role ("admin", "member") hoặc NULL nếu không trong nhóm.
 * Caller phải free chuỗi trả về.
 */
char* db_get_member_role(int32_t group_id, int32_t user_id);

/**
 * @brief Chuyển trạng thái thành viên thành 'kicked'.
 * @return 1 nếu thành công, 0 nếu thất bại.
 */
int db_kick_member(int32_t group_id, int32_t target_id);

/**
 * @brief Giải tán nhóm (Xóa sạch Group, Member và Message).
 * Chỉ Admin mới làm được.
 * @return 
 *   1: Thành công
 *   0: Lỗi DB
 *  -1: Không phải Admin hoặc nhóm không tồn tại
 */
int db_dissolve_group(int32_t group_id, int32_t requester_id);

/**
 * @brief Lấy tên nhóm theo ID.
 * @param group_name Buffer để chứa kết quả (size tối thiểu MAX_GROUP_NAME).
 * @return 1 nếu tìm thấy, 0 nếu không thấy.
 */
int db_get_group_name(int32_t group_id, char* group_name);

/**
 * @brief Lấy danh sách thành viên của một nhóm (kèm thông tin user).
 * Chỉ lấy những người có status = 'active'.
 * 
 * @param members_out Mảng output
 * @return int Số lượng thành viên lấy được
 */
int db_get_group_members_info(int group_id, GroupMemberInfo* members_out, int limit, int offset);

#endif // GROUP_REPO_H
