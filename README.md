# LocalSpot

Nền tảng tìm & đánh giá địa điểm địa phương do cộng đồng viết. API: [docs/api/openapi.yaml](docs/api/openapi.yaml).

| Thư mục | Nội dung |
|---|---|
| `backend/` | Spring Boot 4.1 + Java 21, Maven Wrapper |
| `frontend/` | Vue 3 + Vite + TypeScript + Tailwind 4 |
| `docker-compose.yml` | MySQL 8.4, Redis, RabbitMQ, Meilisearch 1.54, MinIO; backend + nginx dưới profile `app` |

## Yêu cầu

- **WSL (Ubuntu)**: JDK 21, Docker Engine + Compose — backend build và Docker chạy trong WSL (xem [Cài WSL](#cài-wsl)).
- **Node 24 LTS** (`frontend/.nvmrc`) — chạy trên Windows hoặc WSL đều được.

## Chạy môi trường dev

```bash
cp .env.example .env            # đổi các giá trị changeme_*
docker compose up -d            # chỉ hạ tầng (gồm Mailpit — xem email dev tại http://localhost:8025)

# Backend (trong WSL) — profile dev đọc ../.env, API tại http://localhost:8080
cd backend && ./mvnw spring-boot:run

# Frontend — http://localhost:5173, /api và /ws được proxy sang backend
cd frontend && npm ci && npm run dev
```

Chạy cả hệ thống trong Docker (giống môi trường deploy): `docker compose --profile app up -d --build` → http://localhost:8088 (đổi cổng bằng `APP_PORT` trong `.env`; bắt buộc đặt `JWT_SECRET` — tạo bằng `openssl rand -base64 32`).

**Dữ liệu demo** (chỉ profile dev — Flyway nạp `backend/src/main/resources/db/seed/dev/` khi backend khởi động): 60 tài khoản, 300 địa điểm giả ở 5 thành phố, ~2000 review kèm vote hữu ích / bình luận; khi khởi động backend tự tính lại điểm Bayesian và tải ảnh minh họa cho một số review lên MinIO (`localspot.demo.bootstrap`). Mật khẩu chung `LocalSpot2026`:

| Email | Vai trò |
|---|---|
| `admin@localspot.test` | Quản trị viên |
| `mod1@localspot.test`, `mod2@localspot.test` | Kiểm duyệt viên |
| `owner1@localspot.test` … `owner6@localspot.test` | Chủ địa điểm |
| `member01@localspot.test` … `member51@localspot.test` | Thành viên (member09, 18, 27, 36, 45 chưa xác thực email) |

Để demo trust score (requirements §5.1): `member07`, `member10`, `member19`… (tài khoản ≥ 250 ngày — "nhóm kỳ cựu") có trust cao, review đăng ngay; `member01`, `member11`, `member22`, `member33` (tài khoản < 60 ngày) trust < 30, review mới vào hàng chờ duyệt.

Profile prod (Docker `--profile app`) không nạp dữ liệu demo.

**Tìm kiếm** (`GET /api/v1/search`): backend khởi động là tự áp cấu hình index Meilisearch (synonym ở `backend/src/main/resources/search/synonyms.json` — sửa xong khởi động lại) và đồng bộ toàn bộ địa điểm đã duyệt qua RabbitMQ. Xem index tại http://localhost:7700 (khóa `MEILI_MASTER_KEY`).

> **Đổi Meilisearch v1.10 → v1.54 (2026-10-08)**: v1.54 không mở được dữ liệu của v1.10. Máy đã chạy compose trước ngày này làm một lần: `docker compose rm -sf meilisearch && docker volume rm local-spot_meilisearch_data && docker compose up -d` — không mất gì, index được dựng lại từ MySQL khi backend khởi động.

> **Tài khoản quản trị MySQL là `localspot_root`, không phải `root` (2026-10-19)** — tránh trùng với MySQL cài sẵn trên máy. Mật khẩu vẫn là `MYSQL_ROOT_PASSWORD`; backend vẫn dùng `MYSQL_USER`. Volume mới tự đổi qua `docker/mysql/initdb/01-rename-root.sql`; máy đã có volume từ trước chạy một lần (giữ nguyên dữ liệu):
> `docker exec -i localspot-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < docker/mysql/initdb/01-rename-root.sql && docker compose up -d mysql`

## Kiểm tra trước khi push

Chạy các lệnh sau trước khi push.

```bash
# Backend: test (Testcontainers — cần Docker) + Spotless + SpotBugs
cd backend && ./mvnw spotless:apply && ./mvnw verify

# Frontend
cd frontend && npm run lint:fix && npm run format && npm run type-check && npm test && npm run build-only
```

## Cài WSL

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release openjdk-21-jdk maven
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
sudo systemctl enable --now docker
newgrp docker
```
