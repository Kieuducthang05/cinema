# Load test trên Kaggle không dùng Docker

[English](0003-load-test-on-kaggle-without-docker.md) | Tiếng Việt

Load test baseline chạy trên một Kaggle CPU notebook để phase 2 có thể lặp lại quy trình trên cùng nền tảng. Kaggle notebook không chạy được Docker (không có daemon), nên trên Kaggle service chạy dưới dạng một file JAR với PostgreSQL cài bằng `apt`, và load generator (k6) chạy trong cùng notebook, gọi tới `localhost`. Docker image chỉ dùng cho phát triển trên máy và cho việc chấm bài. Mỗi lần chạy cần so sánh với `results/env.txt` đã ghi lại của nó, thay vì giả định phần cứng giống hệt nhau.

Vì service và k6 dùng chung notebook, `taskset` được dùng để tách chúng ra: k6, kernel của notebook và bộ lấy mẫu telemetry chạy trên CPU đầu tiên, service và PostgreSQL chạy trên các CPU còn lại (ba trong bốn CPU của Kaggle). Hệ thống mất một core, nhưng đổi lại có thể phân biệt một step bị hỏng do service với trường hợp k6 tranh CPU của service, và mức dùng CPU của k6 được ghi lại để cho thấy khi nào chính k6 là giới hạn.
