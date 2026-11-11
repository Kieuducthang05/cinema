# Kiểm tra trước khi xóa ở cả business layer và database

[English](0006-delete-guards-in-business-and-database.md) | Tiếng Việt

Quy tắc "không được xóa rạp, phòng chiếu hoặc phim khi vẫn còn dữ liệu phụ thuộc" được kiểm tra hai lần. Service hỏi repository trước (`existsByCinemaId`, `existsByAuditoriumId`, `existsByMovieId`), và adapter cũng chuyển lỗi vi phạm foreign key thành cùng một `409`. Đây là thiết kế có chủ ý, không phải trùng lặp cần loại bỏ. Quy tắc này là quy tắc nghiệp vụ trong đề bài, nên business layer không phụ thuộc framework ([ADR 0001](0001-framework-free-business-layer.vi.md)) phải nêu nó ở chỗ người đọc business layer tìm thấy được. Foreign key chỉ lo trường hợp race, khi dữ liệu phụ thuộc được tạo ra giữa lúc kiểm tra và lúc xóa.

## Hệ quả

- Mỗi quy tắc này có hai bản cần giữ đồng bộ. Quy tắc chồng giờ suất chiếu cũng theo cách làm này, và `ShowtimePersistenceTest` test bản nằm ở adapter khi bỏ qua service.
- Một đợt review kiến trúc thấy các bước kiểm tra trước là thừa so với constraint thì không nên đề xuất xóa chúng.
