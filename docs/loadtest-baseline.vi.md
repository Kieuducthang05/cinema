# Capacity baseline của load test trên Kaggle

[English](loadtest-baseline.md) | Tiếng Việt

Hai Kaggle CPU notebook công khai đã chạy cùng một mã nguồn (`4d40ac3`) với cùng cấu hình vào ngày 2026-10-29, và cả hai đều chạy xong năm scenario: [Run 1, notebook version 8](https://www.kaggle.com/code/tunichdi/se-phase-01-issue19-baseline/versions/8) và [Run 2, notebook version 1](https://www.kaggle.com/code/tunichdi/se-phase-01-capacity-baseline-2/versions/1). Output thô nằm trên Kaggle, không đưa vào Git.

## Tóm tắt

![Tóm tắt capacity baseline: step cao nhất còn giữ được của cả hai lần chạy và năm scenario](loadtest/summary.png)

| Scenario | Workflow | Cao nhất giữ được, it/s (Run 1 / Run 2) | Hỏng đầu tiên, it/s (Run 1 / Run 2) | Tầng quá tải đầu tiên |
|---|---|---:|---:|---|
| account | Đọc tài khoản của khách hàng đang đăng nhập | 1,200 / 1,200 | 1,600 / 1,600 | Hàng đợi request trong ứng dụng (thread Tomcat, Hikari pending) |
| browse | Liệt kê suất chiếu và mở một sơ đồ ghế | 400 / 400 | 600 / 600 | Chưa kết luận được: k6 dùng 92-93% core duy nhất của nó |
| book | Giữ một ghế chưa dùng và thanh toán | 50 / none | 75 / 50 | Chưa xác định: chỉ có dropped iteration, không tầng nào bão hòa; iowait (6-12%) là manh mối đầu tiên |
| contention | Nhiều khách hàng cùng thử đặt 10 ghế cố định, vốn đang bị Hold | 200 / 400 | 300 / 600 | Tranh chấp ở PostgreSQL và ứng dụng quanh truy vấn trạng thái ghế |
| race | Khách hàng giành cùng các ghế trống, và mỗi bên thắng bỏ đơn ngay | 200 / 300 | 300 / 400 | Tranh chấp ở PostgreSQL và ứng dụng quanh truy vấn trạng thái ghế, thêm lock wait ở Run 1 |

Step cao nhất còn giữ được là max sustainable throughput của scenario trong lần chạy đó; `none` nghĩa là không step nào giữ được. Cả 21 step contention và race đều qua kiểm tra SQL: không ghế nào bị đặt hai lần, và số `201` với `409` khớp nhau.

## Cách đọc kết quả

- Mỗi scenario warm-up ở 50 it/s mà không đo, rồi chạy các step 60 giây ở 50, 100, 200, cứ thế tăng dần. Sau step hỏng đầu tiên, scenario chạy thêm một step ở giữa tốc độ cuối cùng còn giữ được và tốc độ bị hỏng. [ADR 0007](adr/0007-capacity-baseline-by-stepped-arrival-rate-runs.vi.md) giải thích lý do.
- Một step là `broke` nếu một trong các điều kiện sau đúng, ngược lại là `held`:
  - p95 latency trên 200 ms
  - hơn 1% request lỗi ngoài dự kiến
  - dưới 99% check đạt
  - có dropped iteration
  - tốc độ thực đạt dưới 95% mục tiêu
- `exhausted` nghĩa là book đã hết ghế được seed, trường hợp này không tính là hỏng. Cả hai lần chạy đều không dùng hết seed.
- it/s là số workflow iteration hoàn tất mỗi giây. RPS là số HTTP request mỗi giây; browse và book gửi hai request cho mỗi iteration.
- Bảng step được sắp theo tốc độ, nên một step ở giữa như step 75 it/s của book đứng trước step cao hơn đã kích hoạt nó.
- Telemetry là giá trị trung bình trên cả step, trừ khi ghi là đỉnh. CPU tính theo phần trăm của một core, nên 135% là 1.35 core. Wait của PostgreSQL là số backend đang hoạt động trung bình ở mỗi loại wait.
- Mỗi step contention và race kết thúc bằng một lần kiểm tra SQL: số đơn được tạo trừ số đơn bị bỏ bằng số dòng ghế đã đặt, không ghế nào xuất hiện hai lần, và mọi `409` đều có lý do đã biết (ghế đã có người lấy, khách hàng đã có một đơn Pending, busy, hoặc ghế đã trống lại).

## Môi trường

| Mục | Cả hai lần chạy |
|---|---|
| Commit mã nguồn | `4d40ac3add0fa3f8d26e8a2ac8cc4ef5fa138e45` |
| Phần cứng | 4 CPU, Intel Xeon @ 2.20 GHz, 31 GiB RAM |
| CPU affinity | k6 và notebook trên CPU 0; service và PostgreSQL trên CPU 1-3 |
| Runtime | OpenJDK 21.0.12.1 (G1 GC, không có JVM flag), PostgreSQL 14.24, Hikari pool 10 kết nối |
| Load generator | k6 v2.3.0 |

<details>
<summary>Hash mã nguồn, dataset và metadata của notebook</summary>

| Mục | Run 1 | Run 2 |
|---|---|---|
| Kernel | `tunichdi/se-phase-01-issue19-baseline`, version 8 | `tunichdi/se-phase-01-capacity-baseline-2`, version 1 |
| Thời điểm báo cáo | 2026-10-29 07:38:05 UTC | 2026-10-29 07:56:03 UTC |
| Source bundle (SHA-256 của cây đã giải nén) | `bca958516e03508b624dabd5a4fefd84fccd66c8e5f5e51c4078d0de228d16e8` | `bca958516e03508b624dabd5a4fefd84fccd66c8e5f5e51c4078d0de228d16e8` |
| SHA-256 của notebook đã stage | `22912b24a77df0d6762c1e2c9b7d7e9e82f07175e6d26ddfad3e59784841e9cf` | `22912b24a77df0d6762c1e2c9b7d7e9e82f07175e6d26ddfad3e59784841e9cf` |
| Source dataset | `tunichdi/se-phase-01-issue19-source`, version 8 (private) | `tunichdi/se-phase-01-issue19-source`, version 8 (private) |
| Metadata của kernel | CPU notebook công khai; GPU false; TPU false; internet true | CPU notebook công khai; GPU false; TPU false; internet true |
| Tham số | `SCENARIO=all`, `START_RATE=50`, `MAX_RATE=6400`, `DURATION=60s`, `CODE_BUNDLE=auto` | Giống Run 1 |

Source dataset là private, nhưng cả hai notebook đều công khai, nên có thể đọc code và output đã lưu của chúng mà không cần dataset. Hai notebook đã lưu có nội dung cell giống hệt nhau. Bundle hash được tính trên đường dẫn tương đối, cờ thực thi và byte của các file trong cây đã giải nén, nên sẽ không khớp với hash của file ZIP.

</details>

## Các scenario

### Account

![Biểu đồ chồng của scenario account: throughput, latency, CPU, connection pool, wait của PostgreSQL, cả hai lần chạy](loadtest/account.png)

| Run | mục tiêu it/s | đạt it/s | RPS | p50 ms | p95 ms | p99 ms | lỗi | checks | dropped | busy 409 | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 50.0 | 50.0 | 2.48 | 3.18 | 4.98 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 100 | 100.0 | 100.0 | 1.94 | 2.40 | 4.34 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 200 | 200.0 | 200.0 | 1.66 | 2.03 | 3.94 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 400 | 400.0 | 400.0 | 1.38 | 2.23 | 4.44 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 800 | 800.0 | 800.0 | 1.36 | 3.56 | 16.50 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 1,200 | 1,200.0 | 1,200.0 | 1.46 | 177.36 | 300.44 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 1,600 | 1,593.2 | 1,593.2 | 1.65 | 734.29 | 960.64 | 0.00% | 100.00% | 411 | 0 | broke: p95 > 200 ms, dropped iterations |
| Run 2 | 50 | 50.0 | 50.0 | 2.07 | 2.77 | 4.58 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 100 | 100.0 | 100.0 | 1.44 | 1.97 | 3.85 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 200 | 200.0 | 200.0 | 1.18 | 1.68 | 3.64 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 400 | 400.0 | 400.0 | 1.05 | 1.95 | 4.45 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 800 | 800.0 | 800.0 | 1.08 | 3.70 | 17.60 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 1,200 | 1,200.0 | 1,200.0 | 1.17 | 96.61 | 226.91 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 1,600 | 1,596.2 | 1,596.2 | 1.53 | 706.20 | 943.82 | 0.00% | 100.00% | 226 | 0 | broke: p95 > 200 ms, dropped iterations |

Cả hai lần chạy đều hỏng ở 1,600 it/s vì p95 (734 ms và 706 ms) và có dropped iteration, không có lỗi. Request bị xếp hàng bên trong ứng dụng: số thread Tomcat bận đạt đỉnh 70 và 200, và có tới 74 và 187 request chờ một kết nối Hikari. PostgreSQL gần như rảnh, khoảng 0.1 backend hoạt động, và k6 dùng dưới 60% core của nó.

| Tại 1,600 it/s | Run 1 | Run 2 |
|---|---:|---:|
| Java CPU % | 129.97 | 135.06 |
| PostgreSQL CPU % | 41.35 | 36.83 |
| k6 CPU % | 58.43 | 56.28 |
| iowait % | 0.02 | 0.02 |
| Hikari active / pending đỉnh / acquire ms | 1.30 / 74 / 1.03 | 1.94 / 187 / 3.52 |
| Thread Tomcat bận, đỉnh | 70 | 200 |
| Tổng GC pause ms | 393 | 380 |
| Wait của PostgreSQL: CPU / Lock / LWLock / IO / khác | 0.08 / 0.00 / 0.00 / 0.00 / 0.04 | 0.02 / 0.00 / 0.00 / 0.00 / 0.09 |
| Deadlock / rollback | 0 / 0 | 0 / 0 |

<details>
<summary>Các câu SQL tốn nhất tại 1,600 it/s</summary>

| Run | Số lần gọi | Tổng ms | Trung bình ms | Đoạn truy vấn đã chuẩn hóa |
|---|---:|---:|---:|---|
| Run 1 | 95,591 | 2,123 | 0.022 | `select ... from account ae1_0 where ae1_0.id=$1` |
| Run 1 | 95,591 | 723 | 0.008 | `BEGIN READ ONLY` |
| Run 1 | 1 | 0 | 0.031 | `select ... from account ae1_0 where ae1_0.email=$1` |
| Run 2 | 95,774 | 1,847 | 0.019 | `select ... from account ae1_0 where ae1_0.id=$1` |
| Run 2 | 95,774 | 617 | 0.006 | `BEGIN READ ONLY` |
| Run 2 | 1 | 0 | 0.031 | `select ... from account ae1_0 where ae1_0.email=$1` |

</details>

### Browse

![Biểu đồ chồng của scenario browse: throughput, latency, CPU, connection pool, wait của PostgreSQL, cả hai lần chạy](loadtest/browse.png)

| Run | mục tiêu it/s | đạt it/s | RPS | p50 ms | p95 ms | p99 ms | lỗi | checks | dropped | busy 409 | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 50.0 | 100.0 | 3.16 | 4.22 | 6.04 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 100 | 100.0 | 200.0 | 2.98 | 4.93 | 7.08 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 200 | 200.0 | 400.0 | 2.95 | 9.95 | 32.33 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 400 | 400.1 | 800.2 | 48.88 | 131.54 | 179.77 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 600 | 419.5 | 838.9 | 1,060.12 | 1,764.66 | 1,880.22 | 0.00% | 100.00% | 10,833 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |
| Run 1 | 800 | 426.6 | 853.2 | 1,273.35 | 2,060.38 | 2,215.26 | 0.00% | 100.00% | 22,350 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |
| Run 2 | 50 | 50.0 | 100.0 | 2.52 | 3.85 | 5.81 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 100 | 100.0 | 200.0 | 2.33 | 4.62 | 7.74 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 200 | 200.0 | 400.0 | 2.26 | 8.69 | 26.03 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 400 | 400.1 | 800.1 | 38.50 | 128.44 | 186.93 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 600 | 430.3 | 860.6 | 1,032.48 | 1,739.33 | 1,920.66 | 0.00% | 100.00% | 10,161 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |
| Run 2 | 800 | 433.7 | 867.3 | 1,167.05 | 1,920.25 | 2,098.56 | 0.00% | 100.00% | 21,823 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |

Cả hai lần chạy đều hỏng ở 600 it/s: chỉ đạt 420 và 430 it/s, p95 tăng lên khoảng 1.7 s, và hơn 10,000 iteration bị dropped. k6 dùng 92% và 93% core duy nhất của nó, vượt ngưỡng cảnh báo 90% của báo cáo, nên có thể chính k6 là giới hạn. Service cũng bận (Java khoảng 135%, PostgreSQL khoảng 64%), nên các lần chạy này không tách được hai nguyên nhân. Browse có chạm giới hạn trong cấu hình notebook dùng chung này, nhưng nếu chạy riêng thì service có thể chịu được nhiều hơn.

| Tại 600 it/s | Run 1 | Run 2 |
|---|---:|---:|
| Java CPU % | 136.83 | 135.07 |
| PostgreSQL CPU % | 64.12 | 63.41 |
| k6 CPU % | 92.37 | 92.82 |
| iowait % | 0.03 | 0.01 |
| Hikari active / pending đỉnh / acquire ms | 3.11 / 31 / 3.17 | 3.08 / 36 / 2.52 |
| Thread Tomcat bận, đỉnh | 43 | 46 |
| Tổng GC pause ms | 345 | 296 |
| Wait của PostgreSQL: CPU / Lock / LWLock / IO / khác | 0.26 / 0.00 / 0.00 / 0.00 / 0.09 | 0.30 / 0.00 / 0.00 / 0.00 / 0.08 |
| Deadlock / rollback | 0 / 0 | 0 / 0 |

<details>
<summary>Các câu SQL tốn nhất tại 600 it/s</summary>

| Run | Số lần gọi | Tổng ms | Trung bình ms | Đoạn truy vấn đã chuẩn hóa |
|---|---:|---:|---:|---|
| Run 1 | 25,168 | 9,674 | 0.384 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 1 | 25,170 | 5,870 | 0.233 | `select ... from showtime se1_0 where se1_0.starts_at>=$1 and se1_0.starts_at<$2 and se1_0.status<>'CANCELLED' ...` |
| Run 1 | 25,168 | 642 | 0.026 | `select ... from showtime se1_0 where se1_0.id=$1` |
| Run 2 | 25,818 | 9,372 | 0.363 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 2 | 25,820 | 5,756 | 0.223 | `select ... from showtime se1_0 where se1_0.starts_at>=$1 and se1_0.starts_at<$2 and se1_0.status<>'CANCELLED' ...` |
| Run 2 | 25,818 | 587 | 0.023 | `select ... from showtime se1_0 where se1_0.id=$1` |

</details>

### Book

![Biểu đồ chồng của scenario book: throughput, latency, CPU, connection pool, wait của PostgreSQL, cả hai lần chạy](loadtest/book.png)

| Run | mục tiêu it/s | đạt it/s | RPS | p50 ms | p95 ms | p99 ms | lỗi | checks | dropped | busy 409 | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 50.0 | 100.0 | 7.50 | 9.89 | 19.50 | 0.00% | 99.98% | 0 | 0 | held |
| Run 1 | 75 | 75.0 | 150.0 | 6.92 | 9.29 | 73.71 | 0.00% | 100.00% | 1 | 0 | broke: dropped iterations |
| Run 1 | 100 | 98.7 | 197.3 | 6.83 | 9.83 | 146.84 | 0.00% | 99.99% | 80 | 0 | broke: dropped iterations |
| Run 2 | 50 | 50.0 | 99.9 | 5.76 | 8.28 | 83.75 | 0.00% | 99.98% | 3 | 0 | broke: dropped iterations |

Book hỏng chỉ vì dropped iteration: một iteration ở 75 it/s trong Run 1, và ba iteration ở 50 it/s trong Run 2, nên Run 2 không có step nào giữ được. Latency vẫn thấp (p95 dưới 10 ms), lỗi là 0%, và không có gì bão hòa: Java dưới 40%, PostgreSQL dưới 25%, k6 dưới 15%, và không có hàng đợi Hikari. Các lần chạy này không xác định được tầng nào quá tải. Tín hiệu duy nhất bất thường là iowait, ở mức 12.43% và 6.20% trong khi các scenario khác cao nhất chỉ 1.78%, và IO là loại wait lớn nhất của PostgreSQL. Book là scenario duy nhất commit một thao tác ghi ở mọi request, nên độ trễ của đĩa và của commit là thứ cần kiểm tra đầu tiên ở phase 2.

| Tại step hỏng đầu tiên | Run 1 tại 75 it/s | Run 2 tại 50 it/s |
|---|---:|---:|
| Java CPU % | 39.26 | 34.16 |
| PostgreSQL CPU % | 23.28 | 13.15 |
| k6 CPU % | 13.66 | 9.23 |
| iowait % | 12.43 | 6.20 |
| Hikari active / pending đỉnh / acquire ms | 0.82 / 0 / 0.21 | 0.33 / 0 / 0.16 |
| Thread Tomcat bận, đỉnh | 4 | 5 |
| Tổng GC pause ms | 31 | 29 |
| Wait của PostgreSQL: CPU / Lock / LWLock / IO / khác | 0.05 / 0.00 / 0.02 / 0.22 / 0.05 | 0.05 / 0.00 / 0.00 / 0.13 / 0.05 |
| Deadlock / rollback | 0 / 0 | 0 / 1 |

<details>
<summary>Các câu SQL tốn nhất tại step hỏng đầu tiên</summary>

| Run | Số lần gọi | Tổng ms | Trung bình ms | Đoạn truy vấn đã chuẩn hóa |
|---|---:|---:|---:|---|
| Run 1 | 4,512 | 888 | 0.197 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 1 | 4,500 | 636 | 0.141 | `insert into booking (created_at,customer_id,expires_at,showtime_id,status,id) values ($1,$2,$3,$4,$5,$6)` |
| Run 1 | 4,500 | 494 | 0.110 | `update booking set status = 'CONFIRMED' where id = $1 and status = 'PENDING' and expires_at > $2` |
| Run 2 | 3,010 | 550 | 0.183 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 2 | 2,998 | 390 | 0.130 | `insert into booking (created_at,customer_id,expires_at,showtime_id,status,id) values ($1,$2,$3,$4,$5,$6)` |
| Run 2 | 2,997 | 283 | 0.094 | `update booking set status = 'CONFIRMED' where id = $1 and status = 'PENDING' and expires_at > $2` |

</details>

### Contention

![Biểu đồ chồng của scenario contention: throughput, latency, CPU, connection pool, wait của PostgreSQL, xung đột, cả hai lần chạy](loadtest/contention.png)

| Run | mục tiêu it/s | đạt it/s | RPS | p50 ms | p95 ms | p99 ms | lỗi | checks | dropped | busy 409 | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 50.0 | 50.0 | 10.42 | 13.02 | 16.26 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 100 | 100.0 | 100.0 | 8.04 | 10.24 | 12.91 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 200 | 200.0 | 200.0 | 9.31 | 16.63 | 23.77 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 300 | 300.0 | 300.0 | 14.99 | 453.17 | 1,056.12 | 0.00% | 100.00% | 0 | 0 | broke: p95 > 200 ms |
| Run 1 | 400 | 345.8 | 345.8 | 3,065.84 | 6,353.35 | 6,888.33 | 0.00% | 100.00% | 3,251 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |
| Run 2 | 50 | 50.0 | 50.0 | 6.03 | 8.19 | 10.45 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 100 | 100.0 | 100.0 | 5.71 | 7.61 | 10.34 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 200 | 200.0 | 200.0 | 5.59 | 9.14 | 14.62 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 400 | 400.0 | 400.0 | 13.65 | 83.67 | 167.65 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 600 | 425.4 | 425.4 | 4,644.24 | 5,620.81 | 5,973.54 | 0.00% | 100.00% | 10,478 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |
| Run 2 | 800 | 426.2 | 426.2 | 4,854.08 | 5,620.68 | 5,961.10 | 0.00% | 100.00% | 22,426 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |

Run 1 hỏng ở 300 it/s vì p95 (453 ms) dù vẫn đạt tốc độ mục tiêu. Run 2 giữ được 400 và hỏng ở 600, chỉ đạt 425 it/s. Ở cả hai lần chạy, toàn bộ 200 thread Tomcat đều bận, có tới 189 request chờ kết nối database, và PostgreSQL dùng 1.3-1.4 core. Run 2 chờ kết nối lâu hơn nhiều (trung bình 97 ms so với 10 ms). Một truy vấn chiếm phần lớn: đọc những ghế nào của suất chiếu đã có người lấy, với 95 s và 68 s tổng thời gian database. k6 dùng dưới 45% core của nó. Cả 11 step đều qua kiểm tra SQL.

| Tại step hỏng đầu tiên | Run 1 tại 300 it/s | Run 2 tại 600 it/s |
|---|---:|---:|
| Java CPU % | 105.63 | 140.87 |
| PostgreSQL CPU % | 142.53 | 126.72 |
| k6 CPU % | 27.20 | 42.90 |
| iowait % | 0.28 | 0.18 |
| Hikari active / pending đỉnh / acquire ms | 4.21 / 189 / 9.76 | 6.50 / 189 / 97.07 |
| Thread Tomcat bận, đỉnh | 200 | 200 |
| Tổng GC pause ms | 174 | 414 |
| Wait của PostgreSQL: CPU / Lock / LWLock / IO / khác | 2.11 / 0.00 / 0.00 / 0.02 / 0.23 | 1.53 / 0.00 / 0.06 / 0.00 / 0.18 |
| Deadlock / rollback | 0 / 14,401 | 0 / 24,225 |

<details>
<summary>Các câu SQL tốn nhất tại step hỏng đầu tiên</summary>

| Run | Số lần gọi | Tổng ms | Trung bình ms | Đoạn truy vấn đã chuẩn hóa |
|---|---:|---:|---:|---|
| Run 1 | 14,360 | 95,431 | 6.646 | `select s.seat_id as seatId, b.status as status from booking b join booked_seat s on s.booking_id = b.id where b.showtime_id = $1 and (b.status = 'CONFIRMED' or b.expires_at > $2)` |
| Run 1 | 18,002 | 5,888 | 0.327 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 1 | 14,369 | 2,694 | 0.187 | `insert into booking (created_at,customer_id,expires_at,showtime_id,status,id) values ($1,$2,$3,$4,$5,$6)` |
| Run 2 | 24,188 | 68,335 | 2.825 | `select s.seat_id as seatId, b.status as status from booking b join booked_seat s on s.booking_id = b.id where b.showtime_id = $1 and (b.status = 'CONFIRMED' or b.expires_at > $2)` |
| Run 2 | 25,524 | 11,598 | 0.454 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 2 | 24,197 | 5,414 | 0.224 | `insert into booking (created_at,customer_id,expires_at,showtime_id,status,id) values ($1,$2,$3,$4,$5,$6)` |

</details>

<details>
<summary>Kiểm tra SQL theo từng step</summary>

| Run | mục tiêu | 201 đã tạo | đã bỏ | tổng 409 | ghế đã có người lấy | pending | busy | trống lại | dòng đã đặt | trùng | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 10 | 0 | 2,991 | 1,845 | 1,146 | 0 | 0 | 10 | 0 | PASS |
| Run 1 | 100 | 10 | 0 | 5,991 | 4,245 | 1,746 | 0 | 0 | 10 | 0 | PASS |
| Run 1 | 200 | 10 | 0 | 11,991 | 9,634 | 2,357 | 0 | 0 | 10 | 0 | PASS |
| Run 1 | 300 | 10 | 0 | 17,991 | 14,359 | 3,632 | 0 | 0 | 10 | 0 | PASS |
| Run 1 | 400 | 10 | 0 | 20,740 | 19,396 | 1,344 | 0 | 0 | 10 | 0 | PASS |
| Run 2 | 50 | 10 | 0 | 2,991 | 1,845 | 1,146 | 0 | 0 | 10 | 0 | PASS |
| Run 2 | 100 | 10 | 0 | 5,991 | 4,244 | 1,747 | 0 | 0 | 10 | 0 | PASS |
| Run 2 | 200 | 10 | 0 | 11,991 | 9,644 | 2,347 | 0 | 0 | 10 | 0 | PASS |
| Run 2 | 400 | 10 | 0 | 23,991 | 20,996 | 2,995 | 0 | 0 | 10 | 0 | PASS |
| Run 2 | 600 | 10 | 0 | 25,513 | 24,187 | 1,326 | 0 | 0 | 10 | 0 | PASS |
| Run 2 | 800 | 10 | 0 | 25,565 | 24,653 | 912 | 0 | 0 | 10 | 0 | PASS |

</details>

### Race

![Biểu đồ chồng của scenario race: throughput, latency, CPU, connection pool, wait của PostgreSQL, xung đột, cả hai lần chạy](loadtest/race.png)

| Run | mục tiêu it/s | đạt it/s | RPS | p50 ms | p95 ms | p99 ms | lỗi | checks | dropped | busy 409 | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 50.0 | 50.0 | 8.34 | 9.91 | 12.06 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 100 | 100.0 | 100.0 | 7.98 | 10.10 | 13.54 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 200 | 200.0 | 200.0 | 10.02 | 15.52 | 24.60 | 0.00% | 100.00% | 0 | 0 | held |
| Run 1 | 300 | 300.0 | 300.0 | 165.26 | 776.64 | 1,121.14 | 0.00% | 100.00% | 0 | 0 | broke: p95 > 200 ms |
| Run 1 | 400 | 324.4 | 324.4 | 4,225.76 | 8,009.27 | 8,770.90 | 0.00% | 100.00% | 4,540 | 0 | broke: p95 > 200 ms, dropped iterations, achieved < 95% of target |
| Run 2 | 50 | 50.0 | 50.0 | 6.41 | 7.96 | 10.26 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 100 | 100.0 | 100.0 | 5.77 | 7.77 | 10.64 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 200 | 200.0 | 200.0 | 7.19 | 10.90 | 16.32 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 300 | 300.0 | 300.0 | 9.55 | 19.45 | 33.33 | 0.00% | 100.00% | 0 | 0 | held |
| Run 2 | 400 | 397.2 | 397.2 | 739.05 | 1,522.60 | 1,857.84 | 0.00% | 100.00% | 167 | 0 | broke: p95 > 200 ms, dropped iterations |

Run 1 hỏng ở 300 it/s vì p95 (777 ms). Run 2 giữ được 300 và hỏng ở 400 vì p95 (1.5 s) cùng 167 dropped iteration. Diễn biến giống contention: toàn bộ 200 thread Tomcat bận, khoảng 190 request chờ kết nối (trung bình 43 ms và 88 ms), PostgreSQL dùng 1.5-1.7 core, và vẫn là truy vấn trạng thái ghế đứng đầu (tổng 149 s và 51 s). Run 1 còn có trung bình 0.58 backend chờ lock. k6 dùng dưới 35% core của nó. Cả 10 step đều qua kiểm tra SQL.

| Tại step hỏng đầu tiên | Run 1 tại 300 it/s | Run 2 tại 400 it/s |
|---|---:|---:|
| Java CPU % | 117.92 | 141.92 |
| PostgreSQL CPU % | 170.51 | 148.58 |
| k6 CPU % | 28.95 | 34.30 |
| iowait % | 1.78 | 0.61 |
| Hikari active / pending đỉnh / acquire ms | 9.00 / 190 / 43.12 | 9.50 / 192 / 88.28 |
| Thread Tomcat bận, đỉnh | 200 | 200 |
| Tổng GC pause ms | 243 | 326 |
| Wait của PostgreSQL: CPU / Lock / LWLock / IO / khác | 2.56 / 0.58 / 0.04 / 0.23 / 0.27 | 1.65 / 0.04 / 0.04 / 0.09 / 0.13 |
| Deadlock / rollback | 0 / 16,102 | 0 / 22,876 |

<details>
<summary>Các câu SQL tốn nhất tại step hỏng đầu tiên</summary>

| Run | Số lần gọi | Tổng ms | Trung bình ms | Đoạn truy vấn đã chuẩn hóa |
|---|---:|---:|---:|---|
| Run 1 | 16,158 | 149,275 | 9.238 | `select s.seat_id as seatId, b.status as status from booking b join booked_seat s on s.booking_id = b.id where b.showtime_id = $1 and (b.status = $3 or b.expires_at > $2)` |
| Run 1 | 18,002 | 7,858 | 0.437 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 1 | 18,001 | 5,016 | 0.279 | `delete from booking b where b.status = $5 and b.expires_at <= $1 and (b.customer_id = $2 or b.id in (...))` |
| Run 2 | 22,962 | 51,148 | 2.228 | `select s.seat_id as seatId, b.status as status from booking b join booked_seat s on s.booking_id = b.id where b.showtime_id = $1 and (b.status = 'CONFIRMED' or b.expires_at > $2)` |
| Run 2 | 23,835 | 10,630 | 0.446 | `select ... from seat s1_0 where s1_0.auditorium_id=$1 order by s1_0.row_letter,s1_0.seat_number` |
| Run 2 | 23,834 | 7,308 | 0.307 | `delete from booking b where b.status = 'PENDING' and b.expires_at <= $1 and (b.customer_id = $2 or b.id in (...))` |

</details>

<details>
<summary>Kiểm tra SQL theo từng step</summary>

| Run | mục tiêu | 201 đã tạo | đã bỏ | tổng 409 | ghế đã có người lấy | pending | busy | trống lại | dòng đã đặt | trùng | kết quả |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Run 1 | 50 | 2,996 | 2,996 | 5 | 5 | 0 | 0 | 0 | 0 | 0 | PASS |
| Run 1 | 100 | 5,946 | 5,946 | 55 | 55 | 0 | 0 | 0 | 0 | 0 | PASS |
| Run 1 | 200 | 6,189 | 6,189 | 5,812 | 5,811 | 0 | 0 | 1 | 0 | 0 | PASS |
| Run 1 | 300 | 1,844 | 1,844 | 16,157 | 15,542 | 0 | 0 | 615 | 0 | 0 | PASS |
| Run 1 | 400 | 206 | 206 | 19,255 | 19,180 | 0 | 0 | 75 | 0 | 0 | PASS |
| Run 2 | 50 | 2,999 | 2,999 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | PASS |
| Run 2 | 100 | 5,979 | 5,979 | 22 | 22 | 0 | 0 | 0 | 0 | 0 | PASS |
| Run 2 | 200 | 8,763 | 8,763 | 3,238 | 3,226 | 0 | 0 | 12 | 0 | 0 | PASS |
| Run 2 | 300 | 7,340 | 7,340 | 10,661 | 10,554 | 0 | 0 | 107 | 0 | 0 | PASS |
| Run 2 | 400 | 873 | 873 | 22,961 | 22,599 | 0 | 0 | 362 | 0 | 0 | PASS |

</details>

## So sánh giữa hai lần chạy

| Scenario | Run 1 cao nhất giữ được | Run 2 cao nhất giữ được | Chênh lệch |
|---|---:|---:|---:|
| account | 1,200 | 1,200 | 0% |
| browse | 400 | 400 | 0% |
| book | 50 | none | không so sánh được |
| contention | 200 | 400 | 100% |
| race | 200 | 300 | 50% |

Account và browse giữ được cùng một step ở cả hai lần chạy. Run 2 giữ được mức cao gấp đôi ở contention và cao hơn 50% ở race. p95 ở cùng một step chênh hơn 10% trong mọi scenario:

- account: thấp hơn 13-18% ở Run 2 cho tới 400 it/s và thấp hơn 46% ở 1,200; chênh trong vòng 4% ở 800 và 1,600.
- browse: chênh trong vòng 9%, trừ mức 13% ở 200 it/s.
- book: 16% ở 50 it/s.
- contention: 26-45% cho tới 200 it/s, và 99% ở 400, nơi Run 1 đã hỏng.
- race: 20-30% cho tới 200 it/s, 98% ở 300 và 81% ở 400.

Hai lần chạy đủ để cho thấy mức dao động này, nhưng chưa đủ để giải thích nó hay tính khoảng tin cậy.

<details>
<summary>p95 ở mọi step mà cả hai lần chạy cùng đo</summary>

Chênh lệch là `abs(Run 2 - Run 1) / Run 1 * 100`, tính ở đúng các tốc độ mà cả hai lần chạy đều đo. Không nội suy giá trị nào.

| Scenario | Mục tiêu chung | Run 1 p95 ms | Run 2 p95 ms | Chênh lệch | Trên 10% |
|---|---:|---:|---:|---:|---|
| account | 50 | 3.18 | 2.77 | 12.97% | có |
| account | 100 | 2.40 | 1.97 | 17.79% | có |
| account | 200 | 2.03 | 1.68 | 17.25% | có |
| account | 400 | 2.23 | 1.95 | 12.69% | có |
| account | 800 | 3.56 | 3.70 | 3.81% | không |
| account | 1,200 | 177.36 | 96.61 | 45.53% | có |
| account | 1,600 | 734.29 | 706.20 | 3.82% | không |
| browse | 50 | 4.22 | 3.85 | 8.74% | không |
| browse | 100 | 4.93 | 4.62 | 6.43% | không |
| browse | 200 | 9.95 | 8.69 | 12.68% | có |
| browse | 400 | 131.54 | 128.44 | 2.36% | không |
| browse | 600 | 1,764.66 | 1,739.33 | 1.44% | không |
| browse | 800 | 2,060.38 | 1,920.25 | 6.80% | không |
| book | 50 | 9.89 | 8.28 | 16.25% | có |
| contention | 50 | 13.02 | 8.19 | 37.09% | có |
| contention | 100 | 10.24 | 7.61 | 25.62% | có |
| contention | 200 | 16.63 | 9.14 | 45.04% | có |
| contention | 400 | 6,353.35 | 83.67 | 98.68% | có |
| race | 50 | 9.91 | 7.96 | 19.60% | có |
| race | 100 | 10.10 | 7.77 | 23.00% | có |
| race | 200 | 15.52 | 10.90 | 29.79% | có |
| race | 300 | 776.64 | 19.45 | 97.50% | có |
| race | 400 | 8,009.27 | 1,522.60 | 80.99% | có |

</details>

## Tái lập

Tab Output của mỗi notebook có `env.txt`, log của ứng dụng và k6, summary, telemetry, câu lệnh SQL, bộ đếm database và các file kiểm tra. Để vẽ lại biểu đồ từ hai bộ kết quả đã tải về:

```bash
python3 loadtest/charts.py --out docs/loadtest "baseline-1=<run-1-results>" "baseline-2=<run-2-results>"
```

Chỉ so sánh các lần chạy sau với một lần chạy dùng cùng giao thức đo và cùng cách chia CPU.
