"""Small assert checks for k6 allocation, contracts, Kaggle SQL, and source bundles."""
import ast
import glob
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path, PurePosixPath
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]


def node(source):
    subprocess.run(["node", "--input-type=module", "-e", source], cwd=ROOT, check=True)


def test_report_verdict():
    sys.dont_write_bytecode = True  # keep __pycache__ out of the repository
    sys.path.insert(0, str(ROOT / "loadtest"))
    from report import seconds, verdict

    def summary(rate=100, iterations=6_000, p95=50.0, failed=0.0, checks=1.0, dropped=None, exhausted=None):
        metrics = {
            "iterations": {"values": {"count": iterations}},
            "http_reqs{phase:load}": {"values": {"count": 2 * iterations}},
            "http_req_duration{phase:load}": {"values": {"med": 5.0, "p(95)": p95, "p(99)": 2 * p95}},
            "http_req_failed{phase:load}": {"values": {"rate": failed}},
            "checks": {"values": {"rate": checks}},
        }
        if dropped:
            metrics["dropped_iterations"] = {"values": {"count": dropped}}
        if exhausted:
            metrics["seats_exhausted"] = {"values": {"count": exhausted}}
        return {"load": {"rate": rate, "duration": "1m"}, "metrics": metrics}

    assert [seconds(d) for d in ("60s", "2m", "1m30s", "1h")] == [60, 120, 90, 3600]
    held = verdict(summary())
    assert held["status"] == "held" and held["reasons"] == []
    assert held["achieved"] == 100 and held["rps"] == 200
    for broken, reason in [
        (summary(p95=200.1), "p95"),
        (summary(failed=0.011), "errors"),
        (summary(checks=0.98), "checks"),
        (summary(dropped=1), "dropped"),
        (summary(iterations=5_600), "achieved"),
    ]:
        step = verdict(broken)
        assert step["status"] == "broke" and len(step["reasons"]) == 1 and reason in step["reasons"][0], step
    # Running out of seeded Seats ends the scenario without counting as a break.
    assert verdict(summary(exhausted=900))["status"] == "exhausted"


def fake_results(root, slowdown=1.0):
    """A capacity run's results/ in which every scenario holds up to 800 it/s and breaks at 1,600."""
    for scenario in ["account", "browse", "book", "contention", "race"]:
        for rate in [50, 100, 200, 400, 800, 1600, 1200]:
            load = min(rate, 1100 / slowdown) / 1100
            achieved = rate if rate <= 800 else 1100 / slowdown
            p95 = 8 + 400 * load ** 6
            iterations = 60 * achieved
            metrics = {
                "iterations": {"values": {"count": iterations}},
                "http_reqs{phase:load}": {"values": {"count": 2 * iterations}},
                "http_req_duration{phase:load}": {"values": {"med": p95 / 3, "p(95)": p95, "p(99)": 1.8 * p95}},
                "http_req_failed{phase:load}": {"values": {"rate": 0.0}},
                "checks": {"values": {"rate": 1.0}},
                "seat_conflicts": {"values": {"count": 0.8 * iterations}},
                "pending_customer_conflicts": {"values": {"count": 0.05 * iterations}},
                "busy_conflicts": {"values": {"count": 0.1 * iterations * load}},
                "free_again_conflicts": {"values": {"count": 0.02 * iterations}},
            }
            if rate == 1600:
                metrics["dropped_iterations"] = {"values": {"count": 500}}
            step = root / scenario / str(rate)
            step.mkdir(parents=True)
            (step / "summary.json").write_text(json.dumps({"load": {"rate": rate, "duration": "60s"},
                                                           "metrics": metrics}))
            rows = ["t,cpu_java,cpu_postgres,cpu_k6,iowait,hikari_active,hikari_pending,hikari_acquire_count,"
                    "hikari_acquire_total_ms,tomcat_busy,gc_pause_count,gc_pause_total_ms,pg_active,pg_lock,"
                    "pg_lwlock,pg_io,pg_other"]
            for t in range(3):
                rows.append(f"{t},{280 * load},{120 * load},{40 + 55 * load},0.5,{10 * load},{30 * load ** 4},"
                            f"{t * iterations},{t * iterations * 0.2},{150 * load},{t},{t * 5},{6 * load},"
                            f"{3 * load ** 3},{load},0.2,0.1")
            (step / "telemetry.csv").write_text("\n".join(rows) + "\n")


def test_charts():
    try:
        import matplotlib  # noqa: F401
    except ImportError:
        print("SKIP  charts: matplotlib is not installed")
        return
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        fake_results(root / "one")
        fake_results(root / "two", slowdown=1.1)
        out = root / "charts"
        subprocess.run([sys.executable, "-B", str(ROOT / "loadtest/charts.py"), "--out", str(out),
                        f"baseline-1={root / 'one'}", f"baseline-2={root / 'two'}"], check=True,
                       capture_output=True)
        pngs = {path.name: path.stat().st_size for path in out.glob("*.png")}
        assert set(pngs) == {f"{name}.png" for name in ["account", "browse", "book", "contention", "race", "summary"]}
        assert all(size > 10_000 for size in pngs.values()), pngs


def test_seat_slots():
    source = (ROOT / "loadtest/k6/lib/seat-allocation.js").read_text()
    node(source + """
import assert from 'node:assert/strict';
const showtimes = [{ id: 's1', auditoriumId: 'a' }, { id: 's2', auditoriumId: 'b' }, { id: 's3', auditoriumId: 'a' }];
const seats = { a: Array.from({ length: 120 }, (_, i) => `a${i}`), b: Array.from({ length: 120 }, (_, i) => `b${i}`) };
const slots = Array.from({ length: 360 }, (_, n) => slotAt(n, showtimes, seats));
assert.deepEqual(slots[0], { showtimeId: 's1', seatId: 'a0' });
assert.deepEqual(slots[121], { showtimeId: 's2', seatId: 'b1' });
assert.deepEqual(slots[359], { showtimeId: 's3', seatId: 'a119' });
assert.equal(new Set(slots.map((slot) => slot.showtimeId + slot.seatId)).size, 360);
assert.equal(slotAt(360, showtimes, seats), null);
""")


def test_conflicts():
    source = (ROOT / "loadtest/k6/lib/conflicts.js").read_text()
    node(source + """
import assert from 'node:assert/strict';
const seat = '550e8400-e29b-41d4-a716-446655440000';
const other = '11111111-1111-4111-8111-111111111111';
const seatTaken = {
	type: 'about:blank', title: 'Conflict', status: 409,
	detail: 'Seat already taken: [550e8400-e29b-41d4-a716-446655440000]',
	seatIds: ['550e8400-e29b-41d4-a716-446655440000'],
};
const pendingBooking = {
	type: 'about:blank', title: 'Conflict', status: 409,
	detail: 'Customer already has a Pending Booking 550e8400-e29b-41d4-a716-446655440000',
};
assert.equal(conflictReason(409, 'application/problem+json', seatTaken, seat), 'seat');
assert.equal(conflictReason(409, 'APPLICATION/PROBLEM+JSON; charset=UTF-8', seatTaken, seat), 'seat');
assert.equal(conflictReason(409, 'application/problem+json', pendingBooking, seat), 'pending_customer');
assert.equal(conflictReason(409, 'application/problem+json',
	{ status: 409, detail: 'Seats are being booked by others right now, try again' }, seat), 'busy');
assert.equal(conflictReason(409, 'application/problem+json', { ...seatTaken, seatIds: [other] }, seat), null);
// The winner abandoned before the loser re-read the Seats (JpaBookingRepository#insert).
assert.equal(conflictReason(409, 'application/problem+json', { ...seatTaken, seatIds: [] }, seat), 'free_again');
assert.equal(conflictReason(409, 'application/problem+json', { ...seatTaken, seatIds: [seat] }, other), null);
assert.equal(conflictReason(409, 'application/problem+json', { ...pendingBooking, seatIds: [other] }, seat), null);
assert.equal(conflictReason(409, 'application/problem+json', { status: 409, detail: 'Seat already taken' }, seat), null);
assert.equal(conflictReason(409, 'application/problem+json', { status: 409, detail: 'unexpected' }, seat), null);
assert.equal(conflictReason(409, 'application/problem+json', { status: 400, detail: 'Seat already taken' }, seat), null);
assert.equal(conflictReason(200, 'application/problem+json', seatTaken, seat), null);
assert.equal(conflictReason(409, undefined, seatTaken, seat), null);
assert.equal(conflictReason(409, 'text/plain', seatTaken, seat), null);
assert.equal(conflictReason(409, 'application/problem+jsonx', seatTaken, seat), null);
assert.equal(conflictReason(409, 'application/problem', seatTaken, seat), null);
assert.equal(conflictReason(409, 'application/problem+json;', seatTaken, seat), 'seat');
assert.equal(conflictReason(409, 'application/problem+json; charset=', seatTaken, seat), 'seat');
assert.equal(conflictReason(409, 'application/problem+json; profile="https://example.invalid/a;b"', seatTaken, seat), 'seat');
assert.equal(conflictReason(409, 'Application/Problem+Json ; charset=UTF-8; profile="a;b"; x=y', seatTaken, seat), 'seat');
assert.equal(conflictReason(409, 'application/problem+json', null, seat), null);
assert.equal(conflictReason(409, 'application/problem+json', { status: 409, seatIds: seat }, seat), null);
""")


def test_browse_setup():
    source = (ROOT / "loadtest/k6/browse.js").read_text()
    source = re.sub(r"^import .*$", "", source, flags=re.MULTILINE)
    source = source.replace("export { handleSummary };", "")
    source = source.replace("export default function ({ date }) {", "function browseIteration({ date }) {")
    source = source.replace("export const ", "const ").replace("export function ", "function ")
    node("""
import assert from 'node:assert/strict';
const __ENV = {};
const LOAD = { tags: { phase: 'load' } };
const summaryOptions = { thresholds: {} };
function arrivalRate() { return {}; }
function handleSummary() { return {}; }
function fail(message) { throw new Error(message); }
function check(value, assertions) {
	return Object.values(assertions).every((assertion) => assertion(value));
}
const start = Date.UTC(2026, 9, 20, 10, 0, 0);
const expectedToday = new Date(start + 7 * 3600 * 1000).toISOString().slice(0, 10);
const expectedTomorrow = new Date(start + (7 + 24) * 3600 * 1000).toISOString().slice(0, 10);
const requests = [];
const http = {
	get(url, params) {
		requests.push({ url, params });
		if (url.includes('/seats')) {
			return { status: 200, body: '', json: () => Array.from({ length: 120 }, () => ({})) };
		}
		const date = new URL(url).searchParams.get('date');
		return { status: 200, body: '', json: () => (date === expectedTomorrow ? [{ id: 'showtime-1' }] : []) };
	},
};
globalThis.__VU = 1;
globalThis.__ITER = 0;
""" + source + """
Date.now = () => start;
const setupData = setup();
assert.deepEqual(setupData, { date: expectedTomorrow });
assert.equal(requests.length, 2);
assert.ok(requests.every((request) => request.params === undefined));
const beforeIteration = requests.length;
Date.now = () => start + 24 * 3600 * 1000;  // midnight may pass after setup
browseIteration(setupData);
const measured = requests.slice(beforeIteration);
assert.equal(measured.length, 2);
assert.equal(measured[0].params, LOAD);
assert.equal(measured[1].params, LOAD);
assert.ok(measured[0].url.endsWith(`date=${expectedTomorrow}`));
assert.ok(measured[1].url.endsWith('/api/showtimes/showtime-1/seats'));
assert.notEqual(expectedToday, expectedTomorrow);
""")


def test_showtimes_scan():
    source = (ROOT / "loadtest/k6/lib/showtimes.js").read_text()
    source = re.sub(r"^import .*$", "", source, flags=re.MULTILINE)
    source = source.replace("export function ", "function ").replace("export const ", "const ")
    node("""
import assert from 'node:assert/strict';
const __ENV = {};
let clock = Date.UTC(2026, 9, 20, 16, 59, 59);  // 23:59:59 on a VN-local date
const start = clock;
Date.now = () => clock;
function fail(message) { throw new Error(message); }
const urls = [];
const http = {
	get(url) {
		urls.push(url);
		clock += 1000;  // make a per-request Date.now() cross VN midnight
		return { status: 200, body: '', json: () => [{ id: url }] };
	},
};
""" + source + """
const showtimes = seededShowtimes();
const dates = urls.map((url) => new URL(url).searchParams.get('date'));
const expected = Array.from({ length: SEED_DAYS + 2 }, (_, day) =>
	new Date(start + (7 + day * 24) * 3600 * 1000).toISOString().slice(0, 10));
assert.deepEqual(dates, expected);
assert.equal(new Set(dates).size, SEED_DAYS + 2);
assert.equal(showtimes.length, SEED_DAYS + 2);
""")


def test_notebook_cells():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    ids = [cell.get("id") for cell in notebook["cells"]]
    assert all(isinstance(cell_id, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", cell_id)
               for cell_id in ids)
    assert len(ids) == len(set(ids))
    assert {"c04", "c06", "c08", "c10a", "c12"} <= set(ids)
    seed = next(cell for cell in notebook["cells"] if cell.get("id") == "c10a")
    assert any("loadtest/seed.sql" in line for line in seed["source"])
    for cell in notebook["cells"]:
        if cell["cell_type"] == "code":
            ast.parse("".join(cell["source"]))


def test_notebook_scenarios():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    source = "".join(next(cell for cell in notebook["cells"] if cell.get("id") == "c02")["source"])
    expected = ["account", "browse", "book", "contention", "race"]
    # login.js only saves tokens for the scenarios.
    assert set(expected) | {"login"} == {path.stem for path in (ROOT / "loadtest/k6").glob("*.js")}
    for selected in ("all", *expected, "unknown"):
        parameters = re.sub(r'^SCENARIO = .*$', f'SCENARIO = {selected!r}', source, flags=re.MULTILINE)
        result = subprocess.run([sys.executable, "-c", parameters + "\nprint(RUN_SCENARIOS)"],
                                text=True, capture_output=True)
        if selected == "unknown":
            assert result.returncode != 0 and "Unknown SCENARIO" in result.stderr
        else:
            assert result.returncode == 0, result.stderr
            assert ast.literal_eval(result.stdout.strip()) == (expected if selected == "all" else [selected])


def test_notebook_build_tail():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    cell = next(cell for cell in notebook["cells"] if cell.get("id") == "c08")
    tree = ast.parse("".join(cell["source"]))
    branch_index = next(i for i, node in enumerate(tree.body) if isinstance(node, ast.If)
                        and isinstance(node.test, ast.Name) and node.test.id == "CODE_BUNDLE")
    tail = ast.Module(body=tree.body[branch_index + 1:], type_ignores=[])
    assert tail.body, "c08 lost the shared Maven build/JAR statements"
    commands = []
    times = iter([100.0, 107.0])
    namespace = {
        "SRC": Path("/tmp/source"),
        "COMMIT": "0123456789abcdef" * 2 + "01234567",
        "time": SimpleNamespace(time=lambda: next(times)),
        "glob": SimpleNamespace(glob=lambda pattern: [pattern.replace("*", "0.0.1-SNAPSHOT")]),
        "run": lambda command, **kwargs: commands.append((command, kwargs)) or "",
    }
    exec(compile(tail, "kaggle-c08-build", "exec"), namespace)
    assert namespace["JAR"].endswith("cinema-0.0.1-SNAPSHOT.jar")
    assert commands == [("./mvnw -B -q -Dmaven.test.skip=true package", {"cwd": Path("/tmp/source")})]


def test_postgres_affinity():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    cell = next(cell for cell in notebook["cells"] if cell.get("id") == "c06")
    tree = ast.parse("".join(cell["source"]))
    affinity = next(node for node in tree.body if isinstance(node, ast.Expr)
                    and isinstance(node.value, ast.Call)
                    and isinstance(node.value.func, ast.Name) and node.value.func.id == "run"
                    and "pgrep" in ast.unparse(node))
    commands = []

    def capture(command, **kwargs):
        commands.append(command)

    exec(compile(ast.Module(body=[affinity], type_ignores=[]), "kaggle-c06-affinity", "exec"),
         {"APP_CPUS": "1,2,3", "run": capture})
    expected = "su postgres -c 'for pid in $(pgrep -u postgres -x postgres); do taskset -a -cp 1,2,3 \"$pid\" > /dev/null || exit; done'"
    assert commands == [expected], commands

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        bindir = root / "bin"
        bindir.mkdir()
        log = root / "taskset.log"
        (bindir / "su").write_text("#!/bin/sh\n"
                                    "[ \"$1\" = postgres ] && [ \"$2\" = -c ] || exit 90\n"
                                    "exec /bin/sh -c \"$3\"\n")
        (bindir / "pgrep").write_text("#!/bin/sh\n"
                                       "[ \"$1\" = -u ] && [ \"$2\" = postgres ] && [ \"$3\" = -x ] && "
                                       "[ \"$4\" = postgres ] || exit 91\n"
                                       "printf '101\\n102\\n'\n")
        (bindir / "taskset").write_text("#!/bin/sh\n"
                                         "printf '%s\\n' \"$*\" >> \"$AFFINITY_LOG\"\n"
                                         "[ \"${TASKSET_FAIL:-}\" = 1 ] && exit 7\n"
                                         "exit 0\n")
        for path in bindir.iterdir():
            path.chmod(0o755)
        env = {**os.environ, "PATH": f"{bindir}:/bin", "AFFINITY_LOG": str(log)}
        success = subprocess.run(commands[0], shell=True, env=env, capture_output=True, text=True)
        assert success.returncode == 0, success.stderr
        assert log.read_text().splitlines() == ["-a -cp 1,2,3 101", "-a -cp 1,2,3 102"]
        log.write_text("")
        env["TASKSET_FAIL"] = "1"
        failure = subprocess.run(commands[0], shell=True, env=env, capture_output=True, text=True)
        assert failure.returncode != 0
        assert log.read_text().splitlines() == ["-a -cp 1,2,3 101"]


def test_contention_verifier():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    cells = {cell.get("id"): cell for cell in notebook["cells"]}
    psql_tree = ast.parse("".join(cells["c06"]["source"]))
    psql_function = next(node for node in psql_tree.body if isinstance(node, ast.FunctionDef)
                         and node.name == "psql")
    run_tree = ast.parse("".join(cells["c04"]["source"]))
    run_function = next(node for node in run_tree.body if isinstance(node, ast.FunctionDef)
                        and node.name == "run")
    verifier_tree = ast.parse("".join(cells["c12"]["source"]))
    verifier_function = next(node for node in verifier_tree.body if isinstance(node, ast.FunctionDef)
                             and node.name == "verify_contention")

    commands = []
    responses = [
        SimpleNamespace(returncode=0, stdout="PostgreSQL test\n"),
        SimpleNamespace(returncode=0, stdout="10|10\n"),
        SimpleNamespace(returncode=0, stdout="10|9\n"),
        SimpleNamespace(returncode=1, stdout="ERROR: relation booked_seat does not exist; db-test-password gh-test-token\n"),
        SimpleNamespace(returncode=0, stdout="3|3\n"),
        SimpleNamespace(returncode=0, stdout="3|3\n"),
    ]

    def subprocess_run(command, **kwargs):
        commands.append((command, kwargs))
        return responses.pop(0)

    run_namespace = {
        "subprocess": SimpleNamespace(PIPE=-1, STDOUT=-2, run=subprocess_run),
        "GITHUB_TOKEN": "gh-test-token",
        "DB_PASSWORD": "db-test-password",
    }
    exec(compile(ast.Module(body=[run_function], type_ignores=[]), "kaggle-c04-run", "exec"), run_namespace)
    psql_namespace = {"run": run_namespace["run"]}
    exec(compile(ast.Module(body=[psql_function], type_ignores=[]), "kaggle-c06-psql", "exec"), psql_namespace)
    assert psql_namespace["psql"]("SELECT version()").startswith("PostgreSQL")
    assert "-d postgres" in commands[-1][0]  # bootstrap/system-catalog callers retain their original database

    with tempfile.TemporaryDirectory() as directory:
        step = Path(directory)
        (step / "contention.json").write_text(json.dumps({"showtimeId": "01234567-89ab-cdef-0123-456789abcdef"}))

        def counts(**values):
            (step / "summary.json").write_text(json.dumps({"metrics": {
                name: {"values": {"count": count}} for name, count in values.items()}}))

        namespace = {"json": json, "re": re, "psql": psql_namespace["psql"]}
        code = ast.Module(body=[verifier_function], type_ignores=[])
        exec(compile(code, "kaggle-c12-verifier", "exec"), namespace)
        verify = namespace["verify_contention"]
        counts(bookings_created=10, booking_conflicts=103, seat_conflicts=90, pending_customer_conflicts=10,
               busy_conflicts=3)
        assert verify(step, False) is True
        report = (step / "verification.txt").read_text()
        assert report.startswith("PASS ") and "duplicate rows: 0" in report
        assert "409 Seat already taken: 90" in report
        assert "409 Customer already has Pending Booking: 10" in report
        assert "409 busy, try again: 3" in report
        assert "-d cinema" in commands[-1][0]

        assert verify(step, False) is False  # a Seat booked twice
        assert (step / "verification.txt").read_text().startswith("FAIL ")
        assert "-d cinema" in commands[-1][0]

        assert verify(step, False) is False
        report = (step / "verification.txt").read_text()
        assert "ERROR: relation booked_seat does not exist" in report
        assert "db-test-password" not in report and "gh-test-token" not in report

        # Race: winners abandon, so only the unabandoned ones keep rows, and seat conflicts may be absent.
        counts(bookings_created=50, bookings_abandoned=47, booking_conflicts=5, busy_conflicts=3, free_again_conflicts=2)
        assert verify(step, True) is True
        assert verify(step, False) is False  # contention needs seat conflicts


def test_source_bundle_paths():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    cell = next(cell for cell in notebook["cells"] if cell.get("id") == "c08")
    tree = ast.parse("".join(cell["source"]))
    function = next(node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef)
                    and node.name == "safe_bundle_path")
    namespace = {"PurePosixPath": PurePosixPath}
    exec(compile(ast.Module(body=[function], type_ignores=[]), "kaggle-c08-validator", "exec"), namespace)
    safe_path = namespace["safe_bundle_path"]
    assert safe_path("loadtest/seed.sql").as_posix() == "loadtest/seed.sql"
    for name in ("../escape", "/absolute", "..\\escape", "src/../escape", ".env.local", "target/classes/a"):
        try:
            safe_path(name)
        except RuntimeError:
            pass
        else:
            raise AssertionError(f"unsafe bundle path accepted: {name}")


def test_source_bundle_inputs():
    notebook = json.loads((ROOT / "loadtest/kaggle.ipynb").read_text())
    cell = next(cell for cell in notebook["cells"] if cell.get("id") == "c08")
    tree = ast.parse("".join(cell["source"]))
    branch = next(node for node in tree.body if isinstance(node, ast.If)
                  and isinstance(node.test, ast.Name) and node.test.id == "CODE_BUNDLE")
    setup = ast.Module(body=[*tree.body[:3], branch], type_ignores=[])

    def load(bundle, destination, input_root=None):
        zip_reads = []
        commands = []

        def open_zip(*args, **kwargs):
            zip_reads.append(Path(args[0]))
            return zipfile.ZipFile(*args, **kwargs)

        def run(command, **kwargs):
            commands.append(command)
            return "0123456789abcdef" * 2 + "01234567" if command == "git rev-parse HEAD" else ""

        namespace = {
            "CODE_BUNDLE": bundle, "SRC": destination,
            "hashlib": hashlib, "json": json, "os": os, "Path": Path,
            "PurePosixPath": PurePosixPath, "re": re, "shutil": shutil,
            "stat": stat, "zipfile": SimpleNamespace(ZipFile=open_zip),
            "zip_reads": zip_reads, "commands": commands, "run": run,
            "GITHUB_TOKEN": "test-token", "REPO": "owner/repo", "GIT_REF": "main",
            "glob": SimpleNamespace(glob=lambda pattern, **kwargs: glob.glob(
                pattern.replace("/kaggle/input", str(input_root)), **kwargs)),
        }
        exec(compile(setup, "notebook-c08-bundle-loader", "exec"), namespace)
        return namespace

    def make_source(root):
        files = {
            "pom.xml": b"<project/>",
            "mvnw": b"#!/bin/sh\n",
            ".mvn/wrapper/maven-wrapper.properties": b"distributionUrl=test\n",
            "loadtest/seed.sql": b"SELECT 1;\n",
            "loadtest/report.py": b"print('report')\n",
            "src/Main.java": b"class Main {}\n",
            "loadtest-bundle.json": json.dumps({
                "snapshotMode": "working-tree", "sourceCommit": "0123456789abcdef" * 2 + "01234567",
            }).encode(),
        }
        for name, content in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            if name == "mvnw":
                path.chmod(0o755)
        return files

    def make_git_fixture(root, extra_tracked=()):
        files = make_source(root)
        (root / ".gitignore").write_text("ignored.txt\n")
        (root / "ignored.txt").write_text("ignored\n")
        (root / "src/New.java").write_text("class New {}\n")
        (root / "loadtest/executable.sh").write_text("#!/bin/sh\n")
        (root / "loadtest/executable.sh").chmod(0o755)
        (root / "target/classes").mkdir(parents=True)
        (root / "target/classes/App.class").write_bytes(b"class App {}\n")
        (root / "secrets").mkdir()
        (root / "secrets/token").write_text("secret\n")
        (root / ".env").write_text("SECRET=1\n")
        (root / "link").symlink_to(root / "pom.xml")
        tracked = [".gitignore", ".mvn", "loadtest/seed.sql", "loadtest/report.py",
                   "mvnw", "pom.xml", "src/Main.java"]
        for name in extra_tracked:
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("tracked placeholder\n")
            tracked.append(name)
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        subprocess.run(["git", "add", *tracked], cwd=root, check=True)
        subprocess.run(["git", "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                        "commit", "-qm", "fixture"], cwd=root, check=True)
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
        return files, commit

    def append_to_copy(archive, destination, name, content, symlink=False):
        shutil.copyfile(archive, destination)
        with zipfile.ZipFile(destination, "a") as bundle:
            if symlink:
                info = zipfile.ZipInfo(name)
                info.create_system = 3
                info.external_attr = (stat.S_IFLNK | 0o777) << 16
                bundle.writestr(info, content)
            else:
                bundle.writestr(name, content)
        return destination

    def expect_rejected(bundle, destination, message, input_root=None):
        try:
            load(bundle, destination, input_root)
        except RuntimeError as error:
            assert message in str(error), str(error)
        else:
            raise AssertionError(f"unsafe or incomplete source bundle accepted: {bundle}")

    with tempfile.TemporaryDirectory() as directory:
        temp = Path(directory)
        fixture = temp / "fixture"
        fixture.mkdir()
        files, commit = make_git_fixture(fixture)
        archive = fixture / "source.zip"
        built = subprocess.run([sys.executable, str(ROOT / "loadtest/bundle.py"), "-o", str(archive)],
                               cwd=fixture, check=True, text=True, capture_output=True)
        assert "Wrote" in built.stdout

        expected = set(files) | {".gitignore", "src/New.java", "loadtest/executable.sh"}
        with zipfile.ZipFile(archive) as bundle:
            names = bundle.namelist()
            assert set(names) == expected
            assert names.count("loadtest-bundle.json") == 1
            manifest = json.loads(bundle.read("loadtest-bundle.json"))
            assert manifest["sourceCommit"] == commit
            assert manifest["snapshotMode"] == "working-tree"
            assert "source.zip" not in names
            assert not any(name.startswith(("target/", "secrets/")) or name == ".env"
                           or name == "ignored.txt" or name == "link" for name in names)
            assert (bundle.getinfo("mvnw").external_attr >> 16) & 0o111
            assert (bundle.getinfo("loadtest/executable.sh").external_attr >> 16) & 0o111
            assert not (bundle.getinfo("src/New.java").external_attr >> 16) & 0o111

        notes = fixture / "notes.txt"
        notes.write_bytes(b"untracked notes\n")
        notes_bytes = notes.read_bytes()
        for target in (fixture / "pom.xml", notes, archive):
            before = target.read_bytes()
            refused = subprocess.run([sys.executable, str(ROOT / "loadtest/bundle.py"),
                                      "-o", str(target)], cwd=fixture, text=True, capture_output=True)
            assert refused.returncode != 0
            assert "already exists" in refused.stderr
            assert target.read_bytes() == before

        output_link = fixture / "output-link"
        output_link.symlink_to(notes)
        refused = subprocess.run([sys.executable, str(ROOT / "loadtest/bundle.py"),
                                  "-o", str(output_link)], cwd=fixture, text=True, capture_output=True)
        assert refused.returncode != 0 and "symlink" in refused.stderr
        assert output_link.is_symlink() and notes.read_bytes() == notes_bytes

        dangling_link = fixture / "dangling-link"
        dangling_target = fixture / "dangling-target"
        dangling_link.symlink_to(dangling_target)
        refused = subprocess.run([sys.executable, str(ROOT / "loadtest/bundle.py"),
                                  "-o", str(dangling_link)], cwd=fixture, text=True, capture_output=True)
        assert refused.returncode != 0 and "symlink" in refused.stderr
        assert dangling_link.is_symlink() and not dangling_target.exists()

        from_zip = load(archive, temp / "bundle-zip-checkout")
        assert from_zip["COMMIT"] == commit
        assert from_zip["SOURCE_PROVENANCE"] == f"full source snapshot based on {commit}"
        assert from_zip["CODE_BUNDLE_HASH_KIND"] == "exact ZIP file bytes"
        assert from_zip["CODE_BUNDLE_SHA256"] == hashlib.sha256(archive.read_bytes()).hexdigest()
        assert from_zip["files"] == set(names)
        assert (from_zip["SRC"] / "mvnw").stat().st_mode & 0o111
        assert (from_zip["SRC"] / "src/New.java").read_text() == "class New {}\n"

        expanded = temp / "bundle-expanded"
        with zipfile.ZipFile(archive) as bundle:
            for info in bundle.infolist():
                destination = expanded / info.filename
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(bundle.read(info))
                destination.chmod(0o755 if (info.external_attr >> 16) & 0o111 else 0o644)
        from_directory = load(expanded, temp / "bundle-directory-checkout")
        assert from_directory["COMMIT"] == commit
        assert from_directory["CODE_BUNDLE_HASH_KIND"].startswith("expanded tree:")
        assert from_directory["files"] == set(names)
        assert (from_directory["SRC"] / "loadtest/executable.sh").stat().st_mode & 0o111
        repeated = load(expanded, temp / "bundle-directory-repeat")
        assert from_directory["CODE_BUNDLE_SHA256"] == repeated["CODE_BUNDLE_SHA256"]

        git_path = load(None, temp / "git-checkout")
        assert git_path["SOURCE_PROVENANCE"] == f"Git ref main at {git_path['COMMIT']}"
        assert git_path["CODE_BUNDLE_SHA256"] == "none"
        assert git_path["CODE_BUNDLE_HASH_KIND"] == "not used (Git clone)"
        assert git_path["commands"] == [
            "git clone --quiet https://x-access-token:test-token@github.com/owner/repo.git "
            f"{git_path['SRC']}",
            "git checkout --quiet main",
            "git remote set-url origin https://github.com/owner/repo.git",
            "git rev-parse HEAD",
        ]

        wrong_root = temp / "dataset"
        wrong_root.mkdir()
        expect_rejected(wrong_root, temp / "wrong-root-checkout", "expanded source root")

        symlink_tree = temp / "symlink-source"
        make_source(symlink_tree)
        (symlink_tree / "link").symlink_to(expanded / "pom.xml")
        expect_rejected(symlink_tree, temp / "symlink-checkout", "symlink")

        for name, message in (("../escape", "Unsafe path"), (".env.local", "omit build output and secrets"),
                              ("secrets/token", "omit build output and secrets")):
            bad_zip = append_to_copy(archive, temp / "unsafe.zip", name, b"secret")
            expect_rejected(bad_zip, temp / "unsafe-checkout", message)

        symlink_zip = append_to_copy(archive, temp / "symlink.zip", "link", b"../outside", symlink=True)
        expect_rejected(symlink_zip, temp / "symlink-zip-checkout", "symlink or special file")

        # A tracked path is still exported when its parent is replaced by a symlink;
        # resolve() normally hides that from the final-component nofollow check.
        leak_fixture = temp / "symlink-parent-fixture"
        leak_fixture.mkdir()
        make_git_fixture(leak_fixture, ("src/Legacy.java", "nested/inner/app.yml",
                                        "nested/deep/settings.yml"))
        for name in ("Main.java", "Legacy.java", "app.yml"):
            (leak_fixture / "secrets" / name).write_text("INTERNAL_SECRET_CANARY\n")
        external = temp / "external-secret-dir"
        external.mkdir()
        (external / "settings.yml").write_text("EXTERNAL_SECRET_CANARY\n")
        shutil.rmtree(leak_fixture / "src")
        (leak_fixture / "src").symlink_to(leak_fixture / "secrets")
        shutil.rmtree(leak_fixture / "nested/inner")
        (leak_fixture / "nested/inner").symlink_to(leak_fixture / "secrets")
        shutil.rmtree(leak_fixture / "nested/deep")
        (leak_fixture / "nested/deep").symlink_to(external)
        replaced_archive = leak_fixture / "replaced.zip"
        subprocess.run([sys.executable, str(ROOT / "loadtest/bundle.py"), "-o", str(replaced_archive)],
                       cwd=leak_fixture, check=True, text=True, capture_output=True)
        with zipfile.ZipFile(replaced_archive) as bundle:
            replaced_names = bundle.namelist()
            for alias in ("src/Main.java", "src/Legacy.java", "nested/inner/app.yml",
                          "nested/deep/settings.yml"):
                assert alias not in replaced_names
            assert all(b"SECRET_CANARY" not in bundle.read(name) for name in replaced_names)
            assert bundle.read("pom.xml") == b"<project/>"
            assert (bundle.getinfo("mvnw").external_attr >> 16) & 0o111
            assert (bundle.getinfo("loadtest/executable.sh").external_attr >> 16) & 0o111

        auto_commit = "0123456789abcdef" * 2 + "01234567"
        flat_mount = temp / "kaggle-input-flat"
        flat_root = flat_mount / "dataset"
        make_source(flat_root)
        (flat_mount / "alias").symlink_to(flat_root, target_is_directory=True)
        auto_flat = load("auto", temp / "auto-flat-checkout", flat_mount)
        assert auto_flat["CODE_BUNDLE"] == "auto" and auto_flat["COMMIT"] == auto_commit
        assert (auto_flat["SRC"] / "pom.xml").read_text() == "<project/>"

        nested_mount = temp / "kaggle-input-nested"
        make_source(nested_mount / "dataset" / "source")
        auto_nested = load("auto", temp / "auto-nested-checkout", nested_mount)
        assert auto_nested["COMMIT"] == auto_commit
        assert (auto_nested["SRC"] / "loadtest/seed.sql").read_text() == "SELECT 1;\n"

        empty_mount = temp / "kaggle-input-empty"
        empty_mount.mkdir()
        empty_destination = temp / "auto-empty-checkout"
        expect_rejected("auto", empty_destination, "found 0", empty_mount)
        assert not empty_destination.exists()

        many_mount = temp / "kaggle-input-many"
        make_source(many_mount / "one")
        make_source(many_mount / "two" / "source")
        many_destination = temp / "auto-many-checkout"
        expect_rejected("auto", many_destination, "found 2", many_mount)
        assert not many_destination.exists()

        bad_mount = temp / "kaggle-input-bad"
        make_source(bad_mount / "bad")
        (bad_mount / "bad" / "loadtest-bundle.json").write_text(json.dumps({
            "snapshotMode": "other", "sourceCommit": "0" * 40}))
        expect_rejected("auto", temp / "auto-bad-checkout", "snapshotMode=working-tree", bad_mount)

        unsafe_mount = temp / "kaggle-input-unsafe"
        make_source(unsafe_mount / "unsafe")
        (unsafe_mount / "unsafe" / "secrets").mkdir()
        (unsafe_mount / "unsafe" / "secrets/token").write_text("secret\n")
        expect_rejected("auto", temp / "auto-unsafe-checkout", "omit build output and secrets",
                        unsafe_mount)


if __name__ == "__main__":
    test_report_verdict()
    test_charts()
    test_seat_slots()
    test_conflicts()
    test_browse_setup()
    test_showtimes_scan()
    test_notebook_cells()
    test_notebook_scenarios()
    test_notebook_build_tail()
    test_postgres_affinity()
    test_contention_verifier()
    test_source_bundle_paths()
    test_source_bundle_inputs()
    print("PASS  report verdict; charts; seat slots; conflicts; browse; showtimes; notebook; PostgreSQL affinity; SQL verifier; "
          "Git fallback; bundle.py ZIP/directory round trips, deterministic hash, and path/symlink rejection")
