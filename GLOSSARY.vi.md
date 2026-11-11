# Cinema Booking

[English](GLOSSARY.md) | Tiếng Việt

Backend cho một chuỗi rạp chiếu phim: Admin công bố phim nào chiếu ở đâu, khách hàng đặt ghế cho một suất chiếu cụ thể.

## Thuật ngữ

### Danh mục

**Rạp**:
Một địa điểm thực tế của chuỗi, xác định bằng tên và địa chỉ.
_Tránh dùng_: chi nhánh, cụm rạp

**Phòng chiếu**:
Một phòng chiếu phim thuộc đúng một rạp, có sơ đồ ghế cố định.
_Tránh dùng_: phòng, rạp

**Ghế**:
Một vị trí cố định trong phòng chiếu, xác định bằng chữ cái của hàng và số ghế (ví dụ `C7`), có một loại ghế.
_Tránh dùng_: chỗ ngồi

**Loại ghế**:
Hạng của ghế (Standard, VIP hoặc Couple), quyết định giá của ghế. Ghế Couple là một đơn vị bán cho hai người.
_Tránh dùng_: hạng ghế

**Phim**:
Một bộ phim mà chuỗi rạp có thể chiếu, với thời lượng đã biết.

**Suất chiếu**:
Một lần chiếu đã lên lịch của một phim, trong một phòng chiếu, vào một giờ bắt đầu cụ thể.
_Tránh dùng_: lịch chiếu, buổi chiếu

**Cancelled**:
Suất chiếu bị Admin hủy, trước khi bắt đầu hoặc khi đang chiếu, vì sự cố phía rạp (hỏng máy chiếu, cháy, mất điện). Admin phải ghi lý do hủy. Đây là trạng thái cuối, không bao giờ được khôi phục. Suất chiếu này không đặt được nữa, các đơn Pending của nó biến mất và nó không còn xuất hiện trong danh sách đang chiếu. Tuy vậy, nó vẫn truy cập được từ các đơn Refunded, để khách hàng biết mình được hoàn tiền cho suất nào. Nó cũng không còn chiếm phòng chiếu: một suất chiếu thay thế có thể dùng lại khung giờ đó.

**Lý do hủy**:
Lý do một suất chiếu bị Cancelled: một trong Equipment Failure, Power Outage, Emergency hoặc Other, kèm ghi chú. Other bắt buộc có ghi chú, các lý do còn lại có thể bỏ trống. Khách hàng thấy lý do này trên suất chiếu đã bị hủy.

**Giá gốc**:
Giá của ghế Standard trong một suất chiếu; giá ghế VIP và Couple được tính từ giá gốc cộng phụ phí cố định.
_Tránh dùng_: giá vé

### Đặt vé

**Đơn đặt vé**:
Việc một khách hàng đặt một hoặc nhiều ghế cho một suất chiếu. Đơn bắt đầu ở Pending và chuyển sang Confirmed khi đã thanh toán.
_Tránh dùng_: vé, đơn hàng

**Hold**:
Quyền giữ ghế có thời hạn của một đơn Pending; khi Hold hết hạn, các ghế được trả lại.
_Tránh dùng_: khóa ghế

**Pending**:
Đơn đặt vé có Hold còn hiệu lực và chưa được thanh toán. Khách hàng có thể bỏ đơn; khi đó đơn biến mất và ghế được trả lại.

**Expired**:
Đơn Pending có Hold đã hết hạn. Đơn không thể thanh toán nữa và không còn giữ ghế.

**Confirmed**:
Đơn đã thanh toán. Khách hàng không thể hủy hay được hoàn tiền; nếu không đi xem được thì khách tự chuyển lại cho người khác. Đơn chỉ kết thúc khi rạp hủy suất chiếu, và khi đó chuyển sang Refunded.

**Refunded**:
Đơn Confirmed có suất chiếu bị Cancelled; khách hàng được hoàn toàn bộ tiền. Đây là trạng thái cuối. Khách hàng không bao giờ tự kích hoạt trạng thái này, và cũng không ai kích hoạt thay cho khách hàng.
_Tránh dùng_: Cancelled (khách hàng không bao giờ hủy đơn)

### Con người

**Tài khoản**:
Danh tính đăng nhập (email và mật khẩu) của một khách hàng hoặc một Admin; role cho biết đó là ai.
_Tránh dùng_: người dùng, hồ sơ

**Khách hàng**:
Người đã đăng ký và đặt ghế cho các suất chiếu.
_Tránh dùng_: người dùng, thành viên

**Admin**:
Nhân viên quản lý rạp, phòng chiếu, phim và suất chiếu. Admin được tạo từ bên ngoài hệ thống, không bao giờ tự đăng ký.
_Tránh dùng_: quản lý, nhân viên vận hành
