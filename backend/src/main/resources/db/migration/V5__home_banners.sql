-- =====================================================================================
-- Ảnh trang chủ do quản trị viên quản lý (FR-43, UC34 — mở lại Q4, supervisor chốt 2026-10-17).
-- Phạm vi tối thiểu: số ô cố định theo bố cục HomePage (không phải danh sách banner tự do — không lịch chạy, không link,
-- không đếm lượt bấm). Hero chỉ đổi ảnh: tiêu đề, ô tìm kiếm giữ nguyên theo thiết kế.
--
--   HERO         nền khối hero (chữ + ô tìm kiếm đè lên — frontend phủ một lớp tối phẳng để giữ tương phản)
--   SIGNUP_BAND  ảnh trong băng coral mời đăng ký
--   COMMUNITY    ảnh khối "Thành viên làm được gì"
--
-- Chưa có dòng / is_active = FALSE → trang chủ dùng hình minh hoạ phẳng như trước. id số để ghi activity_log (FR-42,
-- target_id BIGINT); slot là khóa nghiệp vụ.
-- =====================================================================================

CREATE TABLE home_banners (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    slot        VARCHAR(20)     NOT NULL,
    -- Gốc khóa ảnh trên kho: banners/{uuid} — 3 bản {key}_thumb/_medium/_large.jpg (PhotoSize), đã bỏ EXIF
    storage_key VARCHAR(200)    NOT NULL,
    -- Rỗng = ảnh trang trí (chữ bên cạnh đã mang nội dung)
    alt_text    VARCHAR(200)    NOT NULL DEFAULT '',
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_by  BIGINT UNSIGNED NOT NULL,
    version     INT             NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_home_banners_slot UNIQUE (slot),
    CONSTRAINT fk_home_banners_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_home_banners_slot CHECK (slot IN ('HERO', 'SIGNUP_BAND', 'COMMUNITY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Permission mới, chỉ cho ADMIN (chỉ thêm — không đổi quyền nào đã có)
INSERT INTO permissions (name, description) VALUES ('banner:manage', 'Quản lý ảnh trang chủ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'banner:manage'
WHERE r.name = 'ADMIN';
