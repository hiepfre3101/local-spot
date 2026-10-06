# Progress log

Ghi lại quyết định kỹ thuật: ngày giờ, task, bối cảnh, lý do, đánh đổi.

---

## 2026-09-27 20:44 (+07) — Checklist A: Chuẩn bị

**Task**: Hoàn thành phần còn lại của checklist A trong plan-v1.md (FR, NFR, phạm vi, quy ước git, board task).

**Bối cảnh**: Repo chỉ có tài liệu kế hoạch + docker-compose hạ tầng; chưa scaffold `backend/`, `frontend/`. Hai mục đầu của A (tên dự án, khảo sát) đã xong.

### Quyết định 1 — Đặt đặc tả yêu cầu ở `docs/requirements.md`, không nhét vào plan-v1.md
- **Lý do**: plan-v1.md là tài liệu định hướng tổng; bảng 43 FR + 22 NFR sẽ làm nó phình to. File riêng dễ copy vào Chương 3 báo cáo.
- **Đánh đổi**: thêm một tài liệu phải giữ đồng bộ → mỗi FR có cột "Nguồn" trỏ về mục trong plan-v1.md để truy vết; checklist trong plan link sang file này.

### Quyết định 2 — Phân tầng ưu tiên MoSCoW (Must/Should/Could)
- **Lý do**: plan §11 nêu rủi ro "ôm quá nhiều tính năng" và gợi ý chia MVP vs mở rộng, nhưng chưa nói tính năng nào thuộc tầng nào. MoSCoW là thuật ngữ chuẩn, dễ giải thích trước hội đồng.
- **Nguyên tắc**: 4 điểm khác biệt cốt lõi (chống review ảo, Bayesian, tìm kiếm tiếng Việt, spatial) bắt buộc Must; gamification (follow, huy hiệu, tags, banner) là Could — cắt đầu tiên nếu trễ.
- **Đánh đổi**: xếp check-in và báo cáo vi phạm vào Must (vì là một phần cơ chế chống review ảo / kiểm duyệt) làm MVP nặng hơn (30/43 FR).
- **Trạng thái**: đề xuất, chờ supervisor xác nhận (Q1).

### Quyết định 3 — Không tự chốt các con số/luật chưa có trong plan
- Các tham số (N review/ngày, trust score, ngưỡng IP, độ sâu bình luận, chỉ số NFR, TTL token) được ghi là **(đề xuất)** và liệt kê ở mục "Câu hỏi mở" Q1–Q6 thay vì âm thầm chọn — theo CLAUDE.md §4.
- Phát hiện lệch trong plan: "quản lý banner" có ở §4 nhưng không có bảng ở §5 → Q4.

### Quyết định 4 — Quy ước git ở `CONTRIBUTING.md`, nhánh `develop` tạo local
- Git flow rút gọn: `main` / `develop` / `feature/*` / `fix/*` / `hotfix/*` / `docs/*` theo plan §7; Conventional Commits có scope.
- **Không thêm commitlint/husky**: là dependency Node mới ngoài stack đã chốt (CLAUDE.md §4) và frontend chưa scaffold. Có thể thêm sau khi được đồng ý.
- **Chưa push `develop` / chưa bật branch protection / chưa tạo GitHub Projects board**: là thao tác ra bên ngoài (remote GitHub), chờ supervisor đồng ý.

---

## 2026-09-27 (+07) — Supervisor chốt câu hỏi mở checklist A

- **Q1 ✅** MoSCoW giữ nguyên như đề xuất.
- **Q2 ✅** Moderator: duyệt địa điểm/review/claim, xử lý báo cáo, ẩn nội dung. Admin: toàn bộ quyền moderator + khóa tài khoản, gán role, danh mục, tiện ích, dashboard. → Dùng làm seed `permissions`/`role_permissions`.
- **Q3 ⏳** Chốt `m = 10` cho Bayesian. N review/ngày, ngưỡng cảnh báo IP, giá trị `C`, công thức trust score **vẫn mở** — phải chốt trước khi thiết kế cột trust score trong ERD (checklist B).
- **Q4 ✅** Cắt tính năng banner: bỏ FR-43 (còn 42 FR: 30 M / 8 S / 4 C), sửa plan-v1.md §4 cho khớp. Lý do: không có bảng trong thiết kế CSDL, giá trị demo thấp; không phải xóa dữ liệu vì chưa có schema.
- **Q5 ✅** Bình luận tối đa 2 cấp → `comments.parent_id` chỉ trỏ tới bình luận gốc, không cần truy vấn đệ quy.
- **Q6 ✅** Các chỉ số NFR giữ như đề xuất (p95 < 300 ms / 200 ms search, Lighthouse ≥ 80/90, JWT 15 phút / 7 ngày, ảnh ≤ 5 MB, ≤ 10 ảnh/review, backup hằng ngày giữ 7 bản).

---

## 2026-09-27 (+07) — Chốt trọng số trust score (Q3, một phần)

- Công thức `trust = w1×age_score + w2×helpful_score − w3×penalty_score + base` với **w1 = 1, w2 = 2, w3 = 3, base = 10** (supervisor chốt). Chi tiết ở docs/requirements.md mục 5.1.
- Ý nghĩa: phạt nặng hơn thưởng (1 review bị từ chối = −30, bù lại cần ~8 vote hữu ích) → tài khoản spam khó "rửa" điểm; vote hữu ích nặng hơn tuổi tài khoản → ưu tiên chất lượng hơn thâm niên.
- Còn mở: mức trần các điểm thành phần (20 / 40 / 10 mỗi lỗi), chặn `[0, 100]` (vì max theo công thức = 110), ngưỡng hàng chờ (đề xuất 30).

---

## 2026-09-27 (+07) — Q3: ngưỡng trust score, tham số chống review ảo, đề xuất trần điểm

- **Đã chốt**: ngưỡng hàng chờ = 50; N = 5 review/ngày; cảnh báo IP ≥ 3 review/địa điểm/cùng /24/24 h; `C` tính lại mỗi lần tính lại rating.
- **Đề xuất trần** (chờ xác nhận): age trần 30 (+1/7 ngày), helpful +1/vote trần 30, phạt 20/vi phạm (×3 = −60), vi phạm hết hạn sau 90 ngày, kết quả chặn `[0, 100]`.
- **Lý do**: supervisor yêu cầu phạt đủ nặng để buộc tuân thủ luật → chọn P sao cho một vi phạm kéo người dùng 100 điểm xuống dưới 50 (`3P > 50`). Chọn trần sao cho tổng dương = đúng 100 (không có điểm dư để "đệm" hình phạt), và tuổi tài khoản một mình (40) không qua được ngưỡng → chặn sleeper account.
- **Đánh đổi**: hình phạt hết hạn 90 ngày (không vĩnh viễn) — vì các thành phần dương có trần nên người dùng đã đạt trần không thể cày lại điểm; phạt vĩnh viễn sẽ làm hàng chờ moderator phình theo thời gian. Ngưỡng 50 khá chặt: người dùng mới cần ~3 tháng + ~14 vote hữu ích mới thoát hàng chờ → tải moderator giai đoạn đầu cao hơn.

---

## 2026-09-27 (+07) — Q3: đổi ngưỡng 30, hạn phạt 30 ngày, chốt luật chống vote chéo

- **Đã chốt (supervisor)**: ngưỡng hàng chờ 50 → **30**; hạn hiệu lực vi phạm 90 → **30 ngày**; chỉ tính vote hữu ích từ người có trust ≥ ngưỡng.
- **Hệ quả phải tính lại trần** (đề xuất, chờ xác nhận): với ngưỡng 30, bộ trần cũ phá 2 ràng buộc đã đặt ra:
  - Phạt 20 (−60): người dùng 100 điểm vi phạm còn 40 ≥ 30 → phạt không còn tác dụng. Đổi sang **P = 25** (`100 − 3P < 30` → `P ≥ 24`).
  - Trần tuổi 30: sleeper account đạt 40 ≥ 30 → qua ngưỡng chỉ nhờ chờ. Đổi trần tuổi **18** (10 + 18 = 28 < 30), trần helpful **36** để tổng dương vẫn = 100.
- **Đề xuất kỹ thuật**: chụp trust của người vote tại thời điểm vote, lưu cờ `counted` trên `review_votes` → tránh tính trust đệ quy (trust A phụ thuộc vote từ B, trust B phụ thuộc vote từ A). Đánh đổi: nếu người vote bị phạt sau đó, vote cũ vẫn được tính.
- **Đánh đổi của ngưỡng 30**: người dùng mới thoát hàng chờ nhanh hơn (≈2 tháng + 6 vote, hoặc 10 vote hợp lệ) → giảm tải moderator, nhưng phụ thuộc nhiều hơn vào luật chống vote chéo.

---

## 2026-09-27 (+07) — Chốt Q3 và phạm vi

- **Q3 ✅**: trần age 18, trần helpful 36, phạt 25/vi phạm; chụp trust người vote tại thời điểm vote (cờ `counted` trên `review_votes`). → Ảnh hưởng ERD ở checklist B: cần cột lưu vi phạm có thời điểm (để tính hết hạn 30 ngày) và cột `counted` trên `review_votes`.
- **Phạm vi ✅**: supervisor bỏ 3 mục ngoài phạm vi bổ sung (đăng nhập mạng xã hội, gợi ý cá nhân hóa, kiểm duyệt AI) → danh sách ngoài phạm vi giữ nguyên theo plan-v1.md §1. Tick "Chốt phạm vi" trong checklist A.

---

## 2026-09-27 (+07) — Tạo board quản lý task (GitHub Projects)

- Board: https://github.com/users/hiepfre3101/projects/1 (private), đã link với repo `hiepfre3101/local-spot`.
- 93 thẻ sinh tự động từ checklist A–H của plan-v1.md; field single-select **Giai đoạn** (A…H); Status Done cho 6 mục đã xong ở A, còn lại Todo.
- **Chọn draft item thay vì issue**: tránh tạo 93 issue làm rối tab Issues ngay từ đầu; khi bắt tay làm một thẻ thì "Convert to issue" để link PR (`Refs #n`). Đánh đổi: draft item không có số issue, không tham chiếu được từ commit cho đến khi convert.
- Chọn GitHub Projects thay vì Trello: nằm cùng repo, link được issue/PR, có thể chụp làm minh chứng quy trình cho báo cáo.

---

## 2026-09-27 (+07) — Checklist B: sơ đồ use case tổng quát + đặc tả chi tiết (bản nháp)

- Checklist A hoàn tất (supervisor tự push `develop`). Bắt đầu B; board: 2 thẻ đầu B → In Progress.
- **PlantUML** (`docs/diagrams/*.puml`) thay vì draw.io: plan §2 khuyến nghị; sơ đồ dạng text, diff được trong git. Máy chưa có Java nên chưa render được PNG — xem bằng extension PlantUML của VS Code hoặc render sau khi cài JDK 21 (cần cho backend ở checklist C).
- **Gộp 42 FR thành 33 use case**: sơ đồ tổng quát với 42 use case sẽ quá dày để đưa vào báo cáo. Tác vụ hệ thống (Bayesian FR-22, activity log FR-42, xử lý ảnh) không vẽ thành use case vì không có tác nhân người khởi phát — mô tả ở sơ đồ tuần tự/hoạt động.
- Dùng quan hệ kế thừa tác nhân (Thành viên → Khách, Chủ → Thành viên, Admin → Moderator) để giảm số đường nối, khớp với RBAC đã chốt ở Q2.
- **Chọn 10 use case đặc tả chi tiết** (UC01/02, 03, 08, 09, 11, 12, 16, 23, 28, 29): phủ đủ 5 tác nhân và cả 4 điểm khác biệt cốt lõi.
- Các luật nghiệp vụ chưa có trong requirements.md được đánh dấu (đề xuất) và gom vào 10 câu hỏi mở U1–U10 — không tự chốt.

---

## 2026-09-27 (+07) — Chốt U1–U10; sơ đồ hoạt động & tuần tự (bản nháp)

- **U1–U10 ✅** (supervisor chốt toàn bộ đề xuất) → chép vào docs/requirements.md §5, bỏ nhãn (đề xuất) trong use-cases.md. Tick 2 mục đầu checklist B.
- **Sơ đồ hoạt động** (3, đúng plan §8): viết đánh giá, đề xuất & duyệt địa điểm, check-in — dùng swimlane để thấy rõ ranh giới người dùng / hệ thống / xử lý nền / kiểm duyệt viên.
- **Sơ đồ tuần tự** (3): đăng nhập + làm mới phiên, tìm kiếm (kèm fallback U9 và đồng bộ index), đăng review kèm ảnh. Participant dùng đúng tên lớp dự kiến ở plan §7 để sơ đồ lớp / code khớp nhau.
- **Quyết định kỹ thuật thể hiện trong sơ đồ**:
  - Event tính rating / xử lý ảnh dùng `@TransactionalEventListener(AFTER_COMMIT)`: tránh tính rating hoặc xử lý ảnh cho review mà transaction bị rollback. Đánh đổi: nếu app sập giữa commit và listener thì mất event (chấp nhận được với đồ án; hướng khắc phục: outbox pattern — nêu ở hướng phát triển).
  - Ảnh xuất JPEG thay vì WebP: ImageIO/Thumbnailator không ghi WebP nếu không thêm thư viện (vi phạm CLAUDE.md §4 nếu tự thêm).
  - Fallback tìm kiếm nằm trong `PlaceSearchService`, không lộ ra controller.
- **Chờ supervisor chốt**:
  - S1: refresh token qua cookie HttpOnly + SameSite=Strict, access token chỉ trong bộ nhớ (không localStorage) — chống XSS đánh cắp refresh token; đánh đổi: cần cấu hình CORS `allowCredentials` và đăng nhập lại khi reload trang nếu refresh lỗi.
  - S2: review + ảnh trong một request multipart qua API thay vì presigned URL lên MinIO — server kiểm tra được MIME thật (NFR-09), đơn giản hơn; đánh đổi: ảnh đi qua băng thông của API.
  - Ghi chú cho ERD: cần cột IP (/24) trên `reviews` cho luật cảnh báo IP; cần phân biệt `avg_rating` (trung bình thô) và điểm Bayesian hay dùng chung một cột.

---

## 2026-09-27 (+07) — Render sơ đồ bằng JDK trong WSL

- Supervisor cho dùng JDK 21 có sẵn trong WSL Ubuntu 22.04 (OpenJDK 21.0.12). Tải `plantuml.jar` 1.2026.8 vào `~/tools` của WSL (ngoài repo, không phải dependency dự án).
- Dùng layout **Smetana** (tích hợp trong PlantUML) thay vì cài Graphviz — tránh cần `sudo apt` trong WSL; chất lượng layout đủ cho báo cáo.
- Render 7 sơ đồ ra `docs/diagrams/png/`, kiểm tra từng ảnh: không lỗi cú pháp. Lệnh render ghi ở `docs/diagrams/README.md`.

---

## 2026-09-27 (+07) — Chốt S1–S3; thiết kế CSDL + ERD (bản nháp)

- **S1, S2, S3 ✅** → ghi vào requirements.md §5; tick sơ đồ hoạt động & tuần tự. Board: 3 thẻ B tiếp theo → In Progress.
- **docs/design/database.md**: 32 bảng (plan §5 + `refresh_tokens`, `user_tokens`, `user_violations`; bỏ `banners`). **ERD** tách 3 sơ đồ theo miền — một ERD 32 bảng không đọc được trên trang A4.
- **Quyết định thiết kế** (lý do chi tiết trong database.md §1):
  - Trạng thái dùng `VARCHAR + CHECK` thay `ENUM`: thêm trạng thái không cần `ALTER TABLE` sửa định nghĩa ENUM; map `@Enumerated(STRING)`.
  - `DATETIME(6)` lưu UTC; ngày nghiệp vụ (`checkin_date`, `view_date`) tính theo giờ Việt Nam.
  - Luật U5 (1 check-in/ngày) và U10 (1 báo cáo/người/đối tượng) enforce bằng UNIQUE ở CSDL, không chỉ ở service — chặn race condition.
  - `user_violations.points` lưu mức phạt tại thời điểm ghi → đổi cấu hình không làm sai lịch sử.
  - `review_votes` dùng PK tổ hợp (review_id, user_id) — thỏa `UNIQUE(review_id, user_id)` của plan mà không cần cột id.
  - `reports.target_id` không có FK (quan hệ đa hình) — đánh đổi toàn vẹn tham chiếu lấy tính tổng quát, kiểm tra ở service.
- **Phát hiện khi thiết kế** (chờ supervisor, D1–D7): trust score tính động hay lưu; soft delete xung đột với `UNIQUE(place_id, user_id)` và `users.email`; index plan §5 dùng `avg_rating` nhưng xếp hạng dùng `bayesian_score`; **`C` thay đổi làm bayesian_score của mọi địa điểm khác lỗi thời** → đề xuất job đêm; tags đa hình; số phản hồi chủ quán.
- Sơ đồ lớp để sau khi chốt ERD (entity JPA phản ánh 1-1 bảng).

---

## 2026-09-28 (+07) — Chốt D1, D3–D7 (thiết kế CSDL)

- **D1 ✅** trust score tính khi cần, không lưu cột. **D5 ✅** giữ `tags`/`taggables` đa hình. **D6 ✅** 1 phản hồi chủ quán / review, sửa được.
- **D3 ✅** đổi index `places(category_id, status, avg_rating)` → `(category_id, status, bayesian_score)` và **sửa plan-v1.md §5** trong cùng thay đổi (ràng buộc cốt lõi, CLAUDE.md §3 — đã được supervisor xác nhận).
- **D4 ✅** thêm `RatingRecalculationJob` chạy 03:00 hằng đêm tính lại `bayesian_score` của mọi địa điểm với `C` mới; ghi vào requirements.md §5 và database.md §4.
- **D7 ✅** xóa tài khoản = xóa mềm + ẩn danh hóa email / tên / avatar / bio + thu hồi refresh token.
- **D2 ⏳** supervisor hỏi lại ý nghĩa đề xuất → giải thích bằng ví dụ, chờ quyết định.
- **D2 ✅ = A** (2026-09-28): giữ nguyên `UNIQUE(place_id, user_id)` tính cả review đã xóa mềm. Lý do: chặn chiêu xóa-viết lại để làm mới review / xóa review bị từ chối để thử lại; không đổi ràng buộc cốt lõi. Đánh đổi: người xóa nhầm không viết lại được → UI cảnh báo khi xóa, gợi ý sửa. Ghi vào FR-18. Tick ERD + bảng mô tả CSDL.

---

## 2026-09-28 (+07) — Sơ đồ lớp (bản nháp)

- Tách 2 sơ đồ: **class-domain** (32 bảng → entity, nhóm theo 4 miền như ERD) và **class-review-module** (Controller → DTO → Service → Repository → Entity + event/listener cho module đánh giá). Lý do: sơ đồ lớp gồm cả service của mọi module sẽ không đọc được; module đánh giá chứa đủ 3 điểm nhấn (chống review ảo, trust, Bayesian) nên đại diện tốt cho kiến trúc.
- **Quyết định thể hiện trong sơ đồ**:
  - `BaseEntity` / `SoftDeletableEntity` (`@MappedSuperclass`) gom id + audit + xóa mềm — tránh lặp ở 4 entity xóa mềm.
  - Hành vi nghiệp vụ đặt trên entity (`Review.publish/reject`, `Place.applyRating`, `RefreshToken.isUsable`) thay vì setter trần — giữ bất biến trạng thái ở một chỗ; service chỉ điều phối.
  - Bảng nối có cột thêm (`review_votes`, `collection_place`, `follows`, `user_badges`) → entity riêng với `@EmbeddedId`; bảng nối thuần (`user_roles`, `role_permissions`, `place_amenity`) → `@ManyToMany`.
  - Tham số chống review ảo gom vào `AntiFakeProperties` (`@ConfigurationProperties`) — đổi cấu hình để thử nghiệm Chương 5 mà không sửa code (requirements §5.1).
  - `RatingCalculator` là hàm thuần, không I/O → unit test độc lập (checklist G).
- **Chờ supervisor (C1)**: kiểu Java cho cột `POINT` — `hibernate-spatial` (JTS `Point`) hay `lat`/`lng` + native query.
- **C1 ✅ = A** (2026-09-28): dùng `hibernate-spatial` + JTS `Point` cho `places.location`, `check_ins.location`. Là module chính thức của Hibernate (đã trong stack) nhưng vẫn là dependency mới → đã hỏi trước theo CLAUDE.md §4 và thêm vào bảng stack plan §2. Tick sơ đồ lớp.

---

## 2026-09-29 (+07) — Đặc tả API OpenAPI

- **C1 = A** được supervisor xác nhận bằng "okay" (hiểu là chấp nhận phương án khuyến nghị).
- `docs/api/openapi.yaml`: OpenAPI 3.1, 83 operation, lint hợp lệ bằng Redocly CLI (`--extends=minimal`, 0 lỗi). Công cụ lint chạy qua `npx` (cache npm), không thêm vào dự án. WSL thiếu `python3-venv` nên không dùng openapi-spec-validator.
- **Quyết định & lệch so với plan §6 (đã sửa plan cùng lúc)**:
  - Vote `POST` → `PUT`/`DELETE` idempotent.
  - Tách `/owner/*` và `/moderation/*` khỏi `/admin/*`: nhóm route theo vai trò khớp RBAC Q2, và tránh trùng đường dẫn `/places/{slug}` (GET chi tiết, giữ đúng plan) với `/places/{placeId}` (PATCH của chủ) — Redocly báo `no-ambiguous-paths`.
  - Bỏ "response bọc API Resource" (khái niệm Laravel) → DTO MapStruct, không envelope.
  - Quyết định duyệt dùng `POST .../decision {APPROVE|REJECT, reason, version}` thay vì hai endpoint approve/reject: một chỗ kiểm tra optimistic lock, dễ ghi activity_log.
  - `PlaceUpdateRequest` không cho chủ đổi vị trí / danh mục — tránh "dời" địa điểm để thoát review xấu; đổi phải qua báo cáo WRONG_INFO.
  - `GET /moderation/claims/{id}/evidence/{i}` trả presigned URL 5 phút — minh chứng nằm bucket riêng tư.
- Còn cảnh báo (không phải lỗi): thiếu `operationId` (sẽ lấy từ tên method controller khi springdoc sinh), thiếu mô tả tag.

---

## 2026-09-29 (+07) — Đối chiếu design system với plan; chốt 4 điểm lệch

**Bối cảnh**: đọc `docs/design-system/` (bản xuất từ Claude Design: token, `bundle.css` lớp `.ls-*`, README + preview HTML; không có component code) và đối chiếu với plan / requirements.

- **UI ✅ Tailwind, bỏ shadcn-vue / PrimeVue** (sửa plan §2). Token design system đưa vào theme Tailwind, component Vue tự viết. Lý do: hai thư viện kia mang sẵn bóng đổ, bo góc, style riêng — xung đột quy tắc "không bóng, quản trị bo 2px, không card lồng card". Đánh đổi: tự viết Button / FormField / DataTable / dialog → nhiều việc hơn, bù lại UI khớp design system.
- **Một đánh giá / người / địa điểm ✅** — trả lời câu hỏi mở P04 theo `UNIQUE(place_id, user_id)` (D2). Không đổi schema.
- **Check-in ✅ giữ U5** (200 m, accuracy ≤ 100 m, 1 lần / địa điểm / ngày). Supervisor ban đầu nói "không giới hạn" — mâu thuẫn với U5 đã có trong ERD (`UQ(user_id, place_id, checkin_date)`), API (409) và sơ đồ hoạt động → hỏi lại, supervisor giữ U5. Lý do giữ: check-in cộng điểm xếp hạng; không giới hạn thì cày điểm được.
- **Duyệt đánh giá ✅ giữ luồng lõi**: trust ≥ 30 đăng ngay; < 30 hoặc cảnh báo IP → hàng chờ. **Không** theo design system ("mọi đánh giá vào hàng chờ") vì làm trust score mất tác dụng (điểm bảo vệ), tải moderator tăng tuyến tính theo số review.
- **Hàng chờ gộp FIFO ✅**: thêm `GET /moderation/queue` (openapi) gộp 4 loại, sắp xếp cố định `(submittedAt, type, id)`, cursor keyset (không OFFSET), chỉ trả loại người gọi có quyền. Là màn **xem**; duyệt vẫn qua `decision` riêng từng loại → không đổi luật nghiệp vụ, optimistic lock giữ nguyên. Triển khai dự kiến: native `UNION ALL` 4 bảng dùng index `(status, created_at)` sẵn có. Đánh đổi: báo cáo gom nhóm theo đối tượng nên `submittedAt` = báo cáo đầu tiên của nhóm; `position` cần một `COUNT` các mục cũ hơn.
- Đã sửa: plan-v1.md §2, §6; requirements.md §3.7, §5; openapi.yaml; design-system `DECISIONS.md`, `use-cases.md`.
- **Còn lệch**: các README component (P03, P04, ReviewItem…) và project gốc trên Claude Design vẫn ghi "mọi đánh giá vào hàng chờ" — cần sửa ở nguồn Claude Design rồi xuất lại, không sửa tay bản xuất.

---

## 2026-09-29 (+07) — Duyệt P01–P12; sitemap; đóng checklist B

- **Supervisor duyệt P01–P12** và coi mục "Prototype Figma" là xong (prototype làm trên Claude Design thay Figma — đã sửa dòng checklist plan §10.B cho đúng công cụ thực tế, tránh báo cáo ghi Figma mà không có file Figma).
- **Cách hiểu "duyệt"**: chấp nhận phương án đề xuất của từng màn; câu hỏi nào đã có câu trả lời trong tài liệu chốt (U1, U5, U6, U7, D2, FR-10, FR-26, FR-30, FR-39, ngoài phạm vi) thì **tài liệu chốt thắng**. Phát hiện P02 thiếu bộ lọc khoảng giá so với FR-10 → bổ sung trong sitemap. Bảng kết luận ở `docs/design/sitemap.md` §4.
- **Không tự chốt** 7 câu hỏi không có phương án đề xuất hoặc chạm schema (O1–O7). Đáng chú ý nhất **O2**: giữ bản cũ của đánh giá đã đăng trong lúc bản sửa chờ duyệt cần bảng lưu bản sửa — `reviews` hiện chỉ có một trạng thái. Không ảnh hưởng sitemap, phải chốt trước module đánh giá.
- **Quyết định sitemap**:
  - `/propose` thay vì `/places/new` — tránh đụng route `/places/:slug` (slug "new").
  - Tab trang cá nhân / chủ địa điểm dùng query `?tab=` thay vì route con — giữ link chia sẻ được mà không nhân đôi số route.
  - Danh sách riêng từng loại hàng chờ = `/admin/queue?type=` — một màn DataTable, khớp API `GET /moderation/queue`.
  - Check-in (P12) và thông báo (P07) là hộp thoại / bảng thả xuống, không có route.
- Sơ đồ `sitemap.puml` dạng WBS (PlantUML, cùng công cụ các sơ đồ khác), render bằng JDK trong WSL.
- `docs/design-system/` đưa vào `.gitignore` theo yêu cầu supervisor. Hệ quả: token phải chép vào cấu hình Tailwind trong `frontend/` (được commit) khi scaffold ở checklist C.

---

## 2026-09-29 (+07) — Checklist C: thiết lập môi trường

**Quyết định (supervisor chốt qua câu hỏi):**
- **Spring Boot 4.1.1** thay 3.3: dòng 3.x hết hỗ trợ OSS (3.3 từ 06/2025), Initializr chỉ còn 4.0/4.1. Khởi đầu dự án mới trên bản EOL không có bản vá bảo mật là điểm yếu khi bảo vệ. Đánh đổi: kéo theo Spring Security 7, Hibernate 7, Jackson 3, Testcontainers 2 — tài liệu/ví dụ trên mạng ít hơn 3.x. Đã sửa plan §2 và CLAUDE.md.
- **Build backend trong WSL** (JDK 21, Docker đã có). Đánh đổi: I/O qua `/mnt/d` chậm hơn (verify lần đầu ~3,5 phút gồm tải dependency + image).
- **Dev: hạ tầng trong Docker, app chạy host**; backend + nginx dưới profile `app` cho chạy full stack / deploy. Lý do: hot reload & debug trên host; không thay đổi hành vi `docker compose up` hiện có của nhóm.
- **Spotless (palantir-java-format) + SpotBugs, bỏ Checkstyle**: formatter tự sửa được, Checkstyle trùng phần lớn và dễ xung đột rule.

**Quyết định kỹ thuật khác:**
- `application-dev.yml` import `../.env` (`optional:file:../.env[.properties]`) → một nguồn thông tin đăng nhập cho cả compose và backend. `application-prod.yml` không có giá trị mặc định cho bí mật → thiếu biến thì khởi động lỗi ngay.
- `open-in-view: false`, `ddl-auto: validate`, JDBC time zone UTC (khớp database.md §1).
- Testcontainers ghim `mysql:8.4`, `rabbitmq:3.13-management-alpine`, `redis:7-alpine` = đúng image compose (Initializr sinh `:latest` → test có thể chạy trên MySQL khác production).
- **Gỡ oxlint** mà create-vue tự thêm: công cụ ngoài stack (CLAUDE.md §4); ESLint dùng `flat/recommended` thay `essential`.
- **Tailwind 4 theme = design system**: reset `--color-*`, `--shadow-*`, `--text-*`, `--radius-*`, `--font-*` về `initial`, chỉ khai báo token LocalSpot → `bg-blue-500`, `shadow-md`, `text-sm` không sinh CSS, lệch design system lộ khi review. Màu dùng `@theme inline` trỏ `var(--surface)`… để đổi theme bằng `data-theme`. Khoảng cách giữ mặc định (lưới 4px trùng `space-*`). Token màu chép vào `frontend/src/assets/tokens.css` (được commit) vì `docs/design-system/` bị ignore.
- nginx: SPA fallback, proxy `/api` + `/ws` (cùng origin → cookie refresh token SameSite=Strict — S1), `client_max_body_size 55m` (10 ảnh × 5 MB — S2/NFR-09); backend `forward-headers-strategy: framework` để lấy IP thật cho luật cảnh báo IP.
- Font Be Vietnam Pro qua Google Fonts `<link>` — không thêm package.

**Kiểm chứng:** `./mvnw verify` xanh (1 smoke test trên MySQL/RabbitMQ/Redis thật, Spotless, SpotBugs 0 lỗi); frontend lint + format + type-check + Vitest + build xanh; `docker compose --profile app up --build`: backend profile prod kết nối MySQL, Flyway chạy, SPA 200, deep link 200, `/api` qua nginx tới Spring Security (401 — đúng, SecurityConfig làm ở D); profile dev trên host đọc `.env`, health 200. CI chưa chạy trên GitHub (chạy khi push).

---

## 2026-09-30 21:20 (+07) — Checklist D1: Flyway migration + khóa ngoại + index

**Bối cảnh**: bắt đầu D (backend nền tảng) trên nhánh `feature/be-foundation` (tách từ `feature/infra-dev-environment` vì nhánh đó chưa merge vào `develop` mà D cần scaffold backend). Supervisor chọn nhịp **dừng sau mỗi mục** để duyệt.

**Supervisor chốt trước khi viết schema:**
- **O2 = về PENDING cả bài**: tác giả trust < 30 sửa đánh giá đã đăng → đánh giá quay về `PENDING`, rời trang và điểm xếp hạng tới khi duyệt lại. Không cần bảng lưu bản sửa → `reviews` giữ nguyên một trạng thái. Đánh đổi: bài biến mất tạm thời; đổi lại không thêm bảng / loại mục hàng chờ, và event tính lại rating vốn đã xử lý chuyển trạng thái PUBLISHED ↔ khác. Phương án "sửa không cần duyệt" bị loại vì mở lỗ hổng đăng sạch rồi sửa thành spam.
- **Seed demo = Flyway SQL riêng cho dev** (làm ở D2): file SQL đặt ngoài `db/migration`, chỉ bật ở profile dev.

**Quyết định kỹ thuật (D1):**
- 3 migration: `V1__init.sql` (32 bảng, đúng database.md), `V2__seed_rbac.sql`, `V3__seed_catalog.sql`. Tách seed tham chiếu khỏi schema để đọc/duyệt riêng; dữ liệu tham chiếu nằm trong Flyway vì **mọi** môi trường đều cần (khác demo).
- **Khóa ngoại RESTRICT mặc định**, CASCADE chỉ ở bảng nối thuần. Lý do: bảng nghiệp vụ xóa mềm, một lệnh `DELETE` cứng là lỗi lập trình → CSDL nên chặn thay vì xóa dây chuyền review/ảnh (CLAUDE.md §4 cấm mất dữ liệu). Test `refusesToHardDeleteUserWithContent` khẳng định.
- **Permission = đúng 17 giá trị `x-permission` trong openapi.yaml**, không tự thêm. Kế thừa: OWNER ⊇ USER (plan §4), ADMIN ⊇ MODERATOR (Q2); MODERATOR không kèm quyền USER — tài khoản nhân sự được gán thêm role USER. `user:view` chỉ cho ADMIN (Q2 xếp quản lý người dùng vào admin).
- **Danh mục / tiện ích khởi đầu** seed ở V3 (mọi môi trường): UC11 bắt buộc chọn danh mục nên hệ thống mới không dùng được nếu rỗng; admin sửa qua UC32. Danh sách do agent đề xuất theo phạm vi plan (quán ăn / cà phê / check-in) — supervisor có thể đổi bằng migration mới hoặc qua UC32. `icon` NULL (chưa có bộ icon). **Badges không seed** vì O3 chưa chốt.
- Index / CHECK bổ sung ngoài database.md (index hàng chờ `(status, created_at)` cho `place_claims`, `reports`; CHECK điểm trong [0, 5]…) — đã ghi ở database.md §3.5.
- Không `DEFAULT CURRENT_TIMESTAMP`: phụ thuộc `time_zone` session, dễ lệch UTC.
- `TestcontainersConfiguration` đổi sang `public` để test ở package con dùng được.

**Phát hiện**: MySQL vi phạm CHECK (mã 3819) → Spring ném `UncategorizedSQLException`, không phải `DataIntegrityViolationException`. Phải xử lý riêng trong `@RestControllerAdvice` (D7), nếu không sẽ thành 500.

**Kiểm chứng**: `./mvnw verify` xanh — 12 test (11 test schema mới trên MySQL 8.4 thật: đủ 32 bảng, collation, spatial index SRID 4326, thứ tự trục (lat, lng) qua `ST_Distance_Sphere` Hồ Gươm → Văn Miếu ≈ 1,27 km, UNIQUE review tính cả xóa mềm, UNIQUE vote, CHECK, 1 check-in/ngày, FK RESTRICT, RBAC kế thừa, cây danh mục), Spotless, SpotBugs 0 lỗi. Chưa áp dụng lên DB dev cục bộ (sẽ tự chạy khi khởi động backend).

---

## 2026-09-30 22:08 (+07) — Checklist D2: Entity JPA + dữ liệu demo

**Bối cảnh**: supervisor duyệt D1 (đã commit `85e8ebb`) và yêu cầu "fake thêm một số data của users hay địa điểm".

**Supervisor chốt (qua câu hỏi):**
- **hibernate-spatial** để map `POINT SRID 4326` (dependency mới — module chính thức của Hibernate, version do Spring Boot quản lý). Phương án bị loại: tự viết converter sang định dạng nhị phân nội bộ MySQL — ít phụ thuộc hơn nhưng là code tự chế khó giải thích. Đã thêm vào plan §2 và CLAUDE.md §2.
- **Quy mô seed: ~60 tài khoản, ~300 địa điểm** (khớp 200–500 của plan).

**Quyết định kỹ thuật:**
- **29 entity + 3 bảng nối `@ManyToMany`** (`user_roles`, `role_permissions`, `place_amenity` — không mang dữ liệu riêng nên không cần entity). Bảng khóa tổ hợp có cột riêng (`review_votes`, `collection_place`, `follows`, `user_badges`, `taggables`, `place_views`) dùng `@EmbeddedId` là record + `@MapsId`.
- **Không Lombok** (ngoài stack) → getter/setter viết tay; chỉ có setter cho trường nghiệp vụ được sửa. Cột dẫn xuất `review_count / avg_rating / bayesian_score` **không có setter** — chỉ luồng tính lại rating được ghi (điểm lõi CLAUDE.md §3).
- Mọi `@ManyToOne` là `LAZY` (tránh N+1 ngầm, khớp `open-in-view: false`). Chỉ `Place → openingHours` là aggregate con có cascade.
- Thời gian dùng `Instant` + `DateTimeProvider` trả `Clock.instant()` (bean `Clock` UTC — test thay được). `@EnableJpaAuditing` đặt ở `JpaConfig` riêng để test slice web không kéo JPA.
- `GeoPoints.of(lat, lng)` là nơi duy nhất tạo `Point` (JTS dùng x = lng) — test xác nhận hibernate-spatial ghi đúng thứ tự trục (lat, lng) của MySQL cho SRID 4326.
- `day_of_week` dùng converter ISO 1–7 thay vì `@Enumerated(ORDINAL)` (ordinal bắt đầu từ 0 → lệch 1 ngày âm thầm).
- Cột `TINYINT/SMALLINT UNSIGNED` cần `@JdbcTypeCode` để `ddl-auto: validate` chấp nhận `int`.
- Bảng `collections` → entity `PlaceCollection` (tránh trùng `java.util.Collection`).

**Seed demo** (`db/seed/dev/R__demo_01_users.sql`, `R__demo_02_places.sql`, chỉ `application-dev.yml` thêm vào `spring.flyway.locations`):
- **Repeatable `R__` thay vì `V__`**: version dùng chung một dãy với migration schema; nếu seed chiếm V1000 thì V4 thêm sau sẽ bị Flyway coi là "out of order" và từ chối. Đánh đổi: script chạy lại mỗi khi sửa → mọi lệnh phải idempotent (`INSERT IGNORE` theo khóa UNIQUE, `NOT EXISTS`), không xóa / ghi đè (CLAUDE.md §4).
- **Sinh hàng bằng SQL (CTE đệ quy + công thức số học, không `RAND`)** thay vì dán 300 dòng từ script ngoài: vẫn là "Flyway SQL riêng cho dev" như supervisor chọn, nhưng file gọn, sửa danh sách tên / đường phố ngay trong file, tất định giữa các máy, không cần thêm công cụ sinh (Python...) vào repo.
- Email `@localspot.test` (TLD dành riêng), mật khẩu chung `LocalSpot2026`; **phone để trống** vì số bịa có thể trùng số thật. Tên / địa chỉ hư cấu.
- **Chưa seed review / vote / check-in**: `review_count`, `bayesian_score` phải khớp review; seed thẳng SQL sẽ phải chép công thức Bayesian và trust ra SQL (hai nguồn sự thật cho điểm lõi). Làm ở E: seed review thô rồi chạy job tính lại rating. Hệ quả tạm thời: mọi tài khoản demo trust < 30, mọi địa điểm điểm 0.

**Kiểm chứng**: `./mvnw verify` xanh — 24 test (thêm `EntityMappingTests` 6: trục tọa độ, auditing, giờ mở cửa ISO + qua đêm, xóa mềm, JSON, khóa tổ hợp + role/permission; `DemoSeedTests` 6: 60 tài khoản đúng vai trò, mật khẩu khớp `BCryptPasswordEncoder`, 300 địa điểm đúng phân bổ thành phố / trạng thái, tọa độ < 7 km quanh tâm, giờ mở cửa & tiện ích theo loại hình, **chạy lại script không đổi số dòng**), SpotBugs 0 lỗi. Chưa khởi động backend trên DB dev cục bộ — seed sẽ nạp ở lần `spring-boot:run` tới.

---

## 2026-10-01 22:30 (+07) — Checklist D3: Spring Security + JWT, đăng ký / đăng nhập / đăng xuất / refresh

**Bối cảnh**: supervisor duyệt D2 (commit `10699e7`, đã push `feature/be-foundation`) và cho làm tiếp. O3 (bộ huy hiệu / điểm đóng góp) giải thích cho supervisor: chưa cần chốt tới mục huy hiệu ở E.

**Supervisor chốt (qua câu hỏi):**
- **JWT bằng OAuth2 Resource Server (Nimbus)** thay vì jjwt + `JwtFilter` tự viết. Lý do: filter Bearer token của Spring Security đã kiểm chứng rộng rãi (hạn dùng, chữ ký, thuật toán), giảm code bảo mật tự viết — chỗ dễ có lỗ hổng nhất. Đánh đổi: ít "tự tay" hơn khi trình bày cơ chế; bù lại phần tự viết vẫn có (phát hành token, xoay vòng refresh, nạp quyền). Vẫn tự phát hành token, **không** dùng máy chủ OAuth2/Keycloak. **Lưu ý**: plan §2 ghi rõ `jjwt` — câu hỏi lúc đó chỉ nhắc §7 (`JwtFilter`); đã sửa cả §2, §7 và CLAUDE.md cho khớp.
- **Quyền đọc CSDL mỗi request**: JWT chỉ mang `sub` = id người dùng; converter nạp user + role + permission (1 truy vấn `@EntityGraph`). Khóa tài khoản / gỡ role / xóa tài khoản có hiệu lực ngay (test `lockingAnAccountRevokesItsAccessImmediately`). Đánh đổi: +1 truy vấn / request — cache Redis nếu đo thấy cần.
- **Trust score làm ngay** (`TrustScoreCalculator` thuần + `TrustScoreService`), vì `MeResponse.trustScore` cần và công thức đã chốt đủ ở requirements §5.1. Tham số trong `localspot.trust.*` (application.yml).

**Quyết định kỹ thuật:**
- **HS256, khóa base64 ≥ 32 byte**, từ chối khởi động nếu khóa ngắn hơn; decoder chỉ chấp nhận HS256 (chặn đổi thuật toán) + kiểm issuer + hạn dùng. Một backend vừa phát hành vừa xác thực nên khóa đối xứng đủ; RS256 chỉ cần khi có dịch vụ khác xác thực token.
- **Khóa JWT theo môi trường**: dev có mặc định trong `application-dev.yml` (không bắt sửa `.env` hiện có), prod `${JWT_SECRET}` không mặc định; `docker-compose.yml` thêm `JWT_SECRET: ${JWT_SECRET:?...}` cho service backend (thay đổi Compose — cần để profile prod khởi động; báo lỗi rõ nếu thiếu). `.env.example` thêm biến.
- **Refresh token xoay vòng**: 32 byte ngẫu nhiên base64url, DB lưu SHA-256. Đánh dấu đã dùng bằng `UPDATE ... WHERE used_at IS NULL` nguyên tử → hai request đồng thời cùng token chỉ một thắng. Token đã dùng bị gửi lại → **thu hồi mọi token còn hiệu lực của người dùng** (U2), `@Transactional(noRollbackFor = ApiException.class)` để lệnh thu hồi được commit dù trả 401 (test `reusingRotatedTokenRevokesEverySession`). Token đã bị thu hồi do đăng xuất mà gửi lại → chỉ 401, không thu hồi hàng loạt (không đăng xuất các thiết bị khác vô cớ).
  - **Hệ quả cho frontend (checklist F)**: interceptor phải gộp các lần refresh đồng thời (single-flight); hai tab refresh cùng lúc sẽ bị coi là dùng lại → đăng xuất mọi nơi. Chấp nhận vì đúng U2; không thêm "cửa sổ ân hạn".
- **Đăng nhập chống dò email**: email không tồn tại vẫn chạy BCrypt với hash giả → cùng thời gian phản hồi, cùng thông báo lỗi. Chưa xác thực email vẫn đăng nhập được (FR-02 chỉ chặn viết review). Khóa → 403 `ACCOUNT_LOCKED` kèm thời hạn + lý do. Giới hạn 5 lần / 15 phút để ở D8.
- **Đăng ký**: email chuẩn hóa chữ thường; trùng → 409 (kiểm tra trước + bắt `DataIntegrityViolationException` cho trường hợp chen nhau). DTO chỉ có 3 trường → gửi kèm `roles`, `emailVerifiedAt` bị bỏ qua (test mass assignment). Gửi mail xác thực để D4.
- **Mật khẩu**: validator `@ValidPassword` — ≥ 8 ký tự, có chữ và số, **≤ 72 byte UTF-8** (BCrypt bỏ qua phần thừa; tiếng Việt có dấu 2–3 byte / ký tự). BCrypt cost 10, không dùng `{bcrypt}` prefix (khớp seed).
- **CSRF tắt**: access token trong header; cookie duy nhất là refresh token với SameSite=Strict + Path=/api/v1/auth.
- **Danh sách trắng endpoint công khai** (`POST /api/v1/auth/**`, health, info); còn lại mặc định cần đăng nhập — quên khai báo thì lỗi theo hướng an toàn.
- **Một định dạng lỗi**: 401/403 trong filter chuyển qua `HandlerExceptionResolver` tới `GlobalExceptionHandler` → `application/problem+json` có `code`. Khách chạm `@PreAuthorize` nhận 401, không phải 403. Validate → 422 + `errors[]`. Handler hiện bản tối thiểu; D7 bổ sung phần còn lại (404, optimistic lock, mã 3819, 500).
- Thêm **MapStruct** (đã có trong stack) với `unmappedTargetPolicy=ERROR` — trường DTO quên map là lỗi biên dịch. Thêm `GET /api/v1/me` (đã có trong openapi) vì frontend cần nạp lại hồ sơ khi tải lại trang.
- `AuthenticatedUser` (record bất biến) làm principal thay vì entity — không lazy loading ngoài transaction. SpotBugs bắt `Authentication` phải serializable → sửa đúng gốc (record `Serializable`), không thêm loại trừ.

**Kiểm chứng**: `./mvnw verify` xanh — **56 test** (thêm `TrustScoreCalculatorTest` 10 ca = bảng ví dụ §5.1 + biên; `ValidPasswordValidatorTest` 9; `AuthFlowTests` 13 qua HTTP trên MySQL thật: đăng ký/đăng nhập + thuộc tính cookie, trùng email không phân biệt hoa thường, 422, mass assignment, sai mật khẩu ≡ sai email, tài khoản khóa, token hết hạn / sai issuer / giả chữ ký, khóa có hiệu lực ngay, xoay vòng, dùng lại → thu hồi toàn bộ, đăng xuất), SpotBugs 0 lỗi.

---

## 2026-10-01 23:00 (+07) — Checklist D4: xác thực email, quên / đặt lại mật khẩu, đổi mật khẩu

**Bối cảnh**: supervisor duyệt D3 (commit `959bec7`, chưa push).

**Supervisor chốt (qua câu hỏi):** gửi mail bằng **RabbitMQ + spring-boot-starter-mail + Mailpit** (dependency mới chính thức của Spring + container mới trong Compose). Lý do: NFR-13 yêu cầu tác vụ nền có retry + dead-letter; `@Async` mất mail khi restart. Mailpit cho thấy email thật khi demo mà không gửi ra ngoài.

**Quyết định kỹ thuật:**
- **Luồng mail**: service phát `MailRequestedEvent` → `MailQueuePublisher` (`@TransactionalEventListener(AFTER_COMMIT)`) đẩy `EmailMessage` (JSON) lên exchange `localspot.tasks` → queue `mail.send` → `MailConsumer` gửi SMTP. Lỗi SMTP → retry 4 lần (2 s, ×3, tối đa 30 s) → reject → `localspot.dlx` → `mail.send.dlq` (giữ lại để xem / chạy lại trong Management UI).
  - **Sau commit**: không gửi link cho tài khoản bị rollback; token chắc chắn có trong CSDL khi người dùng bấm. Đánh đổi: RabbitMQ sập đúng giữa commit và publish thì mất mail (không có transactional outbox) — người dùng tự khắc phục bằng "gửi lại" / "quên mật khẩu"; outbox đủ tốt hơn nhưng thêm bảng + job, chưa đáng ở quy mô đồ án.
  - Nội dung mail dựng ở phía gửi (text + HTML tự viết, `HtmlUtils.htmlEscape` tên người dùng — không thêm template engine ngoài stack). Consumer generic, không truy cập CSDL. Log lỗi không ghi email người nhận (NFR-11).
- **Token một lần** (`user_tokens`): 32 byte ngẫu nhiên, lưu SHA-256; 24 h / 30 phút (U1, cấu hình `localspot.security.*-ttl`). Cấp link mới → link cũ cùng loại bị vô hiệu (đặt `used_at`, không xóa — UC01 7a). Dùng bằng `UPDATE ... WHERE used_at IS NULL` nguyên tử. Không tồn tại / hết hạn / đã dùng → cùng **410 `TOKEN_INVALID`** (không cho dò).
- **Link** theo sitemap: `{localspot.app.public-url}/verify-email?token=`, `/reset-password?token=`; prod lấy `APP_PUBLIC_URL`.
- **Gửi lại mail xác thực** cần đăng nhập (openapi không có `security: []`) → thêm luật `authenticated()` cho `/api/v1/auth/resend-verification` **trước** luật `permitAll` của `/api/v1/auth/**` trong `SecurityConfig`. Đã xác thực rồi thì 204 mà không gửi.
- **Quên mật khẩu** luôn 204 kể cả email không tồn tại. Còn chênh thời gian xử lý nhỏ (có email thì thêm 1 insert + publish) — chấp nhận; giới hạn tần suất ở D8 làm việc dò không đáng kể.
- **Đặt lại mật khẩu** → đổi hash + thu hồi mọi refresh token (kẻ giữ phiên cũ bị đăng xuất). Không tự đánh dấu email đã xác thực (đặc tả không yêu cầu — không tự thêm hành vi).
- **Đổi mật khẩu** (FR-06 "thu hồi các refresh token khác"): cookie refresh token có `Path=/api/v1/auth` nên `PUT /me/password` không biết token hiện tại → thu hồi tất cả rồi cấp family mới, trả `Set-Cookie` cho thiết bị đang thao tác. Kết quả đúng yêu cầu: thiết bị này giữ phiên, thiết bị khác bị đăng xuất. Access token cũ ở thiết bị khác còn ≤ 15 phút (đánh đổi stateless từ D3).
- **Đề xuất sửa đặc tả (chưa sửa)**: `PUT /me/password` sai mật khẩu hiện tại trả **401** theo openapi. Interceptor frontend thường hiểu 401 = hết phiên → tự refresh / đăng xuất. Hiện phân biệt bằng `code = INVALID_CURRENT_PASSWORD`; đề xuất đổi sang 422 kèm lỗi trường `currentPassword` — chờ supervisor.

**Lỗi phát hiện và đã sửa:**
1. **Lệch múi giờ JDBC**: Hibernate ghi UTC (`hibernate.jdbc.time_zone`), còn JDBC thuần (JdbcTemplate, native query) dùng múi giờ JVM (+07) → giá trị ghi bằng JDBC lệch 7 giờ. Lộ ra khi test "link hết hạn" vẫn được chấp nhận. Sửa ở gốc: `spring.datasource.hikari.data-source-properties.connectionTimeZone=UTC` + `forceConnectionTimeZoneToSession=true`; thêm test hồi quy `hibernateAndPlainJdbcAgreeOnUtcTimestamps`. Dữ liệu hiện có không bị ảnh hưởng (chỉ Hibernate và seed SQL — đều UTC — từng ghi).
2. **Compose hỏng từ commit D3**: `${JWT_SECRET:?...}` làm `docker compose up -d` (chỉ hạ tầng) lỗi khi `.env` chưa có biến, vì Compose nội suy cả service đang tắt. Đổi thành `${JWT_SECRET:-}`; thiếu biến thì backend prod tự từ chối khởi động (`@NotBlank` + kiểm tra độ dài khóa). Commit D3 chưa push nên chưa ảnh hưởng ai; sửa nằm trong commit D4.
3. Mock `JavaMailSender` làm health check mail của Actuator lỗi → tắt `management.health.mail` ở profile test (dev / prod vẫn bật).

**Test**: consumer RabbitMQ tắt ở profile test (`auto-startup: false`), chỉ bật trong `MailFlowTests` — test khác không đụng mail.

**Kiểm chứng**: `./mvnw verify` xanh — **67 test** (thêm `MailFlowTests` 10 ca đầu-cuối API → RabbitMQ → consumer → đọc token trong mail → API: xác thực email một lần + trust lên 10, nội dung mail, gửi lại vô hiệu link cũ, gửi lại cần đăng nhập, link hết hạn 410, đặt lại mật khẩu thu hồi mọi phiên, quên mật khẩu không lộ email, mật khẩu yếu 422, đổi mật khẩu giữ thiết bị này / đăng xuất thiết bị khác, **SMTP lỗi → 4 lần thử → DLQ** mà đăng ký vẫn 201; + 1 test hồi quy múi giờ), SpotBugs 0 lỗi. `docker compose config` hợp lệ cả chế độ hạ tầng lẫn `--profile app`.

---

## 2026-10-03 16:20 (+07) — Checklist D5: RBAC + `@PreAuthorize`

**Bối cảnh**: D4 đã commit (`afbce9e`). Bảng role / permission và việc nạp authority mỗi request đã có từ D1 / D3 — phần còn thiếu của D5 là phân quyền ở tầng phương thức và endpoint thật dùng nó.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi D5 = hạ tầng + API UC31** (`GET /admin/users`, `POST/DELETE /admin/users/{id}/lock`, `PUT /admin/users/{id}/roles`) — kéo sớm một phần mục E "API quản trị". Lý do: D5 không có endpoint thật nào được bảo vệ thì `@PreAuthorize` chỉ kiểm chứng được bằng controller giả trong test; UC31 chính là màn quản trị RBAC.
- **Luật gán role (đóng O6)**: mọi tài khoản luôn có USER (thiếu → 422 `ROLE_USER_REQUIRED`); **OWNER không gán / gỡ qua API này** — chỉ cấp qua duyệt yêu cầu sở hữu UC30, vì OWNER đi cùng `places.owner_id`: gỡ tay để lại chủ quán mất quyền nhưng vẫn là `owner_id`. Danh sách gửi lên có OWNER khác hiện trạng → 422 `OWNER_ROLE_MANAGED_BY_CLAIM` (báo lỗi thay vì âm thầm bỏ qua; gửi lại nguyên danh sách từ `GET` vẫn hợp lệ).
- **Admin tự bảo vệ**: không tự khóa, không tự gỡ ADMIN của mình (409 `SELF_ACTION_FORBIDDEN`) → luôn còn ≥ 1 admin. Vẫn khóa / hạ quyền admin khác được. Phương án "không đụng admin khác" bị loại vì phải sửa CSDL mới hạ được một admin.
- **`PUT /me/password` sai mật khẩu hiện tại: 401 → 422** (câu treo từ D4) kèm `errors[0].field = currentPassword`. Lý do: interceptor frontend coi 401 là hết phiên → tự refresh / đăng xuất. Đã sửa openapi + test.

**Quyết định kỹ thuật:**
- `@EnableMethodSecurity` trong `SecurityConfig`; luật URL vẫn chỉ phân biệt công khai / cần đăng nhập, **quyền cụ thể khai báo bằng `@PreAuthorize` theo permission** ngay trên endpoint (đọc cạnh `x-permission` của openapi). Theo permission chứ không theo role → đổi quyền của role chỉ cần migration dữ liệu.
- **Hằng số `Permissions`** + nối chuỗi trong annotation (`"hasAuthority('" + Permissions.USER_LOCK + "')"`): gõ sai là lỗi biên dịch. Chuỗi trần `hasAuthority('user:lok')` sẽ âm thầm chặn mọi người.
- **`PermissionCatalogTests`** giữ ba nơi khai báo khớp nhau: bảng `permissions` (V2) = `Permissions.ALL` = mọi `x-permission` trong openapi; đồng thời quét `RequestMappingHandlerMapping`: endpoint `/api/v1/admin|moderation|owner/**` **bắt buộc có `@PreAuthorize`** (quên chú thích = mở cho mọi thành viên đã đăng nhập) và authority trong `@PreAuthorize` phải là permission có thật. Không thêm luật URL `hasRole('ADMIN')` cho `/admin/**` (phòng thủ kép) vì gắn cứng role, trùng nguồn sự thật; test quét thay thế vai trò đó.
- **Tìm kiếm người dùng**: keyset cursor theo id giảm dần (`KeysetCursor`, base64url mờ) — không OFFSET. Truy vấn 2 bước: lấy id có LIMIT, rồi nạp user + roles bằng `@EntityGraph` — gộp fetch collection với LIMIT sẽ buộc Hibernate phân trang trong bộ nhớ. Trust score cả trang tính với **một** truy vấn `GROUP BY` điểm phạt (`trustScoresOf`) thay vì N truy vấn. LIKE thoát `! % _` (ESCAPE `'!'` — không dùng `\` vì MySQL hiểu `\` trong literal là ký tự thoát chuỗi). Collation `utf8mb4_0900_ai_ci` → "quan tri" khớp "Quản Trị". LIKE `%q%` quét toàn bảng `users` — chấp nhận ở quy mô màn quản trị; tìm kiếm người dùng không cần Meilisearch.
- **Khóa**: `until` bắt buộc ở tương lai, lý do 1–500 ký tự; khóa lại = cập nhật thời hạn / lý do; thu hồi mọi refresh token. Access token cũ bị chặn ngay nhờ nạp quyền mỗi request (D3). Mở khóa idempotent. **Không ghi `user_violations`**: requirements §5.1 chỉ tính vi phạm từ review bị từ chối / báo cáo được xác nhận.
- **Gán role có hiệu lực ngay** ở request kế tiếp của người bị đổi quyền (không cần thu hồi token) — test `grantedRoleTakesEffectWithoutNewToken`.
- `ApiException` thêm `errors[]` theo trường (cùng dạng lỗi validate) + `ApiException.fieldError(...)`; mã lỗi mới `USER_NOT_FOUND`, `SELF_ACTION_FORBIDDEN`, `ROLE_USER_REQUIRED`, `OWNER_ROLE_MANAGED_BY_CLAIM`, `INVALID_CURSOR`. `CursorPage<T>` dùng chung cho các danh sách sau.
- **Chưa ghi `activity_logs`** (FR-42, Should): đặc tả yêu cầu ghi qua Spring AOP cho **mọi** thao tác admin / moderator — làm một lần ở E cùng API quản trị, không rải tay từng endpoint.
- SpotBugs: thêm loại trừ hẹp `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` chỉ cho lớp `*MapperImpl` — MapStruct sinh `if (user == null) return null;` rồi lại `if (user != null)` cho method một nguồn đối tượng; mã sinh tự động, không sửa được. Cảnh báo NP trong `GlobalExceptionHandler` sửa ở gốc.
- Sitemap: đánh dấu O6 ✅ và O2 ✅ (O2 đã chốt ngày 2026-09-30 ở D1 nhưng bảng chưa cập nhật).

**Kiểm chứng**: `./mvnw verify` xanh — **85 test** (thêm `AdminUserFlowTests` 14 ca qua HTTP trên MySQL thật: khách 401, thành viên 403 trên cả 4 endpoint, moderator không khóa được (UC29 3b), role mới có hiệu lực với access token cũ, tìm không dấu + phân trang cursor, lọc role / trạng thái khóa, LIKE thoát ký tự đại diện, tham số sai 422, khóa thu hồi phiên + mở khóa idempotent, không tự khóa nhưng khóa admin khác được, validate khóa + 404, luật USER / OWNER / tự gỡ ADMIN; `PermissionCatalogTests` 4 ca; sửa ca đổi mật khẩu sang 422), Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.
- **Bổ sung sau duyệt (supervisor)**: giữ nguyên các chỗ sửa tài liệu ở trên; thêm mục checklist E "Nhật ký thao tác quản trị (FR-42)" vào plan §10 — làm AOP cùng mục E đầu tiên có thao tác duyệt (duyệt địa điểm, openapi đã ghi "ghi activity_log"), không đợi tới mục cuối "API quản trị" để các thao tác kiểm duyệt làm ở giữa không phải gắn log bù.

---

## 2026-10-03 17:04 (+07) — Checklist D6: `PermissionEvaluator` cho Place, Review, Comment

**Bối cảnh**: supervisor duyệt D5 (commit `cf2ad4c`, chưa push), giữ nguyên các chỗ sửa tài liệu và thêm mục FR-42 vào checklist E. Các endpoint cần kiểm tra quyền sở hữu (`PATCH/DELETE /reviews/{id}`, `DELETE /comments/{id}`, `/owner/places/{id}/**`, `PUT/DELETE /reviews/{id}/reply`) đều thuộc mục E.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi = chỉ evaluator + test**, không kéo endpoint từ E. Endpoint ở E chỉ cần gắn `@PreAuthorize("hasPermission(#id, 'Review', 'review:update-own')")`. Phương án kéo `/owner/places` bị loại: đụng module địa điểm trước E1 (CRUD + luồng duyệt), dễ phải sửa lại.
- **Không có đường vượt quyền** cho moderator / admin trên quyền `*-own`: nhân sự xử lý nội dung người khác qua endpoint `/moderation` (có log, có tính vi phạm vào trust) — đúng cách openapi đã tách; không cho nhân sự âm thầm sửa chữ trong review của người khác.

**Quyết định kỹ thuật:**
- **`OwnershipPermissionEvaluator`** — bảng luật permission → (loại đối tượng, truy vấn sở hữu): `review:update-own` / `review:delete-own` → tác giả review; `comment:delete-own` → tác giả bình luận; `place:update-own` / `place:stats-own` → `places.owner_id`; `review:reply-own-place` → chủ địa điểm của review. Permission trong `hasPermission` vừa là authority RBAC vừa chọn luật → một biểu thức kiểm cả hai, và `PermissionCatalogTests` (D5) tự kiểm tên permission trong biểu thức.
- **Thứ tự**: (1) thiếu authority → 403 ngay, không chạm CSDL, không cho dò id; (2) bản ghi không tồn tại / đã xóa mềm → **404** mã theo loại (`PLACE_NOT_FOUND`, `REVIEW_NOT_FOUND`, `COMMENT_NOT_FOUND`); (3) không phải chủ → 403. Chọn 404 thay vì gộp vào 403: nội dung review / bình luận / địa điểm vốn công khai nên phân biệt không lộ gì đáng kể; đổi lại client nhận đúng ngữ nghĩa REST (xóa lần hai → 404). Đánh đổi nhỏ: id review đang chờ duyệt của người khác trả 403 thay vì 404 → biết id đó tồn tại.
- **Kiểm theo id, không nạp entity**: mỗi luật là một truy vấn `SELECT CASE WHEN … THEN TRUE ELSE FALSE END` chỉ đọc khóa ngoại → `Optional<Boolean>` (rỗng = không tồn tại, `false` = của người khác). `LEFT JOIN owner` để địa điểm chưa có chủ trả `false` thay vì mất dòng (bị hiểu nhầm là 404). Xóa mềm tự loại nhờ `@SQLRestriction`. Thêm `ReviewRepository`, `CommentRepository` (mới chỉ có truy vấn sở hữu).
- **Luật theo trạng thái** (vd. tác giả có được sửa review đang `HIDDEN` / `REJECTED`?) **không** đặt trong evaluator — đó là luật nghiệp vụ của service ở E; evaluator chỉ trả lời "người này có phải chủ không".
- **Gọi sai là lỗi lập trình**: permission không phải loại sở hữu, hoặc loại đối tượng không khớp permission → `IllegalArgumentException` (500, test bắt được) thay vì âm thầm `false` — chuỗi sai trong annotation sẽ lộ ngay ở lần chạy đầu thay vì chặn mọi người.
- **Đăng ký**: bean `static MethodSecurityExpressionHandler` nhận `@Lazy PermissionEvaluator` — hạ tầng method security tạo rất sớm, tiêm thẳng evaluator (phụ thuộc repository) sẽ kéo JPA khởi tạo sớm theo. Lớp evaluator `final` (SpotBugs `CT_CONSTRUCTOR_THROW`: constructor có thể ném — `Map.of` trùng khóa — lớp con có thể giữ đối tượng dở dang qua finalizer); không cần proxy CGLIB vì tiêm qua interface.
- **Test qua đúng đường chạy thật** (`OwnershipPermissionEvaluatorTests`, 11 ca): bean `Guarded` chỉ có trong test, chú thích `@PreAuthorize` y hệt endpoint E sẽ dùng; quyền nạp từ CSDL bằng `UserJwtAuthenticationConverter` như request có access token. Khẳng định `ApiException` 404 ném ra từ evaluator **không bị SpEL / Spring Security bọc lại** → `GlobalExceptionHandler` sẽ chuyển đúng thành 404.

**Sự cố môi trường**: lần `verify` đầu, container MySQL của Testcontainers mất kết nối khi context MockMvc đầu tiên khởi động (`Communications link failure`) → các lớp dùng chung context bị bỏ qua (ngưỡng lỗi context = 1). Không liên quan code: chạy lại các lớp đó và hai lần `verify` toàn bộ sau đó đều xanh. Theo dõi — nếu lặp lại thường xuyên thì xem tài nguyên Docker trong WSL.

**Kiểm chứng**: `./mvnw verify` xanh — **96 test** (+11: tác giả / người lạ, nhân sự không vượt quyền, tác giả thiếu authority bị chặn, review không tồn tại / xóa mềm → 404, kiểm quyền trước tồn tại, bình luận, chủ / không chủ / địa điểm chưa có chủ, `owner_id` lệch role bị chặn, phản hồi chỉ chủ địa điểm của review, cấu hình sai ném lỗi, mọi permission `*-own` có luật), Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.

---

## 2026-10-03 17:33 (+07) — Checklist D7: `@RestControllerAdvice` chuẩn hóa response và exception

**Bối cảnh**: supervisor duyệt D6 (commit `146e44e`, chưa push). `GlobalExceptionHandler` đã có bản tối thiểu từ D3 (lỗi xác thực, 401/403, validate DTO, `ApiException`); D7 phủ phần còn lại.

**Supervisor chốt (qua câu hỏi):**
- **400 `MALFORMED_REQUEST`** cho request không đọc được (JSON hỏng, sai kiểu / thiếu tham số), **422** cho đọc được nhưng sai luật — đúng RFC 9110. Frontend gửi đúng kiểu thì không bao giờ gặp 400 → 400 là lỗi lập trình phía client. Phương án "gộp hết vào 422" bị loại vì lẫn lỗi cú pháp với lỗi nghiệp vụ. openapi thêm quy ước chung + response `BadRequest`, không liệt kê 400 ở từng operation.
- **500 kèm `errorId`** (UUID): body chỉ có câu chung chung (không stack trace, không SQL), log ghi cùng `errorId` + stack trace → người dùng / tester báo mã là tra đúng dòng log. Thêm thuộc tính tùy chọn `errorId` vào schema `Problem`.

**Quyết định kỹ thuật:**
- **Không bọc envelope cho response thành công** — trả thẳng DTO như openapi; HTTP status đã mang thông tin thành công / thất bại. "Chuẩn hóa response" của checklist = chuẩn hóa định dạng lỗi.
- **Mọi lỗi Spring MVC** (lớp cha `ResponseEntityExceptionHandler` xử lý: JSON hỏng, sai kiểu tham số, 404 không có route, 405, 406, 413, 415…) đi qua `handleExceptionInternal` → gắn `code` + câu tiếng Việt **theo HTTP status** (`Problems.forStatus`). Ánh xạ theo status thay vì theo từng lớp ngoại lệ: phủ toàn bộ, không lỗi nào lọt ra thiếu `code` khi Spring thêm loại ngoại lệ mới.
- **Ràng buộc trên tham số controller** (`@Min` trên `@RequestParam`) → 422 `VALIDATION_FAILED` cùng dạng `errors[]` như lỗi DTO, thay vì 400 mặc định.
- **Optimistic lock** (`OptimisticLockingFailureException` của Spring + `OptimisticLockException` JPA gốc) → 409 `CONCURRENT_MODIFICATION` ("tải lại rồi thử lại").
- **Lỗi CSDL phân loại theo mã lỗi MySQL** trong chuỗi nguyên nhân, không theo lớp ngoại lệ Spring — vì Spring dịch không đồng nhất (CHECK 3819 có SQLSTATE HY000 nên thành `UncategorizedSQLException`, phát hiện ở D1): 1062 trùng UNIQUE → 409 `DUPLICATE_RESOURCE`; 3819 CHECK và 1452 khóa ngoại thiếu cha → 422 `VALIDATION_FAILED`; còn lại (mất kết nối, 1451 khi xóa cứng — lỗi lập trình, cú pháp SQL…) → 500. Tới được nhánh 409/422 này nghĩa là service chưa chặn trước (hoặc hai request chen nhau) → log WARN để sửa; service vẫn nên ném `ApiException` mã cụ thể (vd. `EMAIL_ALREADY_EXISTS`).
- **Log không ghi query string** (chỉ method + path) — query có thể chứa dữ liệu cá nhân (NFR-11, vd. `?q=email`).
- **`FallbackErrorController` thay `BasicErrorController` ở `/error`**: lỗi phát sinh ngoài Spring MVC (vd. CSDL sập đúng lúc filter JWT nạp quyền) không tới được `@RestControllerAdvice`; container chuyển tới `/error` và mặc định Spring Boot trả JSON dạng khác (`timestamp, status, error, path`) không có `code` / `errorId`. Không khai báo `produces` để trang lỗi trả được cho mọi `Accept`. Hàm dựng body dùng chung (`Problems`) để hai nơi trả cùng định dạng.

**Kiểm chứng**: `./mvnw verify` xanh — **109 test** (+13 `ErrorHandlingTests`: JSON hỏng 400, sai kiểu / thiếu tham số 400, ràng buộc tham số 422 kèm trường, route không tồn tại 404, sai method 405, sai content type 415, optimistic lock 409, trùng khóa thật trên MySQL 409, CHECK 3819 thật → 422 (không phải 500), FK 1452 → 422, lỗi bất ngờ 500 không lộ chi tiết + `errorId` trùng log, lỗi SQL chưa phân loại 500, `/error` cùng định dạng). Lỗi CSDL thử bằng bảng tạm `TEMPORARY TABLE` hoặc lệnh bị chặn hoàn toàn — không ghi dữ liệu thật. Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.

---

## 2026-10-04 14:58 (+07) — Checklist D8: Rate limiting

**Bối cảnh**: D7 đã commit + push (`e3074e4`). Mục cuối của checklist D.

**Supervisor chốt (qua câu hỏi):**
- **Redis, không thêm Bucket4j** — không thêm thư viện ngoài stack (CLAUDE.md §4); Redis đã có trong stack và Compose.
- **Ngưỡng** (ngoài U2 đã chốt — đăng nhập 5 lần sai / 15 phút theo (email, IP)): đăng ký **10 / giờ / IP** (nới vì nhiều máy chung một IP — wifi trường, phòng bảo vệ); quên mật khẩu **3 / giờ / email** (chặn spam hộp thư một người) và **20 / giờ / IP** (chặn dò hàng loạt email); gửi lại mail xác thực **3 / giờ / tài khoản**. Đã ghi vào requirements §5 và openapi `TooManyRequests`.
- **Redis sập → cho qua + log ERROR** (fail-open): Redis là phụ trợ; chặn hết sẽ biến Redis thành điểm hỏng duy nhất (không ai đăng nhập được). Brute force vẫn bị BCrypt làm chậm; health check báo Redis DOWN.
- **Giới hạn 5 review / 24 giờ (FR-23) đếm trong CSDL** (làm ở E), không qua Redis: là một phần cơ chế chống review ảo (lõi — CLAUDE.md §3), phải chính xác, kiểm chứng được bằng dữ liệu và **không tắt theo Redis** (vì đã chọn fail-open). Vẫn trả 429.

**Quyết định kỹ thuật:**
- **Cửa sổ trượt (sliding window log) bằng Lua** (`redis/sliding-window-rate-limit.lua`): sorted set, score = thời điểm ms; một script = một thao tác nguyên tử. Cửa sổ cố định (`INCR` + `EXPIRE`) đơn giản hơn nhưng cho dồn gần 2N lần quanh ranh giới hai cửa sổ (5 lần cuối cửa sổ trước + 5 lần đầu cửa sổ sau trong vài giây) — sai với "5 lần / 15 phút". Đánh đổi: bộ nhớ O(N) mỗi khóa thay vì O(1). Mỗi khóa chứa tối đa `limit` phần tử của luật đó (lần bị từ chối không được ghi, mốc quá cửa sổ bị xóa) — ngưỡng lớn nhất trong cấu hình hiện tại là 20 (`forgot-password-ip`) nên không đáng kể; nếu sau này có luật ngưỡng lớn (hàng nghìn / phút) thì cân nhắc Sliding Window Counter (O(1), xấp xỉ).
- **Lần bị từ chối không được ghi** → thử dồn khi đang bị chặn không kéo dài thời gian chặn; tối đa đúng N lần trong mọi cửa sổ. `Retry-After` = lúc lần cũ nhất rời cửa sổ (làm tròn lên giây).
- **Đăng nhập**: mỗi lần thử chiếm một lượt **trước** khi kiểm mật khẩu (nguyên tử → 100 request song song cũng chỉ 5 lần được thử; kiểm-trước-ghi-sau sẽ cho cả loạt lọt), mật khẩu đúng thì xóa bộ đếm → thực chất chỉ lần sai bị tính (U2). Vượt ngưỡng → 429 **kể cả khi mật khẩu đúng** — không cho kẻ dò biết đã trúng. Giới hạn theo (email, IP) đúng U2: người dùng thật ở mạng khác không bị khóa theo kẻ tấn công. Hạn chế đã biết: kẻ tấn công đổi email liên tục từ một IP (credential stuffing) không bị luật này chặn — U2 không yêu cầu; có thể thêm luật theo IP nếu cần.
- **Đăng ký** tính cả lần thất bại do email trùng (chặn dò email đã đăng ký bằng đăng ký hàng loạt); request sai validate (422) không tới service nên không tính.
- **Quên mật khẩu** đếm theo email cả khi email không tồn tại → 429 không tiết lộ email nào đã đăng ký (giữ nguyên tắc chống dò của D4).
- **Khóa băm SHA-256** (`rl:<luật>:<sha256>`): email / IP không nằm dạng rõ trong Redis (NFR-11); khóa có TTL = một cửa sổ, không tích rác.
- **Timeout Redis 500 ms** (`spring.data.redis.timeout`, `connect-timeout`): mặc định Lettuce chờ 60 s — "cho qua khi Redis sập" sẽ thành "mỗi lần đăng nhập treo 60 s".
- Gọi giới hạn ở **service** (`AuthService`, `AccountService`), không ở controller / interceptor: luật cần dữ liệu trong body (email) và kết quả nghiệp vụ (đăng nhập đúng → xóa bộ đếm); IP truyền qua `ClientInfo` có sẵn — service vẫn không phụ thuộc servlet API.
- Ngưỡng cấu hình ở `localspot.rate-limit.policies.*` (map theo enum `RateLimitPolicy`); thiếu ngưỡng cho một luật → từ chối khởi động. 429 dùng `RateLimitExceededException` + header `Retry-After` (giây) đúng openapi; câu báo "thử lại sau N phút".
- **Profile test** nới ngưỡng đăng ký (mọi request MockMvc từ 127.0.0.1, các lớp test đăng ký hàng chục tài khoản); `RateLimitFlowTests` đặt lại đúng giá trị production (10).

**Kiểm chứng**: `./mvnw verify` xanh — **123 test** (+7 `RateLimiterTests` trên Redis thật với đồng hồ điều khiển được: chặn sau N lần + `Retry-After` đúng, cửa sổ trượt từng lượt (khác cửa sổ cố định), lần bị từ chối không tính, khóa độc lập theo chủ thể / luật, reset, khóa băm + có TTL, **Redis sập → cho qua + log**; +7 `RateLimitFlowTests` qua HTTP với ngưỡng production: lần đăng nhập thứ 6 → 429 kể cả mật khẩu đúng, giới hạn theo (email, IP), đăng nhập đúng xóa bộ đếm, đăng ký thứ 11 / IP → 429, quên mật khẩu thứ 4 / email → 429 cả với email không tồn tại, thứ 21 / IP → 429, gửi lại mail thứ 4 → 429). Spotless, SpotBugs 0 lỗi. **Checklist D hoàn tất.** Chưa commit — chờ supervisor duyệt.

---

## 2026-10-04 21:44 (+07) — Checklist E1: CRUD địa điểm + luồng duyệt (kèm FR-42 nhật ký quản trị)

**Bối cảnh**: checklist D hoàn tất (D8 `4a7104d`, đã merge vào `develop`). Mục E đầu tiên; theo bổ sung ở D5, nhật ký thao tác quản trị (FR-42) làm cùng mục này. Nhánh `feature/be-places`.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi = đầy đủ CRU**: `POST /places` (multipart, phần `place`; `photos` để E2), `GET /places` (lọc + sắp xếp + cursor), `GET /places/{slug}`, `GET /me/places`, `GET /owner/places`, `PATCH /owner/places/{id}`, `GET /moderation/places`, `POST /moderation/places/{id}/decision` + `@AuditedAction`. Để lại: `/places/duplicates` + `/nearby` (E4), `/search` (E5), ảnh (E2). Không có "D" — openapi không có endpoint xóa địa điểm; ẩn đi qua xử lý báo cáo (UC29).
- **Luật duyệt**: chỉ PENDING → APPROVED / REJECTED, đã xử lý → 409 `PLACE_ALREADY_MODERATED` (không đảo quyết định — APPROVED → REJECTED sẽ đụng review / rating đã có); **cấm tự duyệt** đề xuất của chính mình (409 `SELF_ACTION_FORBIDDEN`).
- **PlaceDetail tính thật ngay** các trường phụ thuộc bảng khác (đã có trong schema): phân bố sao (review PUBLISHED), `claimable`, `myReviewId`, ghi lượt xem `place_views` — mục sau không phải quay lại sửa DTO.

**Quyết định kỹ thuật:**
- **Nhật ký quản trị (FR-42)**: `@AuditedAction(action, targetType, targetId = SpEL, metadata = SpEL)` + `ActivityLogAspect` (`@Around`) — một chỗ thay vì rải lệnh ghi log. **Ghi trong cùng transaction với thao tác**: mặc định advice transaction của Spring Boot và aspect cùng độ ưu tiên thấp nhất → thứ tự bọc không xác định. `TransactionConfig` khai báo lại `@EnableTransactionManagement(order = LOWEST_PRECEDENCE - 1)` để transaction luôn bọc ngoài → log không bao giờ ghi một thao tác đã rollback (vd. 409 optimistic lock lúc commit) và không có thao tác thành công mà thiếu log. Aspect tự kiểm có transaction thật, thiếu thì ném lỗi (cấu hình sai lộ ngay). Đánh đổi: thay một cấu hình tự động của Spring Boot (chỉ để đặt `order`, giữ `proxyTargetClass = true`). Chỉ ghi khi phương thức trả về bình thường (thao tác bị từ chối không phải thao tác đã làm). Actor lấy từ SecurityContext, IP từ request. Không thêm dependency: `aspectjweaver` đã có qua `spring-boot-starter-data-jpa`.
- **Gắn lại D5**: `USER_LOCK` (metadata hạn + lý do), `USER_UNLOCK`, `USER_ASSIGN_ROLES` — `replaceRoles` trả `RoleChange(before, after)` để log có giá trị trước / sau như database.md §3 mô tả. 3 endpoint ghi; `GET /admin/users` là đọc nên không ghi (plan ghi "4 endpoint" là tính gộp).
- **Slug** = tên + thành phố, bỏ dấu (NFD + xóa dấu kết hợp, `đ` đổi tay): "Phở Thìn" ở Hà Nội → `pho-thin-ha-noi`; trùng → `-2`, `-3`… (tìm cả địa điểm đã xóa mềm — UNIQUE tính cả dòng đã xóa). Hai đề xuất cùng tên đồng thời có thể chọn cùng slug → UNIQUE chặn, 409 `DUPLICATE_RESOURCE`; hiếm nên không khóa bảng.
- **Ai xem địa điểm chưa duyệt**: người đề xuất, chủ, kiểm duyệt viên (`place:approve`); người khác **404** như không tồn tại (không lộ đề xuất đang chờ). `rejectReason` chỉ người đề xuất / kiểm duyệt viên thấy. `Viewer` dựng ở controller từ `Authentication` — service không phụ thuộc Spring Security.
- **Luật URL**: thêm `GET /api/v1/places` và `/api/v1/places/*` vào danh sách công khai (`SecurityConfig` — phần lõi, chỉ thêm luật permitAll đúng phạm vi đã chốt). Khai báo trước `GET /places/duplicates` cần đăng nhập — "duplicates" cũng khớp mẫu `{slug}`, không chặn trước thì E4 sẽ vô tình mở công khai.
- **Lượt xem**: upsert nguyên tử `INSERT … ON DUPLICATE KEY UPDATE` theo (địa điểm, ngày giờ Việt Nam), mỗi request +1, chỉ địa điểm APPROVED, **không tính khi chủ tự xem** (không phồng số liệu FR-34). Hạn chế đã biết: chưa lọc bot / F5 liên tục; mỗi lượt xem ghi vào một dòng nóng — đủ ở quy mô đồ án, có thể gom qua Redis nếu cần.
- **`claimable`** = APPROVED + chưa có chủ + người xem chưa có yêu cầu PENDING (UC23 bước 3). **`myReviewId`** đọc native, **kể cả review đã xóa mềm** (D2 — mỗi người một review tính cả review đã xóa).
- **Danh sách `GET /places`**: JPQL dựng động trong fragment `PlaceSearchRepositoryImpl` (chỉ ghép mảnh cố định, giá trị luôn bind — không SQL injection; dễ đối chiếu index hơn Criteria API). Ngữ nghĩa lọc: `categoryId` gồm danh mục con; `amenityIds` **AND** (thêm tiện ích = thu hẹp); `priceMax` = `price_min ≤ priceMax` (có lựa chọn trong ngân sách), chưa khai giá bị loại; `minRating` theo **điểm Bayesian** (điểm hiển thị), không theo trung bình thô; `city` khớp đúng (collation bỏ dấu). Fetch join `category` (ManyToOne) nên LIMIT vẫn chạy ở SQL.
- **Keyset tổ hợp** (`PlaceListCursor`): SCORE = (`bayesian_score`, `id`), MOST_REVIEWED = (`review_count`, `id`), NEWEST = `id`; phụ thêm `id DESC` để đồng điểm không trùng / sót giữa trang. Cursor mang tên thứ tự → dùng nhầm cursor của thứ tự khác bị 422 `INVALID_CURSOR` thay vì trả trang lệch.
- **Hàng chờ `/moderation/places`**: bỏ trống `status` = PENDING, FIFO (cũ nhất trước — như hàng chờ gộp); trạng thái đã xử lý là lịch sử → mới nhất trước.
- **Đồng thời**: hai kiểm duyệt viên cùng duyệt → `@Version` cho người sau 409 `CONCURRENT_MODIFICATION`; client gửi `version` đang thấy thì lệch cũng 409 ngay. Thêm `version` vào `PlaceDetail` (openapi chưa có — đã bổ sung) để frontend gửi lại khi sửa / quyết định.
- **Chủ sửa (PATCH)**: trường vắng = giữ nguyên, chuỗi rỗng xóa mô tả / điện thoại / website, `openingHours` / `amenityIds` thay toàn bộ; không có tên / vị trí / danh mục (openapi). `flush()` trước khi dựng response để `version` mới nằm trong response. Hạn chế: chỉ đổi giờ mở cửa (bảng con) thì `version` không tăng — rủi ro thấp vì mỗi địa điểm chỉ một chủ.
- **Validate**: `priceMin ≤ priceMax` (cả khi PATCH một nửa, đối chiếu giá đang lưu), danh mục / tiện ích phải tồn tại (id lạ báo lỗi, không âm thầm bỏ), giờ mở = giờ đóng bị từ chối (mơ hồ đóng / mở 24 giờ — mở cả ngày ghi 00:00–23:59), website chỉ `http(s)://` (chặn `javascript:` khi hiển thị thành link), thẻ trùng slug gộp làm một. Lỗi gắn theo trường (`openingHours[0].closeTime`…).
- **Chưa làm có chủ đích**: phát event khi duyệt (thông báo người đề xuất, đồng bộ Meilisearch) — chưa có listener nào, thêm cùng mục Thông báo / E5 thay vì để event không ai nghe. Không giới hạn tần suất đề xuất (đặc tả chưa có). Ảnh (`coverPhoto`, `photos`) trả rỗng tới E2.
- MockMvc Spring 7: `MockMultipartHttpServletRequestBuilder` không còn là lớp con của `MockHttpServletRequestBuilder` → helper test nhận lớp cha `AbstractMockHttpServletRequestBuilder<?>`.

**Tài liệu**: openapi (ngữ nghĩa lọc + keyset, `Location` + 401 cho `POST /places`, ai xem chi tiết + lượt xem, 409 / 422 của decision, FIFO hàng chờ, PATCH semantics, `PlaceDetail.version`) — Redocly lint hợp lệ; plan §10 đánh dấu E1 và FR-42.

**Kiểm chứng**: `./mvnw verify` xanh — **152 test** (+29): `PlaceFlowTests` 16 ca qua HTTP trên MySQL thật (đề xuất → PENDING chỉ người đề xuất / kiểm duyệt viên thấy, slug bỏ dấu + hậu tố khi trùng, validate 400 / 401 / 422 theo trường, duyệt công khai + log `PLACE_APPROVE` kèm actor / IP, không quyết định lại, từ chối cần lý do + lý do chỉ người đề xuất thấy, cấm tự duyệt, version lệch 409, member 403 / khách 401, hàng chờ FIFO + lịch sử mới nhất trước, lượt xem + phân bố sao + `myReviewId` gồm review đã xóa + `claimable`, lọc danh mục cây / tiện ích AND / giá / điểm / thành phố + 3 thứ tự, keyset không trùng khi đồng điểm, cursor sai thứ tự 422, `/me/places` mọi trạng thái, chủ sửa + thay giờ / tiện ích + version, chỉ chủ sửa được); `ActivityLogAspectTests` 4 ca (ghi actor / metadata, **log lỗi → thao tác rollback**, thao tác lỗi → không log, thiếu người đăng nhập → lỗi); `AdminUserFlowTests` +1 (lock / unlock / roles ghi log, before / after); `SlugsTest` 8 ca. Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.
- **Sau duyệt**: supervisor duyệt, commit `25be0cc` (nhật ký quản trị) + `c2818c3` (địa điểm), PR #5 đã merge vào `develop`, CI xanh.

---

## 2026-10-05 21:55 (+07) — Checklist F (nền tảng): layout, router, axios + làm mới phiên, store auth; màn P01, đổi mật khẩu, P10

**Bối cảnh**: supervisor yêu cầu bắt đầu phần frontend tương ứng với những gì backend đã làm (D: auth, RBAC, UC31; E1: địa điểm). Nhánh `feature/fe-foundation` tạo từ `develop` sau khi PR #5 merge.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi đợt này = nền tảng + auth + quản trị người dùng**: 3 layout, router + guard theo permission, axios + tự làm mới / 401, store auth, bộ component cơ bản; màn P01 đủ 5 route, `/settings/password`, khung quản trị + P10, trang 403 / 404. **Màn địa điểm để đợt sau**, khi có E3 (`GET /categories`, `/amenities`) — form đề xuất và bộ lọc cần danh sách danh mục / tiện ích.
- **Thư viện đúng plan §2, cài theo nhu cầu**: axios, @tanstack/vue-query, vee-validate + zod, dayjs, vue-toastification. Leaflet cài khi làm bản đồ.
- **O5 (sitemap) — chủ địa điểm sửa thông tin không phải duyệt lại**: giữ như E1 (đã làm theo hướng này mà chưa hỏi — đã báo supervisor). Sitemap đánh dấu O5 ✅.

**Thay đổi backend nhỏ (cần cho frontend):**
- **`MeResponse.permissions`**: danh sách permission gộp từ các role. Sitemap định nghĩa quyền route theo permission (`perm:user:view`), không theo role; frontend tự suy quyền từ tên role sẽ lặp lại bảng `role_permissions` (V2) và lệch khi đổi quyền của role bằng migration. Entity graph đăng nhập / `/me` nạp thêm `roles.permissions` (một truy vấn, không N+1).
- **`AdminUser.emailVerified`**: badge "● Chưa xác thực" của P10.
- openapi cập nhật cả hai trường; test `AuthFlowTests` (permissions của thành viên), `AdminUserFlowTests` (emailVerified). `./mvnw verify` xanh — 152 test, SpotBugs 0 lỗi.

**Quyết định kỹ thuật (frontend):**
- **Access token chỉ trong bộ nhớ** (store), tải lại trang thì khôi phục bằng `POST /auth/refresh` (cookie HttpOnly, S1). Không lưu token vào localStorage — script bị chèn (XSS) không đọc được token. Đánh đổi: mỗi lần mở / tải lại trang tốn một request làm mới.
- **Làm mới một lần cho nhiều request**: backend xoay vòng refresh token và coi việc dùng lại token cũ là bị đánh cắp → thu hồi mọi phiên (U2). Vì vậy (1) các request 401 đồng thời chờ chung một promise làm mới; (2) **khóa giữa các tab bằng Web Locks API** (`navigator.locks`) — hai tab cùng làm mới bằng một cookie sẽ tự đăng xuất người dùng; tab sau chờ tab trước xong, khi đó trình duyệt đã gửi cookie mới. Trình duyệt không hỗ trợ Web Locks thì chạy thẳng (vẫn gộp trong một tab). Đăng xuất báo cho các tab khác qua `BroadcastChannel`.
- **Interceptor axios không import store** (tránh vòng phụ thuộc): store đăng ký cầu nối `connectAuth` ở `main.ts`. Endpoint xác thực công khai (đăng nhập, làm mới, quên mật khẩu…) không mang token và không tự làm mới khi 401 — 401 của đăng nhập là sai mật khẩu. `ACCOUNT_LOCKED` → kết thúc phiên ngay (refresh token đã bị thu hồi khi khóa). Hết phiên: xóa cache Vue Query, thông báo, về `/login?redirect=` nếu đang ở trang cần đăng nhập.
- **Guard theo permission** ở mọi cấp route (`/admin` cần quyền nhân sự bất kỳ, `/admin/users` cần `user:view`); hàm quyết định là hàm thuần `resolveNavigation` để test. `?redirect=` chỉ nhận đường dẫn nội bộ (chặn open redirect `//evil.com`). `/admin` chuyển tới mục sidebar đầu tiên người dùng có quyền (sitemap §2.3).
- **Điều hướng tự cập nhật**: header và sidebar chỉ hiện mục **đã có route và người dùng có quyền** (`router.hasRoute`) — module sau thêm route là mục tự hiện, không có link chết tới trang chưa làm. Hiện tại header chưa có mục nào (Khám phá, Bản đồ… thuộc module địa điểm); sidebar chỉ có "Người dùng & vai trò". Kiểm duyệt viên (không phải admin) vào `/admin` sẽ gặp 403 cho tới khi có hàng chờ.
- **Design system**: port phần cần dùng của `bundle.css` sang `assets/components.css` (giữ tên lớp `ls-*` như README thành phần để đối chiếu), component Vue bọc lớp đó; thêm token khoảng cách `--space-*` vào `tokens.css`. Không dùng màu / cỡ chữ tùy ý trong template (chỗ cần cỡ chữ của P10 / P01 đặt trong CSS scoped, lấy đúng giá trị thiết kế). vue-toastification ghi đè kiểu: nền `surface-raised`, viền 1px, không bóng, trạng thái bằng ký hiệu ✓ ✕ ● + vạch màu.
- **Form**: VeeValidate + **Zod 3** + `@vee-validate/zod` — VeeValidate 4.15 chưa hỗ trợ Zod 4 (thử cài Zod 4: không có Standard Schema trong vee-validate). Luật mật khẩu đồng bộ `@ValidPassword` (≥ 8, chữ + số, ≤ 72 byte). Kiểm khi rời ô / khi gửi, không báo lỗi lúc đang gõ. Lỗi 422 của backend gắn vào đúng trường; trường backend báo mà form không có → gộp vào lỗi chung (không để lỗi biến mất).
- **Đăng ký → đăng nhập luôn → "Kiểm tra hộp thư"**: gửi lại mail xác thực cần đăng nhập (`/auth/resend-verification`), và người dùng không phải gõ lại mật khẩu. Nút gửi lại chờ 45 giây (P01) + tôn trọng `Retry-After` của 429. Thành viên chưa xác thực thấy dải nhắc dưới header kèm nút gửi lại.
- **Đăng nhập sai**: một câu chung trên ô mật khẩu, không nói sai email hay mật khẩu (P01). Đặt lại mật khẩu thành công → bỏ phiên ở trình duyệt (backend đã thu hồi mọi phiên) và về đăng nhập.
- **P10 khác bản thiết kế, có lý do**: (1) vai trò dùng **ô chọn** thay radio — một tài khoản giữ được nhiều vai trò (vd. chủ địa điểm kiêm kiểm duyệt viên) và API nhận cả danh sách; USER luôn chọn và khóa, OWNER chỉ hiển thị (O6 — chỉ cấp qua UC30), admin không tự gỡ ADMIN của mình — luật tách thành hàm thuần `roleRules.ts`, khớp `AdminUserService`; (2) cột "Đánh giá" đổi thành **"Điểm tin cậy"** — API `AdminUser` chưa có số đánh giá (module review chưa làm); (3) phân trang **"Tải thêm"** thay số trang — API cursor không trả tổng số (không COUNT bảng users mỗi lần lọc); (4) bộ lọc trạng thái chỉ Hoạt động / Đã khoá — API chưa lọc theo xác thực email; (5) form khóa (thời hạn 1 ngày – 1 năm + lý do bắt buộc) mở ngay trong bảng chi tiết, không hộp thoại (đúng tinh thần P10).
- **Bỏ khỏi P01**: ô "Ghi nhớ đăng nhập" (backend không có chế độ này — phiên luôn 7 ngày), ô "Đồng ý quy tắc cộng đồng" (chưa có trang quy tắc), liên kết "Đổi địa chỉ email" (chưa có API).
- **Theme Sáng / Tối**: mặc định theo hệ điều hành, nút chuyển ở header / chân sidebar, lựa chọn nhớ trong localStorage (tuỳ chọn hiển thị, không phải dữ liệu nhạy cảm).
- Vue Query: chỉ thử lại lỗi mạng / 5xx (tối đa 1 lần); đổi người đăng nhập thì xóa toàn bộ cache.

**Kiểm chứng**:
- `npm run lint`, `format:check`, `type-check`, `build` sạch; **Vitest 31 test** (7 file): interceptor (gắn token, 3 request 401 đồng thời → một lần làm mới thật rồi gửi lại, làm mới thất bại → hết phiên, endpoint đăng nhập không mang token / không làm mới, tài khoản khóa → hết phiên ngay); chuẩn hoá lỗi RFC 7807 + tách lỗi trường / lỗi chung; guard (khách → login kèm redirect, chặn open redirect, quyền ở mọi cấp route); store (gộp làm mới, khôi phục thất bại → khách, đăng xuất luôn xóa phiên); luật vai trò + trạng thái P10; `LoginPage` (validate trước khi gọi API, sai thông tin, 429, quay về `?redirect=`).
- **Chạy thật** (backend profile dev + Vite proxy, Chrome headless qua DevTools Protocol): đăng nhập qua proxy Vite nhận cookie `refresh_token` HttpOnly + Secure + `Path=/api/v1/auth`; đăng nhập sai hiện lỗi đúng ô; đăng nhập admin → `/admin` tự chuyển `/admin/users`; **tải lại trang vẫn giữ phiên** (khôi phục bằng cookie); chụp màn P01, P10 (Sáng + Tối), Cài đặt mật khẩu — khớp thiết kế. Chưa thử thao tác ghi (gán vai trò, khóa) trên CSDL dev để không đổi dữ liệu seed — đã có test backend `AdminUserFlowTests`.

Chưa commit — chờ supervisor duyệt.

---

## 2026-10-06 10:40 (+07) — Dựng môi trường trên máy Ubuntu mới

**Bối cảnh**: supervisor chuyển sang máy Ubuntu 22.04 mới (không phải WSL). Đã có Docker 29 + Compose v5; thiếu JDK 21 (có JDK 17), Node 24; MySQL 8.0 của hệ điều hành đang giữ cổng 3306.

**Đã làm**: cài JDK 21 (apt, gỡ JDK 17), Node 24 qua nvm; `.env` từ `.env.example` với bí mật ngẫu nhiên; hạ tầng `docker compose up -d`; backend dev + `./mvnw verify` (152 test) và kiểm tra frontend (lint, format, type-check, 31 test, build) đều xanh.

**Supervisor chốt:**
- **Cổng 3306 bị chiếm** → chỉ đổi trên máy này, không commit: `docker-compose.override.yml` (loại khỏi git bằng `.git/info/exclude`) đổi MySQL sang **3307**; backend dev chạy với `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/localspot`.
- **MinIO tạm thời trên máy này**: `quay.io/minio/minio` và `minio/minio` **không còn tải được** (MinIO ngừng phát hành image cộng đồng; lỗi `unauthorized` kể cả không đăng nhập) → `docker compose up -d` đầy đủ lỗi ở bước pull. Override cục bộ dùng `cgr.dev/chainguard/minio:latest` (`user: root` để ghi được volume). **Còn mở**: sửa `docker-compose.yml` cho cả nhóm / CI — phương án: build từ mã nguồn, image thay thế, hoặc đổi sang kho tương thích S3 khác (ảnh hưởng mục công nghệ trong báo cáo → cần supervisor quyết).

**Ghi chú**: lần `npm ci` đầu bỏ sót `axios`, `vue-toastification` trong `node_modules` (type-check vẫn qua, test / build lỗi) — chạy lại `npm ci` là đủ.

---

## 2026-10-06 11:31 (+07) — Checklist E2: Upload ảnh địa điểm, resize, nén, lưu S3 qua queue

**Bối cảnh**: mục kế tiếp của checklist E sau E1 (PR #5) và F nền tảng (PR #6) đã merge vào `develop`. Nhánh `feature/be-place-photos`. Thư viện theo plan §2: AWS S3 SDK v2, Thumbnailator.

**Supervisor chốt (qua câu hỏi):**
- **URL ảnh = bucket đọc công khai, URL ổn định** (không presigned, không proxy qua backend): trình duyệt / CDN cache được, response địa điểm không phải ký URL mỗi lần. Đánh đổi: ai có URL đều mở được, kể cả ảnh của địa điểm chưa duyệt — key chứa UUID ngẫu nhiên nên không đoán được.
- **Tối đa 30 ảnh / địa điểm** (ngoài 10 ảnh / request của openapi), cấu hình trong `application.yml`.
- **Chỉ nhận JPEG / PNG** (openapi cũ ghi cả WebP): ImageIO / Thumbnailator không đọc được WebP / HEIC; frontend crop bằng canvas rồi xuất JPEG nên ảnh WebP / HEIC từ điện thoại vẫn qua được giao diện. Không thêm TwelveMonkeys. Đã sửa openapi + NFR-09.

**Quyết định kỹ thuật:**
- **Luồng**: request kiểm tra cả lô (một ảnh hỏng → 422 `INVALID_IMAGE` đúng trường `photos[i]`, không tạo gì) → ảnh gốc lên `incoming/places/{placeId}/{uuid}` (riêng tư — còn EXIF / GPS) + bản ghi `PROCESSING` → **sau commit** đẩy id vào queue `photo.process` → consumer sinh 3 bản JPEG `-thumb` (320) / `-medium` (960) / `-large` (1920 px cạnh dài, không phóng to), q = 0.8 → `READY`, lưu `width/height` của bản large → xóa ảnh gốc. Message chỉ mang id, không đưa vài MB nhị phân qua RabbitMQ.
- **Kiểm tra upload (NFR-09)** theo chữ ký file (không tin `Content-Type`), đuôi tệp phải khớp định dạng thật, ≤ 5 MB, **≤ 40 MP đọc từ header** (chặn ảnh "bom giải nén" mà không giải mã trong request). Giới hạn request 55 MB khớp nginx → vượt là 413.
- **Gỡ EXIF không cần metadata-extractor**: Thumbnailator tự xoay theo EXIF Orientation khi đọc, ảnh đầu ra là JPEG ghi mới nên không mang EXIF nào (GPS — NFR-11). Bỏ metadata-extractor khỏi plan §2 vì không còn việc. PNG trong suốt vẽ lên nền trắng.
- **Lỗi & thử lại (NFR-13)**: ảnh không giải mã được → `FAILED` ngay, không retry; kho object lỗi → ném ra, listener retry 4 lần rồi sang `photo.process.dlq` (ảnh giữ PROCESSING tới khi chạy lại message bằng tay — giống mail). Xử lý lặp lại an toàn (bỏ qua ảnh không còn PROCESSING). Consumer **không giữ transaction** trong lúc đọc kho / resize — chỉ mở transaction ngắn để đọc / cập nhật trạng thái.
- **Nhất quán CSDL ↔ kho**: ảnh gốc lên kho ngay trong transaction của request (giữ kết nối CSDL lúc upload — chấp nhận ở quy mô đồ án, đổi lại không cần kho tạm thứ hai). `PlacePhotosStoredEvent`: commit → đẩy queue; **rollback → xóa ảnh gốc** vừa upload. Xóa ảnh: xóa bản ghi, object xóa **sau commit**. Kho sập lúc upload → 503, không tạo địa điểm. Không có outbox: RabbitMQ sập đúng lúc sau commit → ảnh kẹt PROCESSING (ghi log).
- **Giới hạn 30 ảnh khi upload đồng thời**: khóa dòng địa điểm (`SELECT … FOR UPDATE`) trước khi đếm — hai request không cùng thấy "còn chỗ". Ảnh `FAILED` không tính.
- **Ảnh bìa**: ảnh đầu tiên của địa điểm chưa có bìa; bìa bị xóa / xử lý lỗi → ảnh chưa lỗi kế tiếp theo `sort_order`. Chủ tự chọn bìa không có trong openapi — để sau nếu cần.
- **Ai thấy ảnh nào**: người ngoài chỉ thấy READY; người đề xuất / chủ / kiểm duyệt viên thấy cả PROCESSING / FAILED (kèm `status`, URL rỗng). Ảnh bìa trên thẻ danh sách nạp một truy vấn cho cả trang (`PlaceSummaries`) — không N+1.
- **Tách `ObjectStorage`** khỏi S3 SDK: service không phụ thuộc nhà cung cấp; test dùng bản trong bộ nhớ — CI không cần container MinIO (image chính thức không tải được). `S3ObjectStorage` là lớp chuyển tiếp mỏng, kiểm tra tay với MinIO dev. Client HTTP `url-connection` (bỏ Netty / Apache client không dùng); checksum CRC chỉ gửi khi API bắt buộc (R2 / MinIO cũ từ chối checksum mới của SDK ≥ 2.30).
- **Bucket**: dev tự tạo bucket + policy đọc công khai chỉ cho `places/*`, `reviews/*` (`localspot.storage.create-bucket`); kho chưa chạy chỉ cảnh báo, không chặn khởi động. Deploy: bucket tạo bằng hạ tầng; R2 không có bucket policy — public qua custom domain (khi đó `incoming/` cũng công khai nếu biết key; ngắn hạn, key ngẫu nhiên — cân nhắc bucket riêng cho ảnh gốc khi deploy).
- **`docker-compose.yml` (phần lõi — chỉ thêm)**: backend profile `app` nhận `STORAGE_*`, mặc định trỏ MinIO trong compose — thiếu thì backend prod không khởi động. `.env.example` thêm mẫu cấu hình R2.

**Còn để lại**: ảnh review dùng lại pipeline ở mục "Ảnh đính kèm review"; dọn ảnh kẹt PROCESSING / object rác định kỳ (chưa có job); giới hạn tần suất upload (đặc tả chưa có).

**Tài liệu**: openapi (định dạng / giới hạn ảnh, response 201 của chủ thêm ảnh, 404 / 413 / 422 / 503, mô tả `Photo`), requirements NFR-09, plan §2 + §10, database.md (`storage_key`, `is_cover`, `width/height`).

**Kiểm chứng**:
- `./mvnw verify` xanh — **170 test** (+18): `PlacePhotoFlowTests` 9 ca qua HTTP + RabbitMQ thật (đề xuất kèm ảnh → READY qua queue, URL đúng, không EXIF, ảnh gốc bị xóa, kích thước sau khi xoay, công khai sau duyệt + bìa trên thẻ danh sách; một ảnh hỏng → 422 không tạo gì; 11 ảnh → 422; kho sập → 503 không tạo gì; chủ thêm / người khác 403 / khách 401; giới hạn 30 không tính ảnh lỗi; xóa bìa → chuyển bìa + xóa object, ảnh của địa điểm khác 404; khách chỉ thấy READY; ảnh không giải mã được → FAILED + mất bìa, message lặp không làm gì); `ImageResizerTest` 4 ca, `ImageInspectorTest` 5 ca (gồm "bom giải nén" 20000 × 20000 chỉ bằng header PNG). Spotless, SpotBugs 0 lỗi.
- **Chạy thật với MinIO dev**: đề xuất kèm JPEG 3000 × 2000 (EXIF Orientation 6 + GPS) và PNG trong suốt → READY trong < 3 giây; 6 bản ảnh tải công khai được (200), bản large 1280 × 1920 đúng chiều, 0 thẻ EXIF; PNG ra nền trắng, không phóng to; ảnh gốc `incoming/` không đọc công khai được (403); địa điểm PENDING trả 404 cho khách.

---

## 2026-10-06 16:06 (+07) — Checklist E3: Danh mục phân cấp, tiện ích (giờ mở cửa đã có từ E1)

**Bối cảnh**: mục kế tiếp sau E2 (lúc bắt đầu E2 còn chờ duyệt — nay đã merge, PR #7; nhánh này rebase lên `develop` mới). E3 không phụ thuộc E2 → nhánh `feature/be-catalog` tách từ `develop` để hai PR duyệt độc lập (hai nhánh cùng thêm vào cuối `ErrorCode`, `plan-v1.md`, file log này — xung đột nhỏ khi merge PR thứ hai). Giờ mở cửa đã làm ở E1 (đề xuất, chủ sửa, chi tiết) — phần còn lại của E3 là API danh mục / tiện ích (FR-39, UC32).

**Supervisor chốt (qua câu hỏi):**
- **Cây danh mục tối đa 2 cấp** (gốc → con, như seed và P11): danh mục con chỉ gắn vào gốc; danh mục đang có con không được làm con; không tự làm cha. Không cần kiểm tra vòng lặp; bộ lọc "danh mục gồm cả danh mục con" của E1 (`c.id = :id OR c.parent.id = :id`) đúng với giới hạn này.
- **O7 — tiện ích dùng chung** cho mọi danh mục: giữ schema, không migration. Sitemap đánh dấu O7 ✅.

**Quyết định kỹ thuật:**
- **API**: `GET /categories` (cây), `GET /amenities` công khai (thêm luật `permitAll` đúng 2 đường dẫn trong `SecurityConfig` — openapi `security: []`); `/admin/categories`, `/admin/amenities` POST / PUT / DELETE với `category:manage` / `amenity:manage` (chỉ admin — Q2). PUT thay toàn bộ (`parentId` rỗng = gốc, `icon` rỗng = bỏ). Mỗi thao tác ghi nhật ký FR-42: `CATEGORY_CREATE/UPDATE/DELETE`, `AMENITY_CREATE/UPDATE/DELETE` (kèm slug).
- **Xóa chỉ khi không còn dùng** (openapi): danh mục còn con / còn địa điểm, tiện ích còn gắn địa điểm → 409 `CATEGORY_IN_USE` / `AMENITY_IN_USE`, **tính cả địa điểm đã xóa mềm** (truy vấn native — `@SQLRestriction` ẩn chúng). Riêng tiện ích: khóa ngoại `place_amenity` là `ON DELETE CASCADE`, không chặn ở service thì xóa tiện ích sẽ **âm thầm gỡ nó khỏi mọi địa điểm** — có test riêng.
- **Slug do quản trị viên nhập** (openapi `pattern ^[a-z0-9-]+$`), trùng → 409 `SLUG_TAKEN` theo trường `slug` (kiểm trước, UNIQUE ở CSDL vẫn là chốt cuối).
- **Cache — Spring Cache + Redis** (plan §2; thêm `spring-boot-starter-cache`, thuộc Spring Boot): mỗi cache serializer JSON đúng kiểu (`List<CategoryNode>`, `List<AmenityResponse>`), prefix `localspot:`, TTL 1 giờ chỉ là lưới an toàn. **Redis lỗi không làm hỏng request** (`CacheErrorHandler` chỉ ghi log → đọc CSDL), như rate limit D8. Cache trang chủ để mục G.
- **Lỗi tìm ra khi chạy cả bộ test — xóa cache bất đồng bộ**: bản đầu dùng `@CacheEvict(allEntries = true)` + `transactionAware()`; chạy riêng thì qua, chạy cả bộ thì test "sửa xong thấy ngay" thỉnh thoảng đọc cây cũ. Truy nguyên (trace `CacheInterceptor` + soi bytecode): ở Spring Data Redis 4, `RedisCache.clear()` **ghi bất đồng bộ** (trả về không chờ Redis), còn `invalidate()` đồng bộ; `@CacheEvict(allEntries)` gọi `clear()`. Dùng `beforeInvocation = true` (gọi `invalidate()`) cũng sai: Spring 7 `TransactionAwareCacheDecorator.invalidate()` xóa ngay, **trước commit** → request đọc chen giữa nạp lại dữ liệu cũ. Sửa: service phát `CatalogChangedEvent`, `CatalogCacheInvalidator` (`@TransactionalEventListener(AFTER_COMMIT)`) gọi `invalidate()` — cùng mẫu với `MailQueuePublisher`. Rollback → không xóa (cache vẫn đúng).

**Tài liệu**: openapi (mô tả cây ≤ 2 cấp / cache, 404 / 409 / 422 của admin, `icon` ≤ 100, ngữ nghĩa PUT), requirements FR-39, sitemap O7 ✅, plan §10 E3, database.md `categories.parent_id`.

**Kiểm chứng**:
- `./mvnw verify` xanh — **159 test** (+7, tính trên `develop`): `CatalogFlowTests` qua HTTP trên MySQL + Redis thật — cây 2 cấp + tiện ích công khai; tạo / sửa (chuyển cha) / xóa danh mục **thấy ngay qua API công khai dù đã nạp cache** + nhật ký đúng người; luật 2 cấp (cháu, gốc có con, tự làm cha, cha không tồn tại; chuyển hết con rồi gốc làm con được); slug trùng 409 / sai định dạng 422 / 404; danh mục còn con / còn địa điểm (kể cả xóa mềm) 409; tiện ích tạo / sửa / trùng slug / đang dùng 409 (liên kết không bị CASCADE mất) / xóa / 404; member + kiểm duyệt viên 403, khách 401. Spotless, SpotBugs 0 lỗi.
- **Kiểm tra test có bắt lỗi**: bỏ việc xóa cache khi tạo danh mục → test cache đỏ (cả cơ chế cũ lẫn mới). Tổ hợp `PlaceFlowTests` + `CatalogFlowTests` từng làm lộ lỗi chạy 3 lần liên tiếp đều xanh.
- **Chạy thật (dev)**: `GET /categories` 3 gốc (6 / 3 / 4 con), `GET /amenities` 14 mục; Redis có `localspot:categories::SimpleKey []`, `localspot:amenities::…` dạng JSON, TTL 3600. Không thử ghi trên CSDL dev để không thêm dữ liệu — đã có test.

Chưa commit — chờ supervisor duyệt.
