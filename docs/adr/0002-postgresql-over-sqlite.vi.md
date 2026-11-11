# Chọn PostgreSQL thay vì SQLite

[English](0002-postgresql-over-sqlite.md) | Tiếng Việt

Chúng tôi dùng PostgreSQL, quản lý schema bằng Flyway migration. SQLite từng được cân nhắc vì không cần cài đặt gì, nhưng bị loại: nó tuần tự hóa mọi thao tác ghi sau một lock trên toàn database, nên load test các lượt đặt vé đồng thời sẽ đo lock của SQLite chứ không đo kiến trúc của chúng tôi. Vì SQLite chỉ là một file, nó cũng chặn luôn những bước hiển nhiên ở phase 2 (nhiều instance ứng dụng, read replica, connection pooling). Ngoài ra Hibernate chỉ hỗ trợ SQLite qua một dialect do cộng đồng duy trì.
