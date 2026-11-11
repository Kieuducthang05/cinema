# Load test

English | [Tiếng Việt](README.vi.md)

Find the highest rate each workflow sustains and which tier gives out first, by running k6 against the service and PostgreSQL in a Kaggle CPU notebook (or locally, one step at a time). The method is in [ADR 0007](../docs/adr/0007-capacity-baseline-by-stepped-arrival-rate-runs.md). [loadtest-baseline.md](../docs/loadtest-baseline.md) has the current baseline, from [Run 1, canonical version 8](https://www.kaggle.com/code/tunichdi/se-phase-01-issue19-baseline/versions/8) and [Run 2, independent version 1](https://www.kaggle.com/code/tunichdi/se-phase-01-capacity-baseline-2/versions/1).

## Scenarios

An iteration is one workflow. A virtual user (VU) is a k6 worker. A step is one k6 run at one constant rate.

| Scenario | Workflow | Requests/iteration | Customers |
|---|---|---:|---|
| `account` | Read the signed-in Customer's Account | 1 | 1..2000 |
| `browse` | List Showtimes, open a seat map | 2 | none |
| `book` | Hold one unused Seat, pay | 2 | 1..2000 |
| `contention` | Try to hold one of the same 10 Seats, which stay held | 1 | 2001..4000 |
| `race` | Like contention, but a winner abandons at once, so attempts race for free Seats | 1 | 4001..6000 |

`SCENARIO="all"` runs them in that order. Contention measures the cost of meeting a held Seat; race measures Customers racing for the same free Seat. Setup requests, including login, are excluded from measured requests, and so is the abandon after a race win.

## Run locally

Start the service and PostgreSQL using the main README. Install the k6 version pinned by `K6_VERSION` in `kaggle.ipynb`.

**Use a fresh test database.** Run the seed once, after the service is healthy and Flyway has applied the migrations. A local run is one step; only the notebook steps through rates and samples telemetry.

```bash
psql -v ON_ERROR_STOP=1 "<your postgres url for the cinema database>" -f loadtest/seed.sql
mkdir -p loadtest/results
k6 run -e COUNT=200 loadtest/k6/login.js             # saves loadtest/results/tokens.json
k6 run -e RATE=20 -e DURATION=10s loadtest/k6/book.js
python3 loadtest/report.py
```

`browse` needs no tokens. For `contention` or `race`, log in their own Customers first (`-e FIRST=2001` or `-e FIRST=4001`) and pass `-e STEP=<n>` so each run uses a fresh Showtime. For a second `book` run, pass `-e SLOT_OFFSET=<iterations already run>` so it does not reuse Seats. Run the helper tests with `python3 loadtest/test_loadtest.py` (Python and Node required).

## Run on Kaggle

The notebook installs PostgreSQL, builds the service as a JAR and runs k6; Docker is unavailable. k6 runs on one CPU, the service and PostgreSQL on the rest. See [ADR 0003](../docs/adr/0003-load-test-on-kaggle-without-docker.md).

1. Import `loadtest/kaggle.ipynb`. Set **Accelerator: None**, **Internet: On** and complete any required account verification.
2. Choose the source:
   - Git: set `GIT_REF` and leave `CODE_BUNDLE=None`. Attach `GITHUB_TOKEN` in **Add-ons → Secrets**, with **Contents: Read-only** access to `14m6d4/se-phase-01`.
   - Local changes: attach a private source Dataset and set `CODE_BUNDLE="auto"`; see [Source bundle](#source-bundle). No GitHub token needed.
3. Set `SCENARIO`, `START_RATE`, `MAX_RATE` and `DURATION`. Defaults: `all`, 50 and 6,400 iterations/s, `60s`. Keep the defaults for a baseline that phase 2 compares against.
4. Select **Save Version → Save & Run All (Commit)**. Read the final report and download files from **Output**.

A baseline is two such versions of the same source: Kaggle machines vary, and the spread between the two runs shows how much of a later difference is noise. Note any scenario whose max sustainable throughput, or p95 at the same step, differs by more than 10% between the two. A saved run continues unattended; a full `all` run takes about an hour. The interactive editor stops after 20 minutes idle. Startup failures print an explanation or service log tail.

## Read the results

For each scenario the notebook logs in its Customers once, runs an unmeasured warm-up at `START_RATE`, then one step per rate: 50, 100, 200 and so on, doubling up to `MAX_RATE`. It stops at the first step that breaks and runs one more step halfway between the last step that held and that one.

A step breaks when any of these is true over its whole duration:

| Rule | Breaks when |
|---|---|
| p95 of measured requests | above 200 ms |
| Measured requests that failed unexpectedly | above 1%; contention and race `409`s are expected |
| Checks that passed | below 99% |
| Dropped iterations: arrivals k6 could not start within the VU limit | any |
| Achieved iterations/s | below 95% of the target |

The highest step that held is the scenario's **max sustainable throughput**. A `book` step that runs out of seeded Seats is reported as exhausted, not broken, and ends the scenario. Latency is per request; a booking workflow takes two. `report.py` prints the step table, then the telemetry of the first step that broke, which shows the tier that gave out. If k6 itself used 90% or more of its core there, the report warns that the limit may be k6's. Check hardware and runtime settings in `env.txt` when comparing runs.

| Output under `results/` | Contents |
|---|---|
| `<scenario>/<rate>/summary.json` | k6 metrics of one step; local k6 writes `summary.json` directly unless `RESULTS_DIR` is set |
| `<scenario>/<rate>/telemetry.csv` | Notebook only: every second, CPU of java, postgres and k6 (% of one core), iowait, Hikari active/pending/acquire, Tomcat busy threads, GC pause, and PostgreSQL active backends by wait type |
| `<scenario>/<rate>/statements.csv` | Notebook only: top 20 SQL statements by total time (`pg_stat_statements`) |
| `<scenario>/<rate>/database.json` | Notebook only: deadlocks and rolled-back transactions during the step |
| `<scenario>/<rate>/verification.txt` | Notebook only, contention and race: HTTP counts versus SQL rows, duplicate check |
| `<scenario>/<rate>/k6.log`, `<scenario>/warmup/` | k6 output of the step; the warm-up's results |
| `env.txt` | Notebook only: source identity, hardware, CPU affinity, JVM, PostgreSQL and pool settings |
| `app.log` | Service log saved by the notebook |
| `charts/` | Notebook only: the charts below for this run |

The sampler adds a few actuator requests and one SQL query per second. A failed SQL verification fails the notebook after the report. **Keep reports and raw outputs local; do not commit them.**

### Charts

`charts.py` draws one PNG per scenario and `summary.png`, the max sustainable throughput of every scenario. A scenario's panels share the rate axis: throughput against the target, p50/p95/p99, CPU per process, Hikari connections, PostgreSQL waits and, for contention and race, 409s by reason. A filled marker held, a hollow red one broke, a gray one ran out of Seats. It needs matplotlib (`pip install matplotlib`); `report.py` does not, and a chart failure in the notebook only prints a warning.

For a baseline, overlay both runs; the line style tells them apart. These PNGs are the only output that goes into the repo:

```bash
python3 loadtest/charts.py --out docs/loadtest baseline-1=<run 1>/results baseline-2=<run 2>/results
```

## Seed and limits

`seed.sql` creates 3 Cinemas, 12 Auditoriums with 120 Seats each, 20 Movies and 4,500 Showtimes across 75 days starting tomorrow in Vietnam time. It also creates 6,000 Customers: `loadtest-{1..6000}@loadtest.local` / `loadtest-password`. They share a precomputed BCrypt hash; login still verifies passwords. Rerunning the seed inserts data again, so use it only once per fresh database. Kaggle runs it automatically.

- Browse chooses today or tomorrow, whichever first has Showtimes. Setup fails if both are empty; seat maps must contain 120 Seats.
- Every scenario allows up to 2,000 VUs. In `account`, `book`, `contention` and `race` each VU is one Customer, so a token file holds 2,000 tokens. Tokens last an hour; the notebook logs in again after 50 minutes.
- The first 40 Showtimes are reserved: contention and race take a fresh one per step, so each gets at most 20 steps. Book gives every iteration of the run its own pair from the other 535,200 Showtime/Seat pairs. Warm-up and doublings through 3,200 iterations/s use 384,000, enough for a 2,400 midpoint too, so only a 6,400 step can run out.
- Contention and race pick 10 available Seats. They count seat conflicts, rejections because a Customer already has a Pending Booking, `409 try again` after a Booking lost a deadlock twice, and seat conflicts that list no Seat because the winner abandoned before the loser looked. Book counts its `409 try again` in the report's `busy 409` column. The SQL check needs booked-seat rows to equal 201s minus abandons, with no Seat booked twice. Neither synchronises requests; at higher rates they overlap on their own. `BookingPersistenceTest` covers a synchronised race in the integration tests.

## Source bundle

From the repository root, choose a new output path outside the repository:

```bash
python3 loadtest/bundle.py -o "<new-output-path>/source.zip"
```

The helper copies tracked and untracked non-ignored files into the ZIP and keeps executable bits. It skips `.git/`, `target/`, `.env*`, `secrets/`, symlinks and special files, and will not overwrite an existing output. Check for secrets stored under other filenames, then upload to a private Dataset.

`CODE_BUNDLE="auto"` requires one expanded directory under `/kaggle/input` containing `loadtest-bundle.json`; aliases to the same directory count once. You can also give an explicit ZIP or expanded-root path. The root must contain the manifest beside `pom.xml`, `mvnw`, `.mvn/`, `src/` and `loadtest/`. Check the mounted path instead of guessing it from the dataset name.

The manifest records `HEAD` as `sourceCommit`, but the bundle can include uncommitted changes. Match the hash in `env.txt` to your snapshot: ZIP input hashes archive bytes; expanded input hashes sorted paths, executable flags and bytes. Git input has no bundle hash. The notebook keeps the source outside saved output.

<details>
<summary>Kaggle CLI: update an existing private Dataset and notebook</summary>

Use authenticated Kaggle CLI 2.2.4. Set staging directories outside the repository and replace the placeholders:

```bash
export DATASET_DIR=...
export KERNEL_DIR=...
export KERNEL_REF=...          # owner/notebook
export SOURCE_DATASET_REF=...  # owner/dataset
export OUT_DIR=...
mkdir -p "$DATASET_DIR" "$KERNEL_DIR" "$OUT_DIR"
kaggle kernels pull "$KERNEL_REF" -p "$KERNEL_DIR" -m
```

1. Put `dataset-metadata.json` in `DATASET_DIR`, with `id` matching `SOURCE_DATASET_REF`. Use flat upload metadata, not the `info` wrapper returned by `kaggle datasets metadata`.
2. Copy the repository notebook into `KERNEL_DIR`, set `CODE_BUNDLE="auto"` and the run parameters.
3. Update `kernel-metadata.json`: point `code_file` to the notebook; keep its identity fields; set `dataset_sources` to `["<owner>/<dataset>"]` without a version number. Set `is_private: true`, `enable_gpu: false`, `enable_tpu: false`, `enable_internet: true`. Never include credentials.
4. Build and publish the source. The upload directory should contain only the ZIP and metadata:

```bash
python3 loadtest/bundle.py -o "$DATASET_DIR/source.zip"
kaggle datasets version -p "$DATASET_DIR" -m "load-test source snapshot"
kaggle datasets status "$SOURCE_DATASET_REF" --format 'json(status,current_version_number)'
```

Wait for version creation and `ready` with the new version number. Keep both resources unchanged until output verification finishes; the notebook attaches the latest dataset version. Versioning preserves dataset privacy.

```bash
kaggle kernels push -p "$KERNEL_DIR"
kaggle kernels logs -f "$KERNEL_REF"
kaggle kernels status "$KERNEL_REF"
```

Record the version number from `Kernel version N successfully pushed`. Investigate upload warnings or errors before continuing. Wait until the status says `COMPLETE`; the status command can return exit code 0 even for `ERROR`.

```bash
kaggle kernels output "$KERNEL_REF" -p "$OUT_DIR"
```

Verify `env.txt` against your source commit and matching hash type. CLI logs/status/output use the base reference and latest version; use the saved version's **Output** tab for older results. Evidence URLs end in `/code/<owner>/<slug>/versions/<N>` and `/datasets/<owner>/<slug>/versions/<M>`; their version numbers are independent. Keep existing privacy and older versions; do not enable GPU/TPU.

</details>

## Add a scenario

Add `k6/<name>.js`, then add its name to the notebook's `SCENARIOS` and to `SCENARIOS` in `report.py`.

Reuse `summaryOptions`, `arrivalRate()`, the `LOAD` request tags and `handleSummary` from `lib/summary.js`. Read `BASE_URL` from `__ENV`; use `savedTokens()` from `lib/customers.js` for seeded Customers, and give the scenario its own block in the notebook's `FIRST_CUSTOMER` if it leaves Pending Bookings.
