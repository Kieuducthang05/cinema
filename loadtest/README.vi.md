# Load test

[English](README.md) | Tiếng Việt

Mục tiêu là tìm tốc độ cao nhất mà mỗi workflow còn giữ được và tầng nào quá tải đầu tiên, bằng cách chạy k6 với service và PostgreSQL trong một Kaggle CPU notebook (hoặc trên máy, mỗi lần một step). Phương pháp nằm trong [ADR 0007](../docs/adr/0007-capacity-baseline-by-stepped-arrival-rate-runs.vi.md). [loadtest-baseline.vi.md](../docs/loadtest-baseline.vi.md) chứa baseline hiện tại, lấy từ [Run 1, canonical version 8](https://www.kaggle.com/code/tunichdi/se-phase-01-issue19-baseline/versions/8) và [Run 2, independent version 1](https://www.kaggle.com/code/tunichdi/se-phase-01-capacity-baseline-2/versions/1).

## Các scenario

Một iteration là một workflow. Một virtual user (VU) là một worker của k6. Một step là một lần chạy k6 ở một tốc độ cố định.

| Scenario | Workflow | Request/iteration | Khách hàng |
|---|---|---:|---|
| `account` | Đọc tài khoản của khách hàng đang đăng nhập | 1 | 1..2000 |
| `browse` | Liệt kê suất chiếu, mở một sơ đồ ghế | 2 | không có |
| `book` | Giữ một ghế chưa dùng, thanh toán | 2 | 1..2000 |
| `contention` | Thử giữ một trong 10 ghế cố định, vốn đang bị giữ sẵn | 1 | 2001..4000 |
| `race` | Giống contention, nhưng bên thắng bỏ đơn ngay, nên các lượt thử race nhau giành ghế trống | 1 | 4001..6000 |

`SCENARIO="all"` chạy lần lượt theo thứ tự trên. Contention đo chi phí khi gặp một ghế đang bị giữ; race đo việc nhiều khách hàng giành cùng một ghế trống. Các request chuẩn bị, kể cả đăng nhập, không được tính vào request được đo, và lệnh bỏ đơn sau khi thắng race cũng vậy.

## Chạy trên máy

Khởi động service và PostgreSQL theo README chính. Cài k6 đúng phiên bản ghi ở `K6_VERSION` trong `kaggle.ipynb`.

**Dùng một database test mới.** Chạy seed một lần, sau khi service đã healthy và Flyway đã áp dụng xong migration. Chạy trên máy chỉ là một step; chỉ notebook mới tăng tốc độ qua các step và lấy mẫu telemetry.

```bash
psql -v ON_ERROR_STOP=1 "<your postgres url for the cinema database>" -f loadtest/seed.sql
mkdir -p loadtest/results
k6 run -e COUNT=200 loadtest/k6/login.js             # lưu loadtest/results/tokens.json
k6 run -e RATE=20 -e DURATION=10s loadtest/k6/book.js
python3 loadtest/report.py
```

`browse` không cần token. Với `contention` hoặc `race`, đăng nhập trước cho đúng nhóm khách hàng của chúng (`-e FIRST=2001` hoặc `-e FIRST=4001`) và truyền `-e STEP=<n>` để mỗi lần chạy dùng một suất chiếu mới. Với lần chạy `book` thứ hai, truyền `-e SLOT_OFFSET=<số iteration đã chạy>` để không dùng lại ghế cũ. Chạy test của các helper bằng `python3 loadtest/test_loadtest.py` (cần Python và Node).

## Chạy trên Kaggle

Notebook cài PostgreSQL, build service thành file JAR và chạy k6; trên Kaggle không có Docker. k6 chạy trên một CPU, service và PostgreSQL chạy trên các CPU còn lại. Xem [ADR 0003](../docs/adr/0003-load-test-on-kaggle-without-docker.vi.md).

1. Import `loadtest/kaggle.ipynb`. Đặt **Accelerator: None**, **Internet: On** và hoàn tất bước xác minh tài khoản nếu Kaggle yêu cầu.
2. Chọn nguồn mã:
   - Git: đặt `GIT_REF` và để `CODE_BUNDLE=None`. Gắn `GITHUB_TOKEN` trong **Add-ons → Secrets**, với quyền **Contents: Read-only** trên `14m6d4/se-phase-01`.
   - Thay đổi trên máy: gắn một private source Dataset và đặt `CODE_BUNDLE="auto"`; xem [Source bundle](#source-bundle). Không cần GitHub token.
3. Đặt `SCENARIO`, `START_RATE`, `MAX_RATE` và `DURATION`. Mặc định: `all`, 50 và 6,400 iteration/s, `60s`. Giữ nguyên mặc định nếu đây là baseline để phase 2 so sánh.
4. Chọn **Save Version → Save & Run All (Commit)**. Đọc báo cáo cuối cùng và tải các file từ **Output**.

Một baseline gồm hai version như vậy chạy trên cùng mã nguồn: máy Kaggle không giống nhau, và độ chênh giữa hai lần chạy cho biết bao nhiêu phần của một khác biệt về sau chỉ là nhiễu. Ghi lại scenario nào có max sustainable throughput, hoặc p95 ở cùng một step, chênh hơn 10% giữa hai lần chạy. Một version đã lưu sẽ tự chạy tiếp mà không cần theo dõi; chạy đủ `all` mất khoảng một giờ. Trình soạn thảo tương tác tự dừng sau 20 phút không hoạt động. Khi khởi động thất bại, notebook in ra lời giải thích hoặc phần cuối của log service.

## Đọc kết quả

Với mỗi scenario, notebook đăng nhập các khách hàng của nó một lần, chạy warm-up không đo ở `START_RATE`, rồi chạy mỗi tốc độ một step: 50, 100, 200, cứ thế gấp đôi lên tới `MAX_RATE`. Notebook dừng ở step đầu tiên bị hỏng và chạy thêm một step ở giữa step cuối cùng còn giữ được và step bị hỏng đó.

Một step bị hỏng khi một trong các điều kiện sau đúng trên toàn bộ thời lượng của nó:

| Quy tắc | Hỏng khi |
|---|---|
| p95 của các request được đo | trên 200 ms |
| Request được đo lỗi ngoài dự kiến | trên 1%; `409` của contention và race là lỗi dự kiến |
| Check đạt | dưới 99% |
| Dropped iteration: các lượt đến mà k6 không khởi chạy được trong giới hạn VU | bất kỳ |
| Số iteration/s thực đạt | dưới 95% mục tiêu |

Step cao nhất còn giữ được là **max sustainable throughput** của scenario. Một step `book` hết ghế đã seed được báo là exhausted chứ không phải hỏng, và kết thúc scenario. Latency tính theo từng request; một workflow đặt vé gồm hai request. `report.py` in bảng các step, rồi in telemetry của step hỏng đầu tiên, cho thấy tầng nào quá tải. Nếu tại step đó chính k6 dùng từ 90% core của nó trở lên, báo cáo sẽ cảnh báo rằng giới hạn có thể nằm ở k6. Kiểm tra phần cứng và cấu hình runtime trong `env.txt` khi so sánh các lần chạy.

| Output trong `results/` | Nội dung |
|---|---|
| `<scenario>/<rate>/summary.json` | Metric k6 của một step; k6 chạy trên máy ghi thẳng ra `summary.json` trừ khi đặt `RESULTS_DIR` |
| `<scenario>/<rate>/telemetry.csv` | Chỉ có trên notebook: mỗi giây, CPU của java, postgres và k6 (% của một core), iowait, Hikari active/pending/acquire, số thread Tomcat đang bận, GC pause, và số backend PostgreSQL đang hoạt động theo loại wait |
| `<scenario>/<rate>/statements.csv` | Chỉ có trên notebook: 20 câu SQL tốn tổng thời gian nhất (`pg_stat_statements`) |
| `<scenario>/<rate>/database.json` | Chỉ có trên notebook: số deadlock và transaction bị rollback trong step |
| `<scenario>/<rate>/verification.txt` | Chỉ có trên notebook, với contention và race: đối chiếu số đếm HTTP với số dòng SQL, kiểm tra trùng ghế |
| `<scenario>/<rate>/k6.log`, `<scenario>/warmup/` | Output k6 của step; kết quả của warm-up |
| `env.txt` | Chỉ có trên notebook: định danh mã nguồn, phần cứng, CPU affinity, JVM, cấu hình PostgreSQL và pool |
| `app.log` | Log service do notebook lưu |
| `charts/` | Chỉ có trên notebook: các biểu đồ mô tả bên dưới cho lần chạy này |

Bộ lấy mẫu gửi thêm vài request actuator và một truy vấn SQL mỗi giây. Nếu kiểm tra SQL thất bại, notebook sẽ báo lỗi sau khi in báo cáo. **Giữ báo cáo và output thô ở máy; không commit chúng.**

### Biểu đồ

`charts.py` vẽ mỗi scenario một file PNG, cộng thêm `summary.png` là max sustainable throughput của mọi scenario. Các panel của một scenario dùng chung trục tốc độ: throughput so với mục tiêu, p50/p95/p99, CPU theo process, kết nối Hikari, wait của PostgreSQL, và với contention và race thì thêm số 409 theo lý do. Marker tô đặc là step giữ được, marker rỗng màu đỏ là step bị hỏng, marker xám là step hết ghế. Script cần matplotlib (`pip install matplotlib`); `report.py` thì không cần, và nếu vẽ biểu đồ thất bại trong notebook thì chỉ in ra cảnh báo.

Với một baseline, vẽ chồng cả hai lần chạy lên nhau; kiểu đường cho biết lần chạy nào. Các file PNG này là output duy nhất được đưa vào repo:

```bash
python3 loadtest/charts.py --out docs/loadtest baseline-1=<run 1>/results baseline-2=<run 2>/results
```

## Dữ liệu seed và giới hạn

`seed.sql` tạo 3 rạp, 12 phòng chiếu mỗi phòng 120 ghế, 20 phim và 4,500 suất chiếu trải trên 75 ngày, bắt đầu từ ngày mai theo giờ Việt Nam. Script cũng tạo 6,000 khách hàng: `loadtest-{1..6000}@loadtest.local` / `loadtest-password`. Các tài khoản dùng chung một BCrypt hash tính sẵn; đăng nhập vẫn kiểm tra mật khẩu. Chạy lại seed sẽ insert dữ liệu thêm lần nữa, nên mỗi database mới chỉ chạy seed một lần. Trên Kaggle, seed được chạy tự động.

- Browse chọn hôm nay hoặc ngày mai, ngày nào có suất chiếu trước. Bước chuẩn bị thất bại nếu cả hai ngày đều trống; mỗi sơ đồ ghế phải có 120 ghế.
- Mọi scenario cho phép tối đa 2,000 VU. Trong `account`, `book`, `contention` và `race`, mỗi VU là một khách hàng, nên file token chứa 2,000 token. Token có hiệu lực một giờ; notebook đăng nhập lại sau 50 phút.
- 40 suất chiếu đầu tiên được dành riêng: contention và race lấy một suất mới cho mỗi step, nên mỗi scenario có tối đa 20 step. Book cấp cho mỗi iteration của lần chạy một cặp riêng trong 535,200 cặp suất chiếu/ghế còn lại. Warm-up và các lần gấp đôi tới 3,200 iteration/s dùng 384,000 cặp, vẫn đủ cho một step giữa ở 2,400, nên chỉ step 6,400 mới có thể hết ghế.
- Contention và race chọn 10 ghế còn trống. Chúng đếm các xung đột ghế, các lượt bị từ chối vì khách hàng đã có một đơn Pending, các `409 try again` sau khi một đơn đặt vé thua deadlock hai lần, và các xung đột ghế không liệt kê ghế nào vì bên thắng đã bỏ đơn trước khi bên thua kịp xem. Book đếm `409 try again` của nó ở cột `busy 409` của báo cáo. Kiểm tra SQL yêu cầu số dòng ghế đã đặt bằng số `201` trừ số lần bỏ đơn, và không ghế nào bị đặt hai lần. Cả hai scenario đều không đồng bộ request; ở tốc độ cao các request tự chồng lên nhau. `BookingPersistenceTest` có một race đồng bộ trong integration test.

## Source bundle

Từ thư mục gốc của repository, chọn một đường dẫn output mới nằm ngoài repository:

```bash
python3 loadtest/bundle.py -o "<new-output-path>/source.zip"
```

Helper chép các file được track và các file untracked không bị ignore vào file ZIP, giữ nguyên quyền thực thi. Helper bỏ qua `.git/`, `target/`, `.env*`, `secrets/`, symlink và các file đặc biệt, và không ghi đè lên output đã có. Hãy kiểm tra xem còn secret nào lưu dưới tên file khác không, rồi upload lên một private Dataset.

`CODE_BUNDLE="auto"` yêu cầu đúng một thư mục đã giải nén trong `/kaggle/input` chứa `loadtest-bundle.json`; các alias trỏ tới cùng một thư mục chỉ tính một lần. Bạn cũng có thể chỉ định trực tiếp một file ZIP hoặc đường dẫn gốc đã giải nén. Thư mục gốc phải chứa manifest nằm cạnh `pom.xml`, `mvnw`, `.mvn/`, `src/` và `loadtest/`. Kiểm tra đường dẫn được mount thực tế thay vì đoán từ tên dataset.

Manifest ghi `HEAD` vào `sourceCommit`, nhưng bundle có thể chứa cả thay đổi chưa commit. Hãy đối chiếu hash trong `env.txt` với snapshot của bạn: với input ZIP, hash tính trên byte của file nén; với input đã giải nén, hash tính trên đường dẫn đã sắp xếp, cờ thực thi và byte. Input từ Git không có bundle hash. Notebook giữ mã nguồn bên ngoài output được lưu.

<details>
<summary>Kaggle CLI: cập nhật một private Dataset và notebook đã có</summary>

Dùng Kaggle CLI 2.2.4 đã xác thực. Đặt các thư mục staging nằm ngoài repository và thay các placeholder:

```bash
export DATASET_DIR=...
export KERNEL_DIR=...
export KERNEL_REF=...          # owner/notebook
export SOURCE_DATASET_REF=...  # owner/dataset
export OUT_DIR=...
mkdir -p "$DATASET_DIR" "$KERNEL_DIR" "$OUT_DIR"
kaggle kernels pull "$KERNEL_REF" -p "$KERNEL_DIR" -m
```

1. Đặt `dataset-metadata.json` vào `DATASET_DIR`, với `id` trùng `SOURCE_DATASET_REF`. Dùng metadata upload dạng phẳng, không dùng lớp bọc `info` mà `kaggle datasets metadata` trả về.
2. Chép notebook của repository vào `KERNEL_DIR`, đặt `CODE_BUNDLE="auto"` và các tham số chạy.
3. Cập nhật `kernel-metadata.json`: trỏ `code_file` tới notebook; giữ nguyên các trường định danh; đặt `dataset_sources` là `["<owner>/<dataset>"]`, không kèm số version. Đặt `is_private: true`, `enable_gpu: false`, `enable_tpu: false`, `enable_internet: true`. Không bao giờ đưa credential vào.
4. Build và publish mã nguồn. Thư mục upload chỉ nên chứa file ZIP và metadata:

```bash
python3 loadtest/bundle.py -o "$DATASET_DIR/source.zip"
kaggle datasets version -p "$DATASET_DIR" -m "load-test source snapshot"
kaggle datasets status "$SOURCE_DATASET_REF" --format 'json(status,current_version_number)'
```

Chờ tới khi version được tạo và trạng thái là `ready` với số version mới. Giữ nguyên cả hai tài nguyên cho tới khi kiểm tra output xong; notebook gắn version mới nhất của dataset. Tạo version mới vẫn giữ dataset ở chế độ private.

```bash
kaggle kernels push -p "$KERNEL_DIR"
kaggle kernels logs -f "$KERNEL_REF"
kaggle kernels status "$KERNEL_REF"
```

Ghi lại số version từ dòng `Kernel version N successfully pushed`. Xem xét mọi cảnh báo hoặc lỗi khi upload trước khi đi tiếp. Chờ tới khi trạng thái là `COMPLETE`; lệnh status có thể trả về exit code 0 ngay cả khi trạng thái là `ERROR`.

```bash
kaggle kernels output "$KERNEL_REF" -p "$OUT_DIR"
```

Đối chiếu `env.txt` với source commit của bạn và đúng loại hash tương ứng. Các lệnh logs/status/output của CLI dùng reference gốc và version mới nhất; với kết quả cũ hơn, dùng tab **Output** của version đã lưu. URL bằng chứng có dạng `/code/<owner>/<slug>/versions/<N>` và `/datasets/<owner>/<slug>/versions/<M>`; hai số version này độc lập với nhau. Giữ nguyên chế độ riêng tư và các version cũ; không bật GPU/TPU.

</details>

## Thêm scenario

Thêm `k6/<name>.js`, rồi thêm tên scenario vào `SCENARIOS` của notebook và vào `SCENARIOS` trong `report.py`.

Dùng lại `summaryOptions`, `arrivalRate()`, các request tag `LOAD` và `handleSummary` trong `lib/summary.js`. Đọc `BASE_URL` từ `__ENV`; dùng `savedTokens()` trong `lib/customers.js` cho các khách hàng đã seed, và cấp cho scenario một khối riêng trong `FIRST_CUSTOMER` của notebook nếu scenario để lại đơn Pending.
