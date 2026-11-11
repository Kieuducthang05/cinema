# Hủy suất chiếu trong một transaction của adapter

[English](0005-showtime-cancellation-in-one-adapter-transaction.md) | Tiếng Việt

Hủy một suất chiếu làm thay đổi cả suất chiếu lẫn mọi đơn đặt vé của nó: các đơn Pending bị xóa, các đơn Confirmed chuyển sang Refunded. Việc này phá quy tắc mỗi use case chỉ lưu đúng một aggregate, và chúng tôi cố ý làm vậy: nếu một suất chiếu đã Cancelled mà vẫn còn một đơn Confirmed, nghĩa là có khách hàng đã trả tiền cho một suất chiếu sẽ không diễn ra và chưa bao giờ được hoàn tiền. Vì vậy `ShowtimeRepository.cancel` làm tất cả trong một transaction ở persistence adapter, giống cách `deleteById` đã làm khi xóa các Hold hết hạn. Business layer vẫn không có transaction nào: service kiểm tra điều kiện, rồi gọi đúng một method của repository.

Các câu lệnh chạy theo thứ tự sau, và chính thứ tự này khiến việc thanh toán đồng thời được an toàn:

1. `UPDATE showtime SET status = 'CANCELLED' … WHERE status = 'SCHEDULED' AND ends_at > now`. Không dòng nào thay đổi nghĩa là suất chiếu đã bị hủy hoặc đã kết thúc (`409`). Bước này cũng tuần tự hóa trường hợp hai Admin hủy cùng lúc.
2. Xóa các đơn Pending của suất chiếu (đơn Expired cũng được lưu dưới dạng Pending).
3. Chuyển các đơn Confirmed của nó sang Refunded.

Thanh toán là một conditional update trên dòng của đơn đặt vé (ADR 0004). Nếu thanh toán lấy được row lock trước, bước 2 phải chờ, sau khi thanh toán commit thì kiểm tra lại dòng đó, thấy đơn đã Confirmed nên bỏ qua, và bước 3 hoàn tiền cho đơn. Nếu bước 2 chạy trước, thanh toán không tìm thấy dòng nào. Nếu đảo bước 2 và 3, một lượt thanh toán rơi vào giữa hai bước sẽ để lại một đơn Confirmed trên một suất chiếu đã Cancelled.

Một đơn đặt vé mới race với việc hủy theo cách khác. `BookingService.book` kiểm tra trạng thái trước, nhưng lệnh hủy có thể commit giữa lúc kiểm tra và lúc insert, và foreign key của đơn đặt vé không chặn được việc này. Key-share lock mà bước kiểm tra foreign key lấy không xung đột với một update trên các cột không phải khóa. Vì vậy transaction đặt vé bắt đầu bằng `SELECT status FROM showtime WHERE id = ? FOR SHARE` và từ chối suất chiếu đã Cancelled. Khi đó chỉ có hai khả năng: bước 1 của lệnh hủy chờ lượt đặt vé commit rồi bước 2 xóa đơn đó, hoặc lệnh hủy commit trước và lượt đặt vé thấy `CANCELLED`. Các share lock không xung đột với nhau, nên các lượt đặt vé đồng thời cho cùng một suất chiếu không phải chờ nhau.

## Hệ quả

- Hủy một suất chiếu sẽ khóa các dòng đơn đặt vé của nó và chặn trong chốc lát các lượt đặt vé mới, chỉ của suất chiếu đó.
- Nếu người sau bỏ `FOR SHARE` hoặc đổi thứ tự các câu lệnh của lệnh hủy, race sẽ quay lại mà test thông thường không phát hiện được. `BookingPersistenceTest` có một test ngẫu nhiên kết hợp đặt vé, thanh toán và hủy để bắt cả hai trường hợp.
- Một suất chiếu đã Cancelled có đơn Refunded không bao giờ xóa được (foreign key là `RESTRICT`), nhờ vậy vẫn đọc được suất chiếu đó từ các đơn kia.
