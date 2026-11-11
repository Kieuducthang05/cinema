# Hướng dẫn phát triển

[English](development.md) | Tiếng Việt

Cách chạy nhanh bằng Docker Compose nằm trong [README](../README.vi.md#bắt-đầu).

## Chạy trên máy

Bạn cần JDK 21 và Docker (để chạy PostgreSQL), hoặc JDK 21 và một PostgreSQL 17 của riêng bạn.

Chỉ chạy database trong Docker, rồi chạy ứng dụng bằng Maven:

```bash
docker compose up -d db
set -a; source .env; set +a   # export các biến trong .env
./mvnw spring-boot:run
```

Trong IDE, chạy `TestCinemaApplication` ở `src/test`. Class này khởi động PostgreSQL trong một container qua Testcontainers. Đặt `JWT_SECRET` và `ADMIN_PASSWORD` trong run configuration.

Để chạy image mà CI đã publish từ `main` thay vì tự build:

```bash
docker compose pull app
docker compose up --no-build
```

## Cấu hình

Service đọc cấu hình từ biến môi trường. Các secret không có giá trị mặc định.

| Biến | Bắt buộc | Mặc định | Mô tả |
|---|:---:|---|---|
| `DB_URL` | | `jdbc:postgresql://localhost:5432/cinema` | JDBC URL |
| `DB_USERNAME` | | `cinema` | User của database |
| `DB_PASSWORD` | ✓ | | Mật khẩu database |
| `JWT_SECRET` | ✓ | | Khóa ký HS256, tối thiểu 32 byte (`openssl rand -base64 32`) |
| `ADMIN_EMAIL` | | `admin@cinema.local` | Tài khoản Admin, được tạo khi khởi động nếu chưa có |
| `ADMIN_PASSWORD` | ✓ | | Mật khẩu Admin, được đặt lại mỗi lần khởi động; muốn đổi thì sửa biến rồi khởi động lại |

Thiếu `JWT_SECRET` hoặc `ADMIN_PASSWORD` thì ứng dụng không khởi động. Thiếu `DB_PASSWORD` thì ứng dụng không kết nối được database. Docker Compose kiểm tra cả ba biến trước khi chạy bất cứ thứ gì.

## Xác thực

Đăng nhập để lấy bearer token (hiệu lực một giờ), rồi gửi token trong header `Authorization`:

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123","fullName":"Alice"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}' | jq -r .accessToken)

curl localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
```

Trong Swagger UI, gọi `POST /api/auth/login`, bấm **Authorize** và dán `accessToken` vào. [Hướng dẫn đặt vé](booking-walkthrough.vi.md) đi qua trọn một lượt đặt vé theo cách này.

## Cấu trúc dự án

```
.
├── src/
│   ├── main/
│   │   ├── java/vn/edu/uet/cinema/
│   │   │   ├── api/            # REST controller, DTO, exception → ProblemDetail
│   │   │   ├── business/       # domain model, service, repository interface
│   │   │   ├── persistence/    # JPA entity, Spring Data repository, adapter
│   │   │   └── config/         # bảo mật, wiring bean, OpenAPI
│   │   └── resources/
│   │       └── db/migration/   # Flyway migration V1 đến V9
│   └── test/                   # unit, integration, architecture và contract test
├── loadtest/                   # k6 scenario, dữ liệu seed, Kaggle notebook
├── docs/                       # đặc tả OpenAPI, ADR, walkthrough, load-test baseline
├── .github/workflows/          # CI pipeline
├── Dockerfile                  # build multi-stage
└── docker-compose.yml          # app + PostgreSQL
```

Trong `persistence`, mỗi aggregate có một Spring Data interface tên `<X>EntityRepository` và một adapter `Jpa<X>Repository` hiện thực `<X>Repository` của business layer.
