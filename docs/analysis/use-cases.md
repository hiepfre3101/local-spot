# Use case — LocalSpot

> Trạng thái: **Đã chốt** (2026-09-27) — toàn bộ luật nghiệp vụ U1–U10 ở mục 4 đã được supervisor xác nhận và chép vào [requirements.md](../requirements.md) §5.
> Sơ đồ: [use-case-overview.puml](../diagrams/use-case-overview.puml). Dùng cho Chương 3 báo cáo (sơ đồ use case tổng quát + đặc tả chi tiết).

---

## 1. Tác nhân

| Tác nhân | Kế thừa | Ghi chú |
|---|---|---|
| Khách vãng lai | — | Chưa đăng nhập |
| Thành viên | Khách vãng lai | Role `user` |
| Chủ địa điểm | Thành viên | Role `owner`, chỉ thao tác trên địa điểm mình sở hữu |
| Kiểm duyệt viên | — | Role `moderator` |
| Quản trị viên | Kiểm duyệt viên | Role `admin` |

Tác vụ hệ thống (tính rating Bayesian FR-22, ghi nhật ký quản trị FR-42, xử lý ảnh, đồng bộ index) không vẽ thành use case riêng vì không có tác nhân người khởi phát; chúng xuất hiện như bước bên trong các use case bên dưới và được mô tả ở sơ đồ tuần tự/hoạt động.

---

## 2. Danh sách use case

| Mã | Tên | Tác nhân chính | FR | Đặc tả chi tiết |
|---|---|---|---|---|
| UC01 | Đăng ký | Khách | FR-01 | ✅ §3.1 (gộp UC02) |
| UC02 | Xác thực email | Thành viên | FR-02 | ✅ §3.1 |
| UC03 | Đăng nhập | Khách | FR-03 | ✅ §3.2 |
| UC04 | Đăng xuất / làm mới phiên | Thành viên | FR-04 | |
| UC05 | Khôi phục mật khẩu | Khách | FR-05 | |
| UC06 | Quản lý hồ sơ, đổi mật khẩu | Thành viên | FR-06, FR-07 | |
| UC07 | Xem trang chủ | Khách | FR-08 | |
| UC08 | Tìm kiếm & lọc địa điểm | Khách | FR-09, FR-10 | ✅ §3.3 |
| UC09 | Tìm quanh vị trí trên bản đồ | Khách | FR-11, FR-12 | ✅ §3.4 |
| UC10 | Xem chi tiết địa điểm | Khách | FR-13, FR-14 | |
| UC11 | Đề xuất địa điểm mới | Thành viên | FR-15, FR-16 | ✅ §3.5 |
| UC12 | Viết đánh giá | Thành viên | FR-17, FR-19, FR-22, FR-23 | ✅ §3.6 |
| UC13 | Sửa / xóa đánh giá | Thành viên | FR-18 | |
| UC14 | Vote hữu ích | Thành viên | FR-20 | |
| UC15 | Bình luận đánh giá | Thành viên | FR-21 | |
| UC16 | Check-in | Thành viên | FR-24 | ✅ §3.7 |
| UC17 | Báo cáo vi phạm | Thành viên | FR-25 | |
| UC18 | Quản lý bộ sưu tập | Thành viên | FR-26 | |
| UC19 | Xem thông báo | Thành viên | FR-27 | |
| UC20 | Theo dõi người dùng | Thành viên | FR-28 | |
| UC21 | Xem bảng xếp hạng, huy hiệu | Thành viên | FR-29 | |
| UC22 | Gắn thẻ địa điểm | Thành viên | FR-30 | |
| UC23 | Yêu cầu xác nhận sở hữu | Thành viên | FR-31 | ✅ §3.8 |
| UC24 | Cập nhật thông tin địa điểm | Chủ địa điểm | FR-32 | |
| UC25 | Phản hồi đánh giá | Chủ địa điểm | FR-33 | |
| UC26 | Xem thống kê địa điểm | Chủ địa điểm | FR-34 | |
| UC27 | Duyệt địa điểm | Kiểm duyệt viên | FR-35 | |
| UC28 | Duyệt đánh giá trong hàng chờ | Kiểm duyệt viên | FR-36 | ✅ §3.9 |
| UC29 | Xử lý báo cáo vi phạm | Kiểm duyệt viên | FR-37 | ✅ §3.10 |
| UC30 | Duyệt yêu cầu sở hữu | Kiểm duyệt viên | FR-40 | |
| UC31 | Quản lý người dùng & vai trò | Quản trị viên | FR-38 | |
| UC32 | Quản lý danh mục & tiện ích | Quản trị viên | FR-39 | |
| UC33 | Xem dashboard thống kê | Quản trị viên | FR-41 | |

**Tiêu chí chọn 10 use case đặc tả chi tiết**: bao phủ đủ 5 tác nhân và cả 4 điểm khác biệt cốt lõi — chống review ảo (UC12, UC28, UC16), Bayesian (UC12, UC28), tìm kiếm tiếng Việt (UC08), truy vấn không gian (UC09, UC16) — cộng luồng xác thực (UC01, UC03) mà hội đồng hay hỏi.

---

## 3. Đặc tả chi tiết

### 3.1 UC01 — Đăng ký (gồm UC02 Xác thực email)

| Mục | Nội dung |
|---|---|
| Tác nhân | Khách vãng lai |
| FR | FR-01, FR-02 |
| Mô tả | Khách tạo tài khoản bằng email + mật khẩu, sau đó xác thực email để được viết đánh giá |
| Tiền điều kiện | Chưa đăng nhập |
| Hậu điều kiện | Tài khoản được tạo với role `user`, `email_verified_at = null`; sau khi xác thực: `email_verified_at` được gán, `trust = base = 10` |

**Luồng chính**
1. Khách mở trang Đăng ký, nhập tên hiển thị, email, mật khẩu, xác nhận mật khẩu.
2. Hệ thống kiểm tra dữ liệu hợp lệ (định dạng email, độ mạnh mật khẩu, hai mật khẩu khớp).
3. Hệ thống kiểm tra email chưa tồn tại.
4. Hệ thống băm mật khẩu (BCrypt), tạo tài khoản, gán role `user`.
5. Hệ thống sinh token xác thực email có hạn dùng và đẩy tác vụ gửi mail vào hàng đợi.
6. Hệ thống trả về thông báo "Kiểm tra email để xác thực tài khoản".
7. Người dùng bấm link trong email.
8. Hệ thống kiểm tra token hợp lệ, chưa hết hạn, chưa dùng; gán `email_verified_at`, vô hiệu token.
9. Hệ thống thông báo xác thực thành công.

**Luồng thay thế**
- 3a. Email đã tồn tại → báo "Email đã được sử dụng", quay lại bước 1.
- 7a. Người dùng không nhận được mail → yêu cầu gửi lại link (giới hạn tần suất, NFR-10); token cũ bị vô hiệu.

**Ngoại lệ**
- 2a. Dữ liệu không hợp lệ → trả lỗi 422 theo từng trường (RFC 7807).
- 8a. Token hết hạn hoặc đã dùng → báo lỗi, cho phép gửi lại link.
- 5a. Gửi mail thất bại → tác vụ được retry qua hàng đợi (NFR-13); đăng ký vẫn thành công.

**Quy tắc nghiệp vụ**
- Mật khẩu tối thiểu 8 ký tự, có chữ và số.
- Link xác thực hết hạn sau 24 giờ.
- Chưa xác thực email vẫn đăng nhập được, xem được nội dung, nhưng **không được viết đánh giá** (FR-02).

---

### 3.2 UC03 — Đăng nhập

| Mục | Nội dung |
|---|---|
| Tác nhân | Khách vãng lai |
| FR | FR-03, NFR-06, NFR-10 |
| Mô tả | Khách đăng nhập bằng email + mật khẩu, nhận cặp access token / refresh token |
| Tiền điều kiện | Tài khoản tồn tại, không bị khóa |
| Hậu điều kiện | Access token (15 phút) trả về trong body; refresh token (7 ngày) được lưu dạng băm ở server |

**Luồng chính**
1. Khách nhập email, mật khẩu.
2. Hệ thống kiểm tra giới hạn số lần thử đăng nhập.
3. Hệ thống tìm tài khoản theo email, so khớp mật khẩu với bản băm.
4. Hệ thống kiểm tra tài khoản không bị khóa.
5. Hệ thống sinh access token (JWT chứa `userId`, danh sách role) và refresh token ngẫu nhiên; lưu bản băm refresh token.
6. Hệ thống trả về token và thông tin người dùng; frontend lưu vào auth store.

**Luồng thay thế**
- 6a. Access token hết hạn khi đang dùng → frontend gọi UC04 làm mới bằng refresh token (xoay vòng: cấp refresh token mới, vô hiệu token cũ).

**Ngoại lệ**
- 2a. Vượt ngưỡng thử → 429 Too Many Requests.
- 3a. Sai email hoặc mật khẩu → 401 với thông báo chung "Email hoặc mật khẩu không đúng" (không tiết lộ email có tồn tại hay không).
- 4a. Tài khoản bị khóa → 403 kèm lý do.

**Quy tắc nghiệp vụ**
- Giới hạn 5 lần thử / 15 phút theo cặp (email, IP).
- Refresh token bị dùng lại sau khi đã xoay vòng → coi là bị đánh cắp, thu hồi toàn bộ refresh token của người dùng.

---

### 3.3 UC08 — Tìm kiếm & lọc địa điểm

| Mục | Nội dung |
|---|---|
| Tác nhân | Khách vãng lai (và mọi tác nhân kế thừa) |
| FR | FR-09, FR-10, NFR-02 |
| Mô tả | Tìm địa điểm theo từ khóa tiếng Việt (có dấu / không dấu / sai chính tả nhẹ) kết hợp bộ lọc và sắp xếp |
| Tiền điều kiện | Không |
| Hậu điều kiện | Danh sách địa điểm đã duyệt, phân trang |

**Luồng chính**
1. Người dùng nhập từ khóa (ví dụ "bun cha", "cafe view dep").
2. Hệ thống gửi truy vấn tới Meilisearch kèm bộ lọc (danh mục, khoảng giá, tiện ích, điểm sao tối thiểu).
3. Meilisearch trả về danh sách ID địa điểm theo độ liên quan (typo-tolerance, bỏ dấu, synonym).
4. Hệ thống nạp thông tin tóm tắt (tên, ảnh đại diện, điểm Bayesian, số review, địa chỉ) và trả về.
5. Người dùng đổi bộ lọc / sắp xếp → lặp lại từ bước 2; cuộn xuống → tải trang tiếp theo (infinite scroll).

**Luồng thay thế**
- 1a. Không nhập từ khóa → chỉ áp dụng bộ lọc, sắp xếp mặc định theo điểm Bayesian.
- 2a. Người dùng bật lọc "khoảng cách" → chuyển sang UC09 (extend).

**Ngoại lệ**
- 3a. Không có kết quả → hiển thị trạng thái rỗng kèm gợi ý bỏ bớt bộ lọc.
- 3b. Meilisearch không phản hồi → hệ thống dự phòng bằng truy vấn MySQL `LIKE` trên tên (plan §11 "làm bản đơn giản trước"), ghi log cảnh báo.

**Quy tắc nghiệp vụ**
- Chỉ trả về địa điểm `status = approved`, chưa xóa mềm.
- Index được đồng bộ khi địa điểm được duyệt / cập nhật / xóa và khi điểm xếp hạng thay đổi (qua hàng đợi).

---

### 3.4 UC09 — Tìm quanh vị trí trên bản đồ

| Mục | Nội dung |
|---|---|
| Tác nhân | Khách vãng lai |
| FR | FR-11, FR-12, NFR-03 |
| Mô tả | Hiển thị địa điểm trong bán kính quanh vị trí người dùng hoặc tâm bản đồ |
| Tiền điều kiện | Không |
| Hậu điều kiện | Danh sách + marker trên bản đồ, sắp xếp theo khoảng cách |

**Luồng chính**
1. Người dùng bấm "Gần tôi".
2. Trình duyệt xin quyền vị trí; người dùng đồng ý.
3. Frontend gửi `GET /places/nearby?lat&lng&radius`.
4. Hệ thống lọc thô bằng bounding box trên spatial index (`MBRContains`), sau đó lọc chính xác bằng `ST_Distance_Sphere ≤ radius`.
5. Hệ thống trả về danh sách kèm khoảng cách, sắp xếp tăng dần.
6. Frontend vẽ marker (có cluster khi dày đặc) và danh sách bên cạnh.

**Luồng thay thế**
- 2a. Người dùng từ chối quyền vị trí → dùng tâm bản đồ hiện tại (mặc định trung tâm Hà Nội) làm điểm gốc.
- 6a. Người dùng kéo/zoom bản đồ → nút "Tìm trong khu vực này" gửi lại truy vấn với tâm mới.

**Ngoại lệ**
- 3a. `radius` vượt giới hạn → 422.

**Quy tắc nghiệp vụ**
- Bán kính mặc định 2 km, tối đa 20 km.
- Tọa độ lưu SRID 4326; lưu ý thứ tự trục (lat, lng) của MySQL 8 với SRID 4326.

---

### 3.5 UC11 — Đề xuất địa điểm mới

| Mục | Nội dung |
|---|---|
| Tác nhân | Thành viên |
| FR | FR-15, FR-16 |
| Mô tả | Thành viên đóng góp địa điểm chưa có trong hệ thống; địa điểm chờ kiểm duyệt viên duyệt |
| Tiền điều kiện | Đã đăng nhập |
| Hậu điều kiện | Địa điểm `status = pending`, người đề xuất nhận thông báo khi có kết quả duyệt |

**Luồng chính**
1. Thành viên mở form đề xuất.
2. Thành viên nhập tên, danh mục, địa chỉ, khoảng giá, giờ mở cửa, tiện ích, mô tả.
3. Thành viên chọn vị trí trên bản đồ (kéo marker) hoặc dùng vị trí hiện tại.
4. Thành viên tải ảnh (tùy chọn — extend "Upload ảnh").
5. Hệ thống kiểm tra dữ liệu hợp lệ.
6. Hệ thống kiểm tra trùng lặp.
7. Hệ thống lưu địa điểm `status = pending`, sinh `slug`; ảnh được đưa vào hàng đợi xử lý (resize, nén, gỡ EXIF).
8. Hệ thống thông báo "Đã gửi, chờ duyệt".

**Luồng thay thế**
- 6a. Có địa điểm tên gần giống trong bán kính 50 m → hiển thị danh sách nghi trùng; thành viên chọn "Đây là địa điểm khác" để tiếp tục hoặc hủy.

**Ngoại lệ**
- 5a. Thiếu trường bắt buộc / tọa độ không hợp lệ → 422.
- 7a. Xử lý ảnh thất bại → retry; sau số lần tối đa, ảnh bị đánh dấu lỗi, địa điểm vẫn được lưu.

**Quy tắc nghiệp vụ**
- Địa điểm `pending` không xuất hiện trong tìm kiếm / bản đồ; người đề xuất xem được trong trang cá nhân.
- Kết quả duyệt xử lý ở UC27.

---

### 3.6 UC12 — Viết đánh giá

| Mục | Nội dung |
|---|---|
| Tác nhân | Thành viên |
| FR | FR-17, FR-19, FR-22, FR-23 |
| Mô tả | Thành viên chấm sao và viết đánh giá; hệ thống áp dụng cơ chế chống review ảo trước khi công khai |
| Tiền điều kiện | Đã đăng nhập, **đã xác thực email**, địa điểm `approved` |
| Hậu điều kiện | Review `published` (và điểm Bayesian được tính lại) hoặc `pending` (vào hàng chờ UC28) |

**Luồng chính**
1. Thành viên mở trang địa điểm, bấm "Viết đánh giá".
2. Thành viên chấm 1–5 sao, nhập nội dung, ngày đã đến, đính kèm ảnh (tùy chọn, crop phía client).
3. Hệ thống kiểm tra dữ liệu hợp lệ.
4. Hệ thống kiểm tra thành viên chưa có review cho địa điểm này.
5. Hệ thống kiểm tra rate limit: chưa vượt 5 review trong 24 giờ.
6. Hệ thống tính trust score hiện tại của thành viên (docs/requirements.md §5.1).
7. Trust ≥ 30 và không bị cảnh báo IP → lưu review `status = published`.
8. Hệ thống phát `ReviewCreatedEvent`; listener bất đồng bộ tính lại điểm Bayesian của địa điểm, đồng bộ index tìm kiếm, gửi thông báo cho chủ địa điểm (nếu có).
9. Ảnh đưa vào hàng đợi xử lý.
10. Hệ thống hiển thị review trên trang địa điểm.

**Luồng thay thế**
- 7a. Trust < 30 → lưu `status = pending`, thông báo "Đánh giá đang chờ kiểm duyệt"; **không** tính lại rating cho đến khi được duyệt (UC28).
- 7b. Cảnh báo IP: địa điểm đã nhận ≥ 3 review từ cùng dải /24 trong 24 giờ → review bị đưa vào hàng chờ bất kể trust, gắn cờ "nghi ngờ IP" cho kiểm duyệt viên.

**Ngoại lệ**
- 1a. Chưa xác thực email → 403, gợi ý gửi lại email xác thực.
- 3a. Dữ liệu không hợp lệ (sao ngoài 1–5, nội dung quá ngắn (tối thiểu 20 ký tự), ngày đến ở tương lai, ảnh sai định dạng / quá 5 MB / quá 10 ảnh) → 422.
- 4a. Đã có review → 409 Conflict, gợi ý sửa review cũ (UC13). Ràng buộc `UNIQUE(place_id, user_id)` ở CSDL là lớp chặn cuối cho request đồng thời.
- 5a. Vượt rate limit → 429.

**Quy tắc nghiệp vụ**
- Điểm hiển thị = `(v/(v+m))·R + (m/(v+m))·C`, `m = 10`, chỉ tính review `published`.
- Người dùng xem được review `pending` của chính mình (có nhãn "Chờ duyệt").

---

### 3.7 UC16 — Check-in

| Mục | Nội dung |
|---|---|
| Tác nhân | Thành viên |
| FR | FR-24 |
| Mô tả | Thành viên xác nhận đang có mặt tại địa điểm bằng GPS |
| Tiền điều kiện | Đã đăng nhập, địa điểm `approved`, trình duyệt cấp quyền vị trí |
| Hậu điều kiện | Bản ghi `check_ins` được tạo; lượt check-in tính vào thống kê địa điểm (UC26) |

**Luồng chính**
1. Thành viên bấm "Check-in" trên trang địa điểm.
2. Frontend lấy tọa độ và độ chính xác (`accuracy`) từ Geolocation API, gửi `POST /places/{id}/check-in`.
3. Hệ thống tính khoảng cách từ tọa độ gửi lên tới `places.location` bằng `ST_Distance_Sphere`.
4. Khoảng cách ≤ 200 m → lưu check-in (tọa độ, thời gian).
5. Hệ thống thông báo check-in thành công.

**Ngoại lệ**
- 2a. Người dùng từ chối quyền vị trí → không thể check-in, hiển thị hướng dẫn bật vị trí.
- 2b. `accuracy` > 100 m → từ chối, yêu cầu thử lại ở nơi có tín hiệu tốt hơn (tránh vị trí ước lượng theo IP/wifi).
- 4a. Khoảng cách > 200 m → 422 "Bạn đang ở quá xa địa điểm (x m)".
- 4b. Đã check-in địa điểm này trong cùng ngày → 409 (tối đa 1 check-in / địa điểm / ngày).

**Quy tắc nghiệp vụ**
- Tọa độ GPS có thể bị giả lập phía client; hạn chế này được ghi rõ trong báo cáo (Chương 5 — hạn chế), check-in chỉ là một lớp tín hiệu, không phải bằng chứng tuyệt đối.

---

### 3.8 UC23 — Yêu cầu xác nhận sở hữu

| Mục | Nội dung |
|---|---|
| Tác nhân | Thành viên |
| FR | FR-31 |
| Mô tả | Thành viên chứng minh mình là chủ một địa điểm để được quản lý địa điểm đó |
| Tiền điều kiện | Đã đăng nhập, đã xác thực email, địa điểm `approved` và **chưa có chủ** |
| Hậu điều kiện | Bản ghi `place_claims` `status = pending`; chờ UC30 |

**Luồng chính**
1. Thành viên bấm "Bạn là chủ địa điểm này?" trên trang địa điểm.
2. Thành viên nhập thông tin liên hệ, tải minh chứng (ảnh giấy phép kinh doanh, ảnh chụp tại quán...).
3. Hệ thống kiểm tra địa điểm chưa có chủ và thành viên chưa có yêu cầu đang chờ cho địa điểm này.
4. Hệ thống lưu yêu cầu `pending`, lưu minh chứng vào kho **riêng tư** (không công khai URL).
5. Hệ thống thông báo cho kiểm duyệt viên và hiển thị "Yêu cầu đã được gửi".

**Ngoại lệ**
- 3a. Địa điểm đã có chủ → 409, gợi ý liên hệ quản trị nếu cho rằng có nhầm lẫn.
- 3b. Đã có yêu cầu đang chờ → 409.

**Quy tắc nghiệp vụ**
- Mỗi địa điểm có tối đa **một** chủ.
- Khi được duyệt (UC30): gán `places.owner_id`, thêm role `owner` cho thành viên nếu chưa có; các yêu cầu khác đang chờ cho cùng địa điểm tự động bị từ chối.

---

### 3.9 UC28 — Duyệt đánh giá trong hàng chờ

| Mục | Nội dung |
|---|---|
| Tác nhân | Kiểm duyệt viên (và Quản trị viên) |
| FR | FR-36, FR-22, FR-42 |
| Mô tả | Kiểm duyệt viên xem các review `pending` do cơ chế chống review ảo giữ lại và quyết định công khai hay từ chối |
| Tiền điều kiện | Đăng nhập với quyền duyệt review |
| Hậu điều kiện | Review `published` hoặc `rejected`; thao tác được ghi `activity_log` |

**Luồng chính**
1. Kiểm duyệt viên mở hàng chờ review (sắp xếp cũ nhất trước; có cờ "nghi ngờ IP", trust score của tác giả).
2. Kiểm duyệt viên mở một review, xem nội dung, ảnh, lịch sử tác giả, các review khác cùng dải IP.
3. Kiểm duyệt viên chọn "Duyệt".
4. Hệ thống chuyển review sang `published`, phát event → tính lại điểm Bayesian, đồng bộ index, thông báo cho tác giả và chủ địa điểm.
5. Hệ thống ghi `activity_log`.

**Luồng thay thế**
- 3a. Kiểm duyệt viên chọn "Từ chối" và nhập lý do → review `rejected`; **tác giả bị ghi một vi phạm** (trust −75, hết hạn sau 30 ngày — requirements §5.1); tác giả nhận thông báo kèm lý do.

**Ngoại lệ**
- 3b. Review đã được người khác xử lý (hai kiểm duyệt viên mở cùng lúc) → 409, tải lại hàng chờ. (dùng optimistic locking `@Version`)

**Quy tắc nghiệp vụ**
- Review bị từ chối không tính vào rating, tác giả vẫn thấy với nhãn "Bị từ chối" và lý do.

---

### 3.10 UC29 — Xử lý báo cáo vi phạm

| Mục | Nội dung |
|---|---|
| Tác nhân | Kiểm duyệt viên; Quản trị viên (thêm quyền khóa tài khoản) |
| FR | FR-37, FR-38, FR-42 |
| Mô tả | Xử lý các báo cáo mà thành viên gửi (UC17) về địa điểm, đánh giá, bình luận hoặc người dùng |
| Tiền điều kiện | Đăng nhập với quyền xử lý báo cáo |
| Hậu điều kiện | Báo cáo `resolved` hoặc `dismissed`; thao tác ghi `activity_log` |

**Luồng chính**
1. Kiểm duyệt viên mở hàng đợi báo cáo, gom nhóm theo đối tượng bị báo cáo (một đối tượng nhiều báo cáo hiển thị một dòng, có số lượng).
2. Kiểm duyệt viên xem đối tượng, lý do các báo cáo.
3. Kiểm duyệt viên xác nhận vi phạm và chọn "Ẩn nội dung".
4. Hệ thống ẩn đối tượng (không hiển thị công khai); nếu là review thì tính lại rating địa điểm.
5. Hệ thống **ghi một vi phạm cho tác giả nội dung** (requirements §5.1), đóng toàn bộ báo cáo về đối tượng đó `resolved`, thông báo cho tác giả và người báo cáo.
6. Hệ thống ghi `activity_log`.

**Luồng thay thế**
- 3a. Không vi phạm → "Bỏ qua": báo cáo `dismissed`, không ảnh hưởng tác giả.
- 3b. Vi phạm nghiêm trọng / tái phạm → Quản trị viên chọn "Khóa tài khoản" (kèm thời hạn và lý do) → tài khoản bị khóa, toàn bộ refresh token bị thu hồi. Kiểm duyệt viên không có quyền này (Q2) — nút bị ẩn và API trả 403.

**Ngoại lệ**
- 3c. Đối tượng đã bị xóa bởi tác giả trước khi xử lý → báo cáo tự đóng `resolved`, không ghi vi phạm.

**Quy tắc nghiệp vụ**
- Mỗi thành viên chỉ báo cáo một đối tượng một lần.
- "Ẩn" là đổi trạng thái hiển thị, **không xóa dữ liệu** (CLAUDE.md §4) — có thể khôi phục.

---

## 4. Luật nghiệp vụ đã chốt

| # | Câu hỏi | Quyết định (supervisor xác nhận 2026-09-27) | Ảnh hưởng |
|---|---|---|---|
| U1 | Chính sách mật khẩu, hạn link xác thực email | ≥ 8 ký tự có chữ + số; link 24 h; link đặt lại mật khẩu 30 phút | UC01, UC05 |
| U2 | Giới hạn đăng nhập sai; xử lý refresh token bị dùng lại | 5 lần / 15 phút theo (email, IP); dùng lại → thu hồi toàn bộ | UC03, NFR-10 |
| U3 | Review từ dải IP bị cảnh báo: chỉ gắn cờ hay bắt buộc vào hàng chờ? | Bắt buộc vào hàng chờ + gắn cờ | UC12, UC28 |
| U4 | Độ dài tối thiểu nội dung review | 20 ký tự | UC12 |
| U5 | Check-in: ngưỡng `accuracy` GPS và tần suất | Từ chối nếu accuracy > 100 m; 1 check-in / địa điểm / ngày | UC16 |
| U6 | Một địa điểm có tối đa bao nhiêu chủ? | 1 chủ | UC23, UC30, ERD (`places.owner_id` vs bảng trung gian) |
| U7 | Phát hiện trùng khi đề xuất địa điểm | Cảnh báo nếu tên gần giống trong bán kính 50 m, không chặn cứng | UC11 |
| U8 | Bán kính tìm quanh vị trí; vị trí mặc định khi từ chối quyền | Mặc định 2 km, tối đa 20 km; tâm Hà Nội | UC09 |
| U9 | Dự phòng khi Meilisearch lỗi | Fallback MySQL `LIKE` | UC08 |
| U10 | Báo cáo trùng | Mỗi người 1 báo cáo / đối tượng | UC17, UC29, ERD (`UNIQUE`) |
