"""Judges every step of a capacity run and prints where each scenario stopped holding (ADR 0007).

Usage:
  python3 loadtest/report.py results/                       # the notebook's results directory
  python3 loadtest/report.py loadtest/results/summary.json  # one local k6 run
"""
import csv
import json
import re
import sys
from pathlib import Path

# A step holds only if all of these hold over its whole duration.
MAX_P95_MS = 200
MAX_ERROR_RATE = 0.01
MIN_CHECK_RATE = 0.99
MIN_ACHIEVED = 0.95
# k6 has one core (ADR 0003); near it, a break may be k6 falling behind rather than the service.
K6_SATURATED = 90
SCENARIOS = ["account", "browse", "book", "contention", "race"]


def load_metric(name):
    """The sub-metric of requests tagged phase:load (see k6/lib/summary.js); setup's requests are excluded."""
    return f"{name}{{phase:load}}"


def metric(data, name, stat):
    return data["metrics"].get(name, {}).get("values", {}).get(stat)


def seconds(duration):
    """k6 duration such as 60s, 2m or 1m30s."""
    parts = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?", duration)
    if not parts or not any(parts.groups()):
        raise ValueError(f"unsupported duration: {duration}")
    h, m, s = (int(part or 0) for part in parts.groups())
    return 3600 * h + 60 * m + s


def verdict(data):
    """One step's numbers and whether it held, broke (with the reasons) or ran out of seeded Seats."""
    rate, duration = data["load"]["rate"], seconds(data["load"]["duration"])
    exhausted = metric(data, "seats_exhausted", "count") or 0
    iterations = (metric(data, "iterations", "count") or 0) - exhausted
    step = {
        "rate": rate,
        "achieved": iterations / duration,
        "rps": (metric(data, load_metric("http_reqs"), "count") or 0) / duration,
        "p50": metric(data, load_metric("http_req_duration"), "med"),
        "p95": metric(data, load_metric("http_req_duration"), "p(95)"),
        "p99": metric(data, load_metric("http_req_duration"), "p(99)"),
        "errors": metric(data, load_metric("http_req_failed"), "rate") or 0,
        # A step without checks (every request failed before one ran) has nothing to vouch for it.
        "checks": metric(data, "checks", "rate") or 0,
        # Only present when k6 ran out of VUs for the target rate.
        "dropped": metric(data, "dropped_iterations", "count") or 0,
        "exhausted": exhausted,
        # Deadlock retries the service gave up on (ADR 0004): expected, but shown apart.
        "busy": metric(data, "busy_conflicts", "count") or 0,
    }
    reasons = []
    if step["p95"] is None or step["p95"] > MAX_P95_MS:
        reasons.append(f"p95 > {MAX_P95_MS} ms")
    if step["errors"] > MAX_ERROR_RATE:
        reasons.append(f"errors > {MAX_ERROR_RATE:.0%}")
    if step["checks"] < MIN_CHECK_RATE:
        reasons.append(f"checks < {MIN_CHECK_RATE:.0%}")
    if step["dropped"]:
        reasons.append("dropped iterations")
    if step["achieved"] < MIN_ACHIEVED * rate:
        reasons.append(f"achieved < {MIN_ACHIEVED:.0%} of target")
    step["reasons"] = reasons
    step["status"] = "exhausted" if exhausted else "broke" if reasons else "held"
    return step


def fmt(value, spec=",.2f"):
    return "n/a" if value is None else f"{value:{spec}}"


def step_row(step):
    result = step["status"] if not step["reasons"] or step["exhausted"] else "broke: " + ", ".join(step["reasons"])
    return [fmt(step["rate"], ",.0f"), fmt(step["achieved"], ",.1f"), fmt(step["rps"], ",.1f"),
            fmt(step["p50"]), fmt(step["p95"]), fmt(step["p99"]), fmt(step["errors"], ".2%"),
            fmt(step["checks"], ".2%"), fmt(step["dropped"], ",.0f"), fmt(step["busy"], ",.0f"), result]


def print_table(rows):
    header = ["target it/s", "achieved it/s", "RPS", "p50 ms", "p95 ms", "p99 ms", "errors", "checks",
              "dropped", "busy 409", "result"]
    widths = [max(len(row[i]) for row in [header, *rows]) for i in range(len(header))]
    for row in [header, *rows]:
        print("  ".join(cell.rjust(width) for cell, width in zip(row, widths)).rstrip())


def mean(values):
    return sum(values) / len(values) if values else None


def telemetry(directory):
    """Averages and peaks of the 1-second samples the notebook took during one step."""
    path = directory / "telemetry.csv"
    if not path.exists():
        return None
    with path.open() as f:
        rows = list(csv.DictReader(f))

    def column(name):
        return [float(row[name]) for row in rows if row.get(name) not in (None, "")]

    def delta(name):
        values = column(name)
        return values[-1] - values[0] if len(values) > 1 else None

    acquires, acquire_ms = delta("hikari_acquire_count"), delta("hikari_acquire_total_ms")
    peaks = {name: max(column(name), default=None) for name in ("hikari_pending", "tomcat_busy")}
    return {
        **{name: mean(column(name)) for name in ("cpu_java", "cpu_postgres", "cpu_k6", "iowait", "hikari_active",
                                                 "pg_active", "pg_lock", "pg_lwlock", "pg_io", "pg_other")},
        **peaks,
        "acquire_ms": acquire_ms / acquires if acquires else None,
        "gc_ms": delta("gc_pause_total_ms"),
    }


def print_telemetry(directory):
    t = telemetry(directory)
    if t is None:
        print("  no telemetry.csv")
        return
    print(f"  CPU (% of one core): java {fmt(t['cpu_java'], '.0f')}, postgres {fmt(t['cpu_postgres'], '.0f')}, "
          f"k6 {fmt(t['cpu_k6'], '.0f')}; iowait {fmt(t['iowait'], '.1f')}%")
    if t["cpu_k6"] is not None and t["cpu_k6"] >= K6_SATURATED:
        print(f"  WARNING: k6 used {t['cpu_k6']:.0f}% of its core: this step may show k6's limit, not the service's")
    print(f"  Hikari: active {fmt(t['hikari_active'], '.1f')}, peak pending {fmt(t['hikari_pending'], '.0f')}, "
          f"mean acquire {fmt(t['acquire_ms'], '.2f')} ms; Tomcat peak busy threads {fmt(t['tomcat_busy'], '.0f')}; "
          f"GC pause {fmt(t['gc_ms'], ',.0f')} ms")
    print(f"  PostgreSQL active backends: on CPU {fmt(t['pg_active'], '.1f')}, Lock {fmt(t['pg_lock'], '.1f')}, "
          f"LWLock {fmt(t['pg_lwlock'], '.1f')}, IO {fmt(t['pg_io'], '.1f')}, other {fmt(t['pg_other'], '.1f')}")
    database = directory / "database.json"
    if database.exists():
        counts = json.loads(database.read_text())
        print(f"  deadlocks {counts['deadlocks']}, rolled-back transactions {counts['rollbacks']:,}")
    statements = directory / "statements.csv"
    if statements.exists():
        with statements.open() as f:
            top = list(csv.DictReader(f))[:3]
        for row in top:
            query = " ".join(row["query"].split())[:100]
            print(f"  SQL {float(row['total_ms']):,.0f} ms total, {int(row['calls']):,} calls: {query}")


def scenario_steps(directory):
    steps = []
    for summary in directory.glob("*/summary.json"):
        if summary.parent.name.isdigit():  # skips warmup/
            steps.append((verdict(json.loads(summary.read_text())), summary.parent))
    return sorted(steps, key=lambda item: item[0]["rate"])


def report_scenario(name, directory):
    steps = scenario_steps(directory)
    if not steps:
        return
    print(f"\n=== {name} ===")
    print_table([step_row(step) for step, _ in steps])
    held = [step for step, _ in steps if step["status"] == "held"]
    if held:
        best = max(held, key=lambda step: step["rate"])
        print(f"Max sustainable throughput: {best['rate']:,} it/s ({best['rps']:,.1f} RPS)")
    else:
        print("Max sustainable throughput: none of the steps held")
    first_stop = next(((step, path) for step, path in steps if step["status"] != "held"), None)
    if first_stop:
        step, path = first_stop
        print(f"First step that {'ran out of Seats' if step['status'] == 'exhausted' else 'broke'}: {step['rate']:,} it/s")
        print_telemetry(path)
        verification = path / "verification.txt"
        if verification.exists():
            print("  " + verification.read_text().splitlines()[0])


def main(path):
    path = Path(path)
    if path.is_file():
        step = verdict(json.loads(path.read_text()))
        print_table([step_row(step)])
        return
    for name in SCENARIOS:
        report_scenario(name, path / name)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "loadtest/results/summary.json")
