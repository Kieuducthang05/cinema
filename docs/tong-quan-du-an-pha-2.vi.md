# Tổng quan dự án để bắt đầu pha 2

Tài liệu đọc nhanh về trạng thái **Cinema Booking API sau pha 1**. Đây là backend REST cho một chuỗi rạp chiếu phim; repo hiện không có giao diện người dùng. Admin quản lý rạp, phòng, phim và suất chiếu. Khách hàng đăng ký, xem ghế, giữ ghế và thanh toán giả lập. Pha 2 có thể mở rộng trên nền này; các mục cuối trang là vấn đề hiện hữu, chưa phải tính năng đã triển khai.

## 1. Luồng nghiệp vụ cần nắm

1. **Admin tạo danh mục:** rạp → phòng chiếu và sơ đồ ghế → phim → suất chiếu. Sơ đồ ghế sinh theo số hàng và số ghế mỗi hàng, có loại Standard, VIP, Couple. Một suất chiếu chiếm phòng trong thời lượng phim cộng 15 phút dọn phòng; các suất trong cùng phòng không được chồng giờ.
2. **Khách xem và đặt vé:** lọc suất theo ngày, rạp, phim; xem từng ghế cùng trạng thái và giá. Đơn chứa 1–8 ghế, giữ trong **10 phút** ở trạng thái `PENDING`. Mỗi khách chỉ được có một đơn `PENDING` còn hiệu lực. Không thể đặt sau khi suất đã bắt đầu.
3. **Thanh toán:** endpoint thanh toán giả lập luôn thành công nếu Hold còn hạn, chuyển đơn thành `CONFIRMED` và ghế thành `SOLD`. Thanh toán lại đơn đã xác nhận trả về cùng đơn. Giá của từng ghế được chốt lúc đặt: Standard = giá gốc; VIP = giá gốc + 20.000 VND; Couple = 2 × giá gốc + 10.000 VND.
4. **Hết hạn, bỏ đơn, hủy suất:** Hold hết hạn được xem là `EXPIRED`; các bản ghi giữ ghế hết hạn được dọn khi có lượt đặt liên quan tiếp theo. Khách có thể xóa đơn chưa thanh toán. Admin hủy suất kèm lý do: trong một transaction, đơn đã thanh toán chuyển `REFUNDED`, đơn chưa thanh toán được xóa để trả ghế. `REFUNDED` hiện chỉ là trạng thái vì chưa tích hợp tiền thật.

**Bất biến quan trọng:** PostgreSQL có `UNIQUE(showtime_id, seat_id)` để một ghế không bị đặt hai lần, partial unique index cho một đơn Pending mỗi khách và exclusion constraint ngăn suất chiếu chồng giờ. Khi hai request tranh cùng ghế, database quyết định request thắng; request còn lại nhận `409`. Các ràng buộc này cần được giữ khi sửa luồng đặt vé.

## 2. Kiến trúc và chỗ tìm mã

Ứng dụng là **một service** Java 21 / Spring Boot 4.1, dùng PostgreSQL, Spring Data JPA/Hibernate và Flyway. Các package ở `src/main/java/vn/edu/uet/cinema/`:

| Package | Vai trò |
|---|---|
| `api` | REST controller, DTO, kiểm tra request và ánh xạ lỗi thành Problem Details |
| `business` | Model, quy tắc nghiệp vụ, service và repository interface; không phụ thuộc Spring/JPA |
| `persistence` | JPA entity, Spring Data repository và adapter hiện thực interface của `business` |
| `config` | Cấu hình bảo mật JWT, nối các bean, OpenAPI |

Các thực thể chính: `Account`, `Cinema`, `Auditorium`, `Seat`, `Movie`, `Showtime`, `Booking`, `BookedSeat`. Migration schema nằm trong `src/main/resources/db/migration/` (V1–V9). `ArchitectureTest` dùng ArchUnit kiểm tra ranh giới các tầng. Khi thêm nghiệp vụ, ưu tiên quy tắc trong `business`, phần lưu trữ trong `persistence`, giao thức HTTP trong `api`; thay đổi schema bằng migration mới.

## 3. API, bảo mật và cách chạy

Base path là `/api`. Đăng ký/đăng nhập: `/auth/register`, `/auth/login`; danh mục: `/cinemas`, `/auditoriums`, `/movies`, `/showtimes`; đặt vé: `/bookings` và `/bookings/{id}/payment`. `GET` danh mục công khai; ghi danh mục cần `ADMIN`; thao tác đặt vé cần `CUSTOMER`. JWT bearer HS256 có hạn **1 giờ**; mật khẩu dùng BCrypt. Lỗi API dùng `application/problem+json`, trong đó `401` là chưa xác thực, `403` là thiếu quyền, `409` thường là xung đột nghiệp vụ. Tiền dùng số nguyên VND; thời gian ISO-8601 có offset, lọc `date` theo ngày Việt Nam. Xem chi tiết request/response ở [OpenAPI](openapi.json).

Chạy nhanh: sao chép `.env.example` thành `.env`, điền `DB_PASSWORD`, `JWT_SECRET` (ít nhất 32 byte), `ADMIN_PASSWORD`, rồi chạy `docker compose up --build`. Compose khởi động PostgreSQL và app trên `127.0.0.1`; Admin được tạo từ biến môi trường khi app khởi động. Mở `http://localhost:8080/swagger-ui.html` để thử API và `/actuator/health` để kiểm tra service. Nếu phát triển bằng IDE/Maven, xem [hướng dẫn phát triển](development.vi.md). Bộ kiểm thử chạy bằng `./mvnw verify` và cần Docker cho Testcontainers/PostgreSQL.

## 4. Điểm xuất phát cho pha 2

- **Độ đúng và bảo mật:** thanh toán/hoàn tiền đang giả lập; chưa có giới hạn giữ ghế, xác minh email, rate limit hay thu hồi token/logout. Chỉ có một Admin cấu hình qua môi trường, chưa có audit log.
- **Hiệu năng:** baseline k6 trên Kaggle ghi nhận luồng tranh ghế giữ được khoảng **200–400 workflow/s** tùy scenario và lần chạy. Truy vấn trạng thái ghế là điểm tốn thời gian nổi bật khi tranh chấp; sơ đồ ghế tĩnh cũng đang bị đọc lại mỗi request. Các số này thuộc môi trường đo cụ thể, không phải cam kết năng lực khi triển khai nơi khác.
- **Vận hành và API:** hiện là một instance, observability mới có health/metrics; các danh sách chưa phân trang. Mỗi thay đổi hiệu năng nên đo lại bằng cùng scenario và kiểm tra bất biến không bán trùng ghế.

**Lộ trình đọc tiếp:** [walkthrough đặt vé](booking-walkthrough.vi.md) để thấy luồng đầu cuối; [glossary](../GLOSSARY.vi.md) để thống nhất thuật ngữ; [ADR](adr/) để hiểu lý do thiết kế; [báo cáo baseline](loadtest-baseline.vi.md) và [hướng dẫn load test](../loadtest/README.vi.md) trước khi tối ưu. README gốc có danh sách đầy đủ endpoint, kiểm thử và vấn đề đã biết.
