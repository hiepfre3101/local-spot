-- =====================================================================================
-- LocalSpot — schema khởi tạo (32 bảng).
-- Nguồn: docs/design/database.md (đã chốt 2026-09-28). Sửa schema = thêm migration mới,
-- KHÔNG sửa file này sau khi đã áp dụng (Flyway checksum — CLAUDE.md §3).
--
-- Quy ước:
--   * InnoDB, utf8mb4 / utf8mb4_0900_ai_ci; thời gian DATETIME(6) lưu UTC; không DEFAULT thời gian
--     phía CSDL (CURRENT_TIMESTAMP phụ thuộc time_zone của session) — ứng dụng (JPA Auditing) điền.
--   * Tên ràng buộc: fk_<bảng>_<cột>, uk_<bảng>_<cột>, ix_<bảng>_<cột>, ck_<bảng>_<luật>.
--   * Khóa ngoại mặc định RESTRICT: bảng nghiệp vụ dùng xóa mềm nên không có xóa cứng hợp lệ;
--     RESTRICT chặn mất dữ liệu ngoài ý muốn. Chỉ bảng nối thuần (không mang dữ liệu nghiệp vụ
--     riêng) dùng ON DELETE CASCADE ở phía bị xóa cứng được (role, tiện ích, bộ sưu tập, địa điểm).
--   * Trạng thái: VARCHAR(20) + CHECK, map @Enumerated(STRING).
-- =====================================================================================

-- ---------------------------------------------------------------- 1. Tài khoản & phân quyền

CREATE TABLE users (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email               VARCHAR(191)    NOT NULL,
    password_hash       VARCHAR(100)    NOT NULL,
    display_name        VARCHAR(100)    NOT NULL,
    avatar_url          VARCHAR(500)    NULL,
    bio                 VARCHAR(500)    NULL,
    email_verified_at   DATETIME(6)     NULL,
    helpful_votes_count INT UNSIGNED    NOT NULL DEFAULT 0,
    contribution_points INT UNSIGNED    NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)     NULL,
    lock_reason         VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    deleted_at          DATETIME(6)     NULL,
    PRIMARY KEY (id),
    -- Tài khoản xóa được ẩn danh hóa email (D7) nên UNIQUE không chặn đăng ký lại
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(50)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY ix_role_permissions_permission_id (permission_id),
    CONSTRAINT fk_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY ix_user_roles_role_id (role_id),
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- S1: refresh token xoay vòng, lưu SHA-256; dùng lại token cũ → thu hồi cả family (U2)
CREATE TABLE refresh_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    family_id  CHAR(36)        NOT NULL,
    expires_at DATETIME(6)     NOT NULL,
    used_at    DATETIME(6)     NULL,
    revoked_at DATETIME(6)     NULL,
    user_agent VARCHAR(255)    NULL,
    ip_address VARCHAR(45)     NULL,
    created_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    KEY ix_refresh_tokens_user_id (user_id),
    KEY ix_refresh_tokens_family_id (family_id),
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Token một lần: xác thực email (24 h), đặt lại mật khẩu (30 phút) — U1
CREATE TABLE user_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    type       VARCHAR(20)     NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    expires_at DATETIME(6)     NOT NULL,
    used_at    DATETIME(6)     NULL,
    created_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_tokens_token_hash UNIQUE (token_hash),
    KEY ix_user_tokens_user_id_type (user_id, type),
    CONSTRAINT fk_user_tokens_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_tokens_type CHECK (type IN ('EMAIL_VERIFY', 'PASSWORD_RESET'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Thành phần penalty của trust score (requirements §5.1): SUM(points) WHERE expires_at > now()
CREATE TABLE user_violations (
    id          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED  NOT NULL,
    source_type VARCHAR(30)      NOT NULL,
    source_id   BIGINT UNSIGNED  NOT NULL,
    points      TINYINT UNSIGNED NOT NULL DEFAULT 25,
    expires_at  DATETIME(6)      NOT NULL,
    created_by  BIGINT UNSIGNED  NULL,
    created_at  DATETIME(6)      NOT NULL,
    PRIMARY KEY (id),
    KEY ix_user_violations_user_id_expires_at (user_id, expires_at),
    CONSTRAINT fk_user_violations_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_violations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_user_violations_source_type CHECK (source_type IN ('REVIEW_REJECTED', 'REPORT_CONFIRMED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- 2. Địa điểm

CREATE TABLE categories (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT UNSIGNED NULL,
    name       VARCHAR(100)    NOT NULL,
    slug       VARCHAR(120)    NOT NULL,
    icon       VARCHAR(100)    NULL,
    sort_order INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    KEY ix_categories_parent_id (parent_id),
    CONSTRAINT fk_categories_parent_id FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE places (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id    BIGINT UNSIGNED NOT NULL,
    created_by     BIGINT UNSIGNED NOT NULL,
    owner_id       BIGINT UNSIGNED NULL,
    name           VARCHAR(200)    NOT NULL,
    slug           VARCHAR(220)    NOT NULL,
    description    TEXT            NULL,
    address        VARCHAR(300)    NOT NULL,
    city           VARCHAR(100)    NOT NULL,
    -- SRID 4326, thứ tự trục (lat, lng) theo EPSG — MySQL 8 bắt buộc SRID cố định để dùng spatial index
    location       POINT           NOT NULL SRID 4326,
    price_min      INT UNSIGNED    NULL,
    price_max      INT UNSIGNED    NULL,
    phone          VARCHAR(20)     NULL,
    website        VARCHAR(300)    NULL,
    status         VARCHAR(20)     NOT NULL,
    reject_reason  VARCHAR(500)    NULL,
    moderated_by   BIGINT UNSIGNED NULL,
    moderated_at   DATETIME(6)     NULL,
    -- Dẫn xuất (database.md §4): v, R và điểm Bayesian hiển thị — cập nhật bởi luồng event tính lại rating
    review_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    avg_rating     DECIMAL(3, 2)   NOT NULL DEFAULT 0,
    bayesian_score DECIMAL(4, 3)   NOT NULL DEFAULT 0,
    checkin_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    version        INT             NOT NULL DEFAULT 0,
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    deleted_at     DATETIME(6)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_places_slug UNIQUE (slug),
    SPATIAL KEY sx_places_location (location),
    KEY ix_places_category_id_status_bayesian_score (category_id, status, bayesian_score),
    KEY ix_places_status_created_at (status, created_at),
    KEY ix_places_owner_id (owner_id),
    KEY ix_places_created_by (created_by),
    CONSTRAINT fk_places_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_places_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_places_owner_id FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_places_moderated_by FOREIGN KEY (moderated_by) REFERENCES users (id),
    CONSTRAINT ck_places_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN')),
    CONSTRAINT ck_places_price_range CHECK (price_min IS NULL OR price_max IS NULL OR price_min <= price_max),
    CONSTRAINT ck_places_avg_rating CHECK (avg_rating BETWEEN 0 AND 5),
    CONSTRAINT ck_places_bayesian_score CHECK (bayesian_score BETWEEN 0 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE place_photos (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    place_id    BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    storage_key VARCHAR(300)    NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    is_cover    BOOLEAN         NOT NULL DEFAULT FALSE,
    sort_order  INT             NOT NULL DEFAULT 0,
    width       INT UNSIGNED    NULL,
    height      INT UNSIGNED    NULL,
    created_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_place_photos_place_id_sort_order (place_id, sort_order),
    KEY ix_place_photos_uploaded_by (uploaded_by),
    CONSTRAINT fk_place_photos_place_id FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_place_photos_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_place_photos_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Nhiều khung giờ / ngày được phép; close_time < open_time = mở qua đêm
CREATE TABLE opening_hours (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    place_id    BIGINT UNSIGNED NOT NULL,
    day_of_week TINYINT         NOT NULL,
    open_time   TIME            NOT NULL,
    close_time  TIME            NOT NULL,
    PRIMARY KEY (id),
    KEY ix_opening_hours_place_id_day_of_week (place_id, day_of_week),
    CONSTRAINT fk_opening_hours_place_id FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT ck_opening_hours_day_of_week CHECK (day_of_week BETWEEN 1 AND 7)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE amenities (
    id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100)    NOT NULL,
    slug VARCHAR(120)    NOT NULL,
    icon VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_amenities_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE place_amenity (
    place_id   BIGINT UNSIGNED NOT NULL,
    amenity_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (place_id, amenity_id),
    KEY ix_place_amenity_amenity_id (amenity_id),
    CONSTRAINT fk_place_amenity_place_id FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_amenity_amenity_id FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tags (
    id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(50)     NOT NULL,
    slug VARCHAR(60)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đa hình như plan (D5): taggable_id không có FK, toàn vẹn kiểm tra ở service.
-- Hiện chỉ gắn thẻ địa điểm; mở rộng loại khác = migration mới nới CHECK.
CREATE TABLE taggables (
    tag_id        BIGINT UNSIGNED NOT NULL,
    taggable_type VARCHAR(20)     NOT NULL,
    taggable_id   BIGINT UNSIGNED NOT NULL,
    created_by    BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (tag_id, taggable_type, taggable_id),
    KEY ix_taggables_taggable_type_taggable_id (taggable_type, taggable_id),
    KEY ix_taggables_created_by (created_by),
    CONSTRAINT fk_taggables_tag_id FOREIGN KEY (tag_id) REFERENCES tags (id),
    CONSTRAINT fk_taggables_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_taggables_taggable_type CHECK (taggable_type IN ('PLACE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Gộp theo ngày (giờ Việt Nam): INSERT ... ON DUPLICATE KEY UPDATE view_count = view_count + 1
CREATE TABLE place_views (
    place_id   BIGINT UNSIGNED NOT NULL,
    view_date  DATE            NOT NULL,
    view_count INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (place_id, view_date),
    CONSTRAINT fk_place_views_place_id FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- UC23, UC30. "Một yêu cầu đang chờ / địa điểm / người" kiểm tra ở service
CREATE TABLE place_claims (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    place_id      BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    contact_phone VARCHAR(20)     NOT NULL,
    evidence_note VARCHAR(1000)   NULL,
    evidence_keys JSON            NOT NULL,
    status        VARCHAR(20)     NOT NULL,
    reject_reason VARCHAR(500)    NULL,
    reviewed_by   BIGINT UNSIGNED NULL,
    reviewed_at   DATETIME(6)     NULL,
    version       INT             NOT NULL DEFAULT 0,
    created_at    DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_place_claims_place_id_status (place_id, status),
    KEY ix_place_claims_status_created_at (status, created_at),
    KEY ix_place_claims_user_id (user_id),
    CONSTRAINT fk_place_claims_place_id FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_place_claims_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_place_claims_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_place_claims_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- 3. Đánh giá & tương tác

CREATE TABLE reviews (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    place_id      BIGINT UNSIGNED  NOT NULL,
    user_id       BIGINT UNSIGNED  NOT NULL,
    rating        TINYINT UNSIGNED NOT NULL,
    content       TEXT             NOT NULL,
    visited_at    DATE             NOT NULL,
    status        VARCHAR(20)      NOT NULL,
    ip_address    VARCHAR(45)      NOT NULL,
    ip_prefix     VARCHAR(45)      NOT NULL,
    ip_flagged    BOOLEAN          NOT NULL DEFAULT FALSE,
    helpful_count INT UNSIGNED     NOT NULL DEFAULT 0,
    reject_reason VARCHAR(500)     NULL,
    moderated_by  BIGINT UNSIGNED  NULL,
    moderated_at  DATETIME(6)      NULL,
    version       INT              NOT NULL DEFAULT 0,
    created_at    DATETIME(6)      NOT NULL,
    updated_at    DATETIME(6)      NOT NULL,
    deleted_at    DATETIME(6)      NULL,
    PRIMARY KEY (id),
    -- Mỗi người một review / địa điểm, tính cả review đã xóa mềm (D2 = A)
    CONSTRAINT uk_reviews_place_id_user_id UNIQUE (place_id, user_id),
    KEY ix_reviews_place_id_status_created_at (place_id, status, created_at),
    KEY ix_reviews_user_id_created_at (user_id, created_at),
    KEY ix_reviews_place_id_ip_prefix_created_at (place_id, ip_prefix, created_at),
    KEY ix_reviews_status_created_at (status, created_at),
    CONSTRAINT fk_reviews_place_id FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT fk_reviews_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_moderated_by FOREIGN KEY (moderated_by) REFERENCES users (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'HIDDEN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE review_photos (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id   BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    storage_key VARCHAR(300)    NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    sort_order  INT             NOT NULL DEFAULT 0,
    width       INT UNSIGNED    NULL,
    height      INT UNSIGNED    NULL,
    created_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_review_photos_review_id_sort_order (review_id, sort_order),
    KEY ix_review_photos_uploaded_by (uploaded_by),
    CONSTRAINT fk_review_photos_review_id FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_photos_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_review_photos_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- PK(review_id, user_id) = UNIQUE(review_id, user_id) của plan §5.
-- counted: người vote có trust ≥ 30 tại thời điểm vote (requirements §5.1)
CREATE TABLE review_votes (
    review_id  BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    counted    BOOLEAN         NOT NULL,
    created_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (review_id, user_id),
    KEY ix_review_votes_user_id (user_id),
    CONSTRAINT fk_review_votes_review_id FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_votes_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2 cấp (Q5): parent_id chỉ trỏ tới bình luận gốc — kiểm tra ở service
CREATE TABLE comments (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id  BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    parent_id  BIGINT UNSIGNED NULL,
    content    VARCHAR(2000)   NOT NULL,
    status     VARCHAR(20)     NOT NULL,
    created_at DATETIME(6)     NOT NULL,
    updated_at DATETIME(6)     NOT NULL,
    deleted_at DATETIME(6)     NULL,
    PRIMARY KEY (id),
    KEY ix_comments_review_id_created_at (review_id, created_at),
    KEY ix_comments_user_id (user_id),
    KEY ix_comments_parent_id (parent_id),
    CONSTRAINT fk_comments_review_id FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_comments_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_comments_parent_id FOREIGN KEY (parent_id) REFERENCES comments (id),
    CONSTRAINT ck_comments_status CHECK (status IN ('VISIBLE', 'HIDDEN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Một phản hồi / review, sửa được (D6)
CREATE TABLE owner_replies (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id  BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    content    VARCHAR(2000)   NOT NULL,
    created_at DATETIME(6)     NOT NULL,
    updated_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_owner_replies_review_id UNIQUE (review_id),
    KEY ix_owner_replies_user_id (user_id),
    CONSTRAINT fk_owner_replies_review_id FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_owner_replies_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- U5: bán kính 200 m, accuracy ≤ 100 m kiểm tra ở service (tham số cấu hình được);
-- 1 check-in / địa điểm / ngày ràng buộc ở CSDL
CREATE TABLE check_ins (
    id           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED   NOT NULL,
    place_id     BIGINT UNSIGNED   NOT NULL,
    location     POINT             NOT NULL SRID 4326,
    accuracy_m   SMALLINT UNSIGNED NOT NULL,
    distance_m   SMALLINT UNSIGNED NOT NULL,
    checkin_date DATE              NOT NULL,
    created_at   DATETIME(6)       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_check_ins_user_id_place_id_checkin_date UNIQUE (user_id, place_id, checkin_date),
    KEY ix_check_ins_place_id (place_id),
    CONSTRAINT fk_check_ins_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_check_ins_place_id FOREIGN KEY (place_id) REFERENCES places (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- 4. Cộng đồng & vận hành

CREATE TABLE collections (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    name        VARCHAR(150)    NOT NULL,
    description VARCHAR(1000)   NULL,
    is_public   BOOLEAN         NOT NULL DEFAULT FALSE,
    share_slug  VARCHAR(40)     NULL,
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    -- NULL khi riêng tư; InnoDB cho phép nhiều NULL trong UNIQUE
    CONSTRAINT uk_collections_share_slug UNIQUE (share_slug),
    KEY ix_collections_user_id (user_id),
    CONSTRAINT fk_collections_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE collection_place (
    collection_id BIGINT UNSIGNED NOT NULL,
    place_id      BIGINT UNSIGNED NOT NULL,
    note          VARCHAR(500)    NULL,
    added_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (collection_id, place_id),
    KEY ix_collection_place_place_id (place_id),
    CONSTRAINT fk_collection_place_collection_id FOREIGN KEY (collection_id) REFERENCES collections (id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_place_place_id FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- FK giữ RESTRICT: MySQL không cho CHECK dùng cột có hành động tham chiếu CASCADE/SET NULL
CREATE TABLE follows (
    follower_id  BIGINT UNSIGNED NOT NULL,
    following_id BIGINT UNSIGNED NOT NULL,
    created_at   DATETIME(6)     NOT NULL,
    PRIMARY KEY (follower_id, following_id),
    KEY ix_follows_following_id (following_id),
    CONSTRAINT fk_follows_follower_id FOREIGN KEY (follower_id) REFERENCES users (id),
    CONSTRAINT fk_follows_following_id FOREIGN KEY (following_id) REFERENCES users (id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> following_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE badges (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    NULL,
    icon        VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_badges_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_badges (
    user_id    BIGINT UNSIGNED NOT NULL,
    badge_id   BIGINT UNSIGNED NOT NULL,
    awarded_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (user_id, badge_id),
    KEY ix_user_badges_badge_id (badge_id),
    CONSTRAINT fk_user_badges_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_badges_badge_id FOREIGN KEY (badge_id) REFERENCES badges (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- UC17, UC29. target_id đa hình, không FK — toàn vẹn kiểm tra ở service
CREATE TABLE reports (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(20)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    reason      VARCHAR(30)     NOT NULL,
    detail      VARCHAR(1000)   NULL,
    status      VARCHAR(20)     NOT NULL,
    handled_by  BIGINT UNSIGNED NULL,
    handled_at  DATETIME(6)     NULL,
    version     INT             NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    -- 1 báo cáo / người / đối tượng (U10)
    CONSTRAINT uk_reports_reporter_id_target_type_target_id UNIQUE (reporter_id, target_type, target_id),
    KEY ix_reports_status_target_type_target_id (status, target_type, target_id),
    KEY ix_reports_status_created_at (status, created_at),
    CONSTRAINT fk_reports_reporter_id FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_handled_by FOREIGN KEY (handled_by) REFERENCES users (id),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('PLACE', 'REVIEW', 'COMMENT', 'USER')),
    CONSTRAINT ck_reports_reason CHECK (reason IN ('SPAM', 'FAKE', 'OFFENSIVE', 'WRONG_INFO', 'OTHER')),
    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    type       VARCHAR(50)     NOT NULL,
    data       JSON            NOT NULL,
    read_at    DATETIME(6)     NULL,
    created_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_notifications_user_id_read_at_created_at (user_id, read_at, created_at),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- FR-42: ghi qua Spring AOP quanh phương thức service có @AuditedAction
CREATE TABLE activity_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(50)     NOT NULL,
    target_type VARCHAR(20)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    metadata    JSON            NULL,
    ip_address  VARCHAR(45)     NULL,
    created_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_activity_log_actor_id_created_at (actor_id, created_at),
    KEY ix_activity_log_target_type_target_id (target_type, target_id),
    CONSTRAINT fk_activity_log_actor_id FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
