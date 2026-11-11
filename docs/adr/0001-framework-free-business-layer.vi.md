# Business layer không phụ thuộc framework

[English](0001-framework-free-business-layer.md) | Tiếng Việt

Đề bài yêu cầu business layer không import web framework hay thư viện DB nào. Chúng tôi hiểu yêu cầu này theo nghĩa chặt: business layer chỉ phụ thuộc `java.*` và code của chính nó, không có annotation nào của Spring (kể cả `@Service` hay `@Transactional`). Business model là các record/class thuần, tách khỏi JPA entity; business layer tự khai báo các repository interface mà nó cần, còn persistence layer hiện thực chúng bằng Spring Data JPA. Việc wiring nằm trong các method `@Bean` ở package `config`. Một ArchUnit test làm build thất bại nếu quy tắc này bị vi phạm.

## Hệ quả

- Có hai bộ model (business model và JPA entity) cùng các mapper viết tay giữa chúng. Đây là cái giá của quy tắc, không phải trùng lặp ngoài ý muốn; đừng "sửa" bằng cách gắn annotation vào các class của business.
