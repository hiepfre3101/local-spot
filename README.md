# LocalSpot

Nền tảng tìm & đánh giá địa điểm địa phương do cộng đồng viết. Kế hoạch: [plan-v1.md](plan-v1.md) · Yêu cầu: [docs/requirements.md](docs/requirements.md) · API: [docs/api/openapi.yaml](docs/api/openapi.yaml) · Quy ước git: [CONTRIBUTING.md](CONTRIBUTING.md).

| Thư mục | Nội dung |
|---|---|
| `backend/` | Spring Boot 4.1 + Java 21, Maven Wrapper |
| `frontend/` | Vue 3 + Vite + TypeScript + Tailwind 4 |
| `docker-compose.yml` | MySQL 8.4, Redis, RabbitMQ, Meilisearch, MinIO; backend + nginx dưới profile `app` |

## Yêu cầu

- **WSL (Ubuntu)**: JDK 21, Docker Engine + Compose — backend build và Docker chạy trong WSL (xem [Cài WSL](#cài-wsl)).
- **Node 24 LTS** (`frontend/.nvmrc`) — chạy trên Windows hoặc WSL đều được.

## Chạy môi trường dev

```bash
cp .env.example .env            # đổi các giá trị changeme_*
docker compose up -d            # chỉ hạ tầng

# Backend (trong WSL) — profile dev đọc ../.env, API tại http://localhost:8080
cd backend && ./mvnw spring-boot:run

# Frontend — http://localhost:5173, /api và /ws được proxy sang backend
cd frontend && npm ci && npm run dev
```

Chạy cả hệ thống trong Docker (giống môi trường deploy): `docker compose --profile app up -d --build` → http://localhost:8088 (đổi cổng bằng `APP_PORT` trong `.env`; bắt buộc đặt `JWT_SECRET` — tạo bằng `openssl rand -base64 32`).

**Dữ liệu demo** (chỉ profile dev — Flyway nạp `backend/src/main/resources/db/seed/dev/` khi backend khởi động): 60 tài khoản và 300 địa điểm giả ở 5 thành phố. Mật khẩu chung `LocalSpot2026`:

| Email | Vai trò |
|---|---|
| `admin@localspot.test` | Quản trị viên |
| `mod1@localspot.test`, `mod2@localspot.test` | Kiểm duyệt viên |
| `owner1@localspot.test` … `owner6@localspot.test` | Chủ địa điểm |
| `member01@localspot.test` … `member51@localspot.test` | Thành viên (member09, 18, 27, 36, 45 chưa xác thực email) |

Profile prod (Docker `--profile app`) không nạp dữ liệu demo.

## Kiểm tra trước khi push

CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) chạy đúng các lệnh sau; chạy local trước để khỏi đỏ CI.

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
