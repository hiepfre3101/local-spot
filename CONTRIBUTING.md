# Quy ước làm việc với git — LocalSpot

Theo plan-v1.md mục 7 ("Quy ước git"). Lịch sử commit sạch là một phần được hội đồng đánh giá.

## Nhánh

| Nhánh | Vai trò | Quy tắc |
|---|---|---|
| `main` | Bản ổn định, dùng để demo/deploy | Chỉ nhận merge từ `develop` (hoặc `hotfix/*`); không commit trực tiếp; không force-push |
| `develop` | Nhánh tích hợp | Nhận merge từ `feature/*`; không force-push |
| `feature/<phạm-vi>-<mô-tả-ngắn>` | Một tính năng/task | Tách từ `develop`, merge lại `develop` qua Pull Request. Ví dụ: `feature/be-auth-jwt`, `feature/fe-place-map` |
| `fix/<mô-tả>` | Sửa lỗi trên `develop` | Như `feature/*` |
| `hotfix/<mô-tả>` | Sửa lỗi gấp trên `main` | Tách từ `main`, merge vào cả `main` và `develop` |
| `docs/<mô-tả>` | Chỉ sửa tài liệu/báo cáo | Như `feature/*` |

Tiền tố phạm vi trong tên nhánh: `be` (backend), `fe` (frontend), `infra` (Docker/CI), bỏ trống nếu chung.

## Commit — Conventional Commits

```
<type>(<scope>): <mô tả ngắn, thể mệnh lệnh, không chấm cuối>

[thân: giải thích vì sao, không chỉ cái gì]

[footer: Refs #<issue>, BREAKING CHANGE: ...]
```

- **type**: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`, `style`.
- **scope** (khuyến nghị): `auth`, `place`, `review`, `search`, `checkin`, `collection`, `admin`, `infra`, `db`, `fe`, `report`...
- Mô tả tối đa ~72 ký tự; viết tiếng Việt hoặc tiếng Anh nhưng nhất quán trong một PR.
- Một commit = một thay đổi logic. Migration Flyway mới đi commit riêng với scope `db`.

Ví dụ:
```
feat(review): enforce one review per user per place
fix(search): normalize Vietnamese diacritics before indexing
docs(report): add use case specs for check-in
```

## Pull Request

- Merge `feature/*` → `develop` bằng **squash merge** hoặc merge commit sạch (tránh commit "wip", "fix typo" lọt vào lịch sử).
- PR mô tả: mục đích, FR liên quan (ví dụ `FR-17`), cách kiểm thử, ảnh chụp nếu có UI.
- CI (lint + test) phải xanh trước khi merge (sau khi thiết lập ở checklist C).
