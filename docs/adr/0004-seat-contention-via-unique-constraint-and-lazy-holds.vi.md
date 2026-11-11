# Xử lý tranh chấp ghế bằng unique constraint và Hold hết hạn kiểu lazy

[English](0004-seat-contention-via-unique-constraint-and-lazy-holds.md) | Tiếng Việt

Việc chặn đặt trùng ghế dựa vào một `UNIQUE(showtime_id, seat_id)` đơn giản trên bảng ghế đã đặt, không dùng lock ở tầng ứng dụng. Lệnh insert thua race sẽ thất bại và được báo về dưới dạng `409`, nêu rõ những ghế được yêu cầu đã có người lấy. Transaction thua trong một deadlock (do các lệnh xóa Hold hết hạn chồng lên nhau) được thử lại một lần trước. Để constraint này giữ được dạng đơn giản, các đơn Pending bị bỏ hoặc Expired bị xóa hẳn thay vì giữ lại kèm một trạng thái, và việc hết hạn được xử lý kiểu lazy: không có scheduler. Khi có người đặt một ghế, cùng transaction đó xóa trước mọi Hold đã hết hạn trên ghế ấy; sơ đồ ghế lọc theo `expires_at > now()`; thanh toán là một conditional update, chỉ thành công khi Hold còn hiệu lực.

Cùng cách làm này áp dụng cho quy tắc "mỗi khách hàng có tối đa một đơn Pending còn hiệu lực": một partial `UNIQUE(customer_id) WHERE status = 'PENDING'`, và transaction đặt vé xóa trước đơn Pending đã hết hạn của chính khách hàng đó. `now()` không được phép xuất hiện trong điều kiện của index, nên chính lệnh xóa lazy là thứ khiến một Hold đã hết hạn không bị tính.

## Hệ quả

- Không có lịch sử của các đơn bị bỏ hoặc Expired. Chấp nhận được, vì không chức năng nào đọc dữ liệu đó.
- Hold hết hạn trên những ghế không ai đặt lại vẫn nằm trong bảng cho tới khi bị chạm tới; chúng không chặn ai, không chặn đơn đặt vé mới và cũng không chặn việc xóa suất chiếu của chúng.
- Một Hold chiếm ghế dù có được thanh toán hay không, nên nhiều tài khoản khách hàng có thể giữ ghế của một suất chiếu mà không mua. Chấp nhận: chống việc này là công việc chống lạm dụng (rate limit, xác minh tài khoản), không phải một quy tắc đặt vé.
- Pessimistic/optimistic locking và đặt vé qua hàng đợi được cố ý để lại làm các điểm so sánh cho phase 2.
