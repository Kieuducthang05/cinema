# Hướng dẫn đặt vé

[English](booking-walkthrough.md) | Tiếng Việt

Admin dựng một danh mục nhỏ, sau đó một khách hàng giữ hai ghế, thanh toán và thấy hai ghế đó chuyển sang đã bán. Hai bước tùy chọn ở cuối minh họa xung đột ghế và việc hủy suất chiếu.

Bạn có thể làm theo trong Swagger UI (hợp để demo trực tiếp) hoặc chạy cả lượt dưới dạng một bash script dùng `curl`. Cách nào thì service cũng phải đang chạy, ví dụ bằng `docker compose up` như trong [README](../README.vi.md#bắt-đầu). Đăng nhập Admin bằng `ADMIN_EMAIL` và `ADMIN_PASSWORD` mà service được khởi động cùng.

## Trong Swagger UI

Mở `/swagger-ui.html` trên service đang chạy (trên máy là http://localhost:8080/swagger-ui.html). Ở mỗi bước, mở operation, bấm **Try it out**, dán body, bấm **Execute**, rồi chép `id` cần dùng từ response.

Swagger UI chỉ giữ một token tại một thời điểm. Bước 1 và 2 dùng token Admin, bước 3 đến 6 dùng token của khách hàng, bước 8 dùng lại token Admin.

### 1. Admin đăng nhập

`POST /api/auth/login`

```json
{"email": "admin@cinema.local", "password": "<ADMIN_PASSWORD>"}
```

Chép `accessToken`, bấm **Authorize** ở đầu trang, dán token vào rồi đóng hộp thoại. Từ giờ các operation có biểu tượng ổ khóa sẽ gửi kèm token này.

### 2. Admin dựng danh mục

`POST /api/cinemas` → chép `id` làm `CINEMA`

```json
{"name": "Central Cinema", "address": "1 Example Street"}
```

`POST /api/cinemas/{id}/auditoriums` với `id` = `CINEMA` → chép `id` làm `AUDITORIUM`. Lệnh này tạo 5 hàng, mỗi hàng 8 ghế: hàng A đến C là Standard, D là VIP, E là Couple.

```json
{"name": "Screen 1", "rows": 5, "seatsPerRow": 8, "vipRows": ["D"], "coupleRows": ["E"]}
```

`POST /api/movies` → chép `id` làm `MOVIE`

```json
{"title": "The Example", "description": "A story about cinema.", "durationMinutes": 120, "genre": "Drama",
 "ageRating": "T13", "releaseDate": "2026-05-01", "posterUrl": "https://example.com/poster.jpg"}
```

`POST /api/showtimes` → chép `id` làm `SHOWTIME`. `startsAt` phải ở tương lai. Response có `endsAt` = giờ bắt đầu + 120 phút + 15 phút dọn phòng.

```json
{"movieId": "<MOVIE>", "auditoriumId": "<AUDITORIUM>", "startsAt": "2030-01-01T19:30:00+07:00", "basePrice": 100000}
```

### 3. Khách hàng đăng ký và đăng nhập

`POST /api/auth/register` (trả về `409` nếu email đã được đăng ký; khi đó chỉ cần đăng nhập)

```json
{"email": "bob@example.com", "password": "password123", "fullName": "Bob"}
```

`POST /api/auth/login` với cùng email và mật khẩu, rồi **Authorize** lại bằng `accessToken` mới (logout token Admin trước).

### 4. Khách hàng mở sơ đồ ghế

`GET /api/showtimes/{id}/seats` với `id` = `SHOWTIME`. Mọi ghế đều `AVAILABLE`, kèm `label` (`A1`), `type` và `price`: Standard 100,000, VIP 120,000, Couple 210,000. Chép `id` của `A1` và `A2`.

### 5. Khách hàng giữ hai ghế

`POST /api/bookings` → chép `id` làm `BOOKING`

```json
{"showtimeId": "<SHOWTIME>", "seatIds": ["<A1 id>", "<A2 id>"]}
```

Response là `201` với `status` `PENDING`, `expiresAt` sau thời điểm hiện tại 10 phút và `totalPrice` 200000. Mở lại sơ đồ ghế: `A1` và `A2` giờ là `HELD`.

### 6. Khách hàng thanh toán

`POST /api/bookings/{id}/payment` với `id` = `BOOKING`. Đơn đặt vé chuyển sang `CONFIRMED`; sơ đồ ghế hiện `A1` và `A2` là `SOLD`. Thanh toán lần nữa chỉ trả về đúng đơn đó (`200`).

### 7. Tùy chọn: xung đột ghế

Vẫn bằng tài khoản khách hàng, gọi `POST /api/bookings` với `A2` và `A3`. Response là `409` dạng `application/problem+json`, liệt kê `id` của `A2` là đã có người lấy. Không ghế nào được giữ, kể cả `A3`.

### 8. Tùy chọn: hủy suất chiếu

Authorize lại bằng token Admin, rồi gọi `POST /api/showtimes/{id}/cancellation` với `id` = `SHOWTIME`:

```json
{"reason": "EQUIPMENT_FAILURE", "note": "Projector broke"}
```

Suất chiếu chuyển sang `CANCELLED`. Đăng nhập lại bằng tài khoản khách hàng: `GET /api/bookings/{id}` cho thấy đơn đã `REFUNDED`, còn sơ đồ ghế giờ trả về `409`.

### Khi có bước bị lỗi

| Response | Nguyên nhân thường gặp |
|---|---|
| `401` | Chưa có token, hoặc token đã hết hạn sau 1 giờ: đăng nhập và **Authorize** lại |
| `403` | Sai role: thao tác ghi danh mục cần token Admin, đặt vé cần token khách hàng |
| `409` ở `POST /showtimes` | Giờ chiếu chồng lên một suất chiếu khác trong phòng đó: chọn `startsAt` khác |
| `409` ở `POST /bookings` | Khách hàng đang có một đơn Pending còn hiệu lực: thanh toán hoặc `DELETE` đơn đó trước |
| `409` khi thanh toán | Hold 10 phút đã hết hạn: đặt lại |

## Dưới dạng bash script

Dán đoạn này vào bash shell từ thư mục gốc của repository. Script cần `curl` và `jq`, và chạy từ bước 1 đến bước 6.

```bash
set -a; source .env; set +a                  # ADMIN_EMAIL và ADMIN_PASSWORD
API=localhost:8080/api
JSON='Content-Type: application/json'

# 1. Admin đăng nhập
ADMIN=$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" '{email: $email, password: $password}' |
  curl -s -X POST $API/auth/login -H "$JSON" -d @- | jq -r .accessToken)

# 2. Admin tạo một rạp, một phòng chiếu (5 hàng x 8 ghế), một phim và một suất chiếu
CINEMA=$(curl -s -X POST $API/cinemas -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"name":"Central Cinema","address":"1 Example Street"}' | jq -r .id)

AUDITORIUM=$(curl -s -X POST $API/cinemas/$CINEMA/auditoriums -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"name":"Screen 1","rows":5,"seatsPerRow":8,"vipRows":["D"],"coupleRows":["E"]}' | jq -r .id)

MOVIE=$(curl -s -X POST $API/movies -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"title":"The Example","description":"A story about cinema.","durationMinutes":120,"genre":"Drama",
       "ageRating":"T13","releaseDate":"2026-05-01","posterUrl":"https://example.com/poster.jpg"}' | jq -r .id)

# startsAt phải ở tương lai
SHOWTIME=$(curl -s -X POST $API/showtimes -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d "{\"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",\"startsAt\":\"2030-01-01T19:30:00+07:00\",\"basePrice\":100000}" |
  jq -r .id)

# 3. Khách hàng đăng ký và đăng nhập (ở lần chạy thứ hai, đăng ký trả về 409 nhưng các bước sau vẫn chạy)
curl -s -X POST $API/auth/register -H "$JSON" \
  -d '{"email":"bob@example.com","password":"password123","fullName":"Bob"}' | jq -c .
CUSTOMER=$(curl -s -X POST $API/auth/login -H "$JSON" \
  -d '{"email":"bob@example.com","password":"password123"}' | jq -r .accessToken)

# 4-5. Khách hàng chọn hai ghế còn trống và giữ chúng trong 10 phút
SEATS=$(curl -s $API/showtimes/$SHOWTIME/seats | jq -c '[.[] | select(.status == "AVAILABLE") | .id][:2]')
BOOKING=$(curl -s -X POST $API/bookings -H "$JSON" -H "Authorization: Bearer $CUSTOMER" \
  -d "{\"showtimeId\":\"$SHOWTIME\",\"seatIds\":$SEATS}" | jq -r .id)

# 6. Khách hàng thanh toán; đơn chuyển sang CONFIRMED và ghế chuyển sang SOLD
curl -s -X POST $API/bookings/$BOOKING/payment -H "Authorization: Bearer $CUSTOMER" | jq '{status, totalPrice}'
curl -s $API/showtimes/$SHOWTIME/seats | jq -c '.[] | select(.status != "AVAILABLE") | {label, status}'
```

Lệnh thanh toán in ra `"status": "CONFIRMED"` và `totalPrice` là `200000`; lệnh cuối in `A1` và `A2` ở trạng thái `SOLD`. Nếu một biến nhận giá trị `null`, chạy lại lệnh `curl` đó mà bỏ `| jq` để xem lỗi `problem+json`.
