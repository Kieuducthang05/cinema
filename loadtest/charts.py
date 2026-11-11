"""Draws where each scenario stopped holding, for one or more capacity runs (ADR 0007).

Usage:
  python3 loadtest/charts.py --out docs/loadtest baseline-1=run1/results baseline-2=run2/results
  python3 loadtest/charts.py results/   # one run, labelled by its directory name

Writes <scenario>.png for every scenario with steps and summary.png. Needs matplotlib; report.py does not.
"""
import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D

from report import K6_SATURATED, MAX_P95_MS, SCENARIOS, scenario_steps, telemetry

# Categorical slots in fixed order (dataviz reference palette, light surface; validated adjacent-pair).
SERIES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4"]
SURFACE, TEXT, MUTED, GRID, CRITICAL = "#fcfcfb", "#0b0b0b", "#898781", "#e1e0d9", "#d03b3b"
# Line style tells the runs apart; color tells the metrics apart.
RUN_STYLES = ["-", "--", ":", "-."]
CONFLICTS = [("seat_conflicts", "Seat taken"), ("pending_customer_conflicts", "Customer has Pending Booking"),
             ("busy_conflicts", "busy, try again"), ("free_again_conflicts", "winner abandoned")]

plt.rcParams.update({
    "figure.facecolor": SURFACE, "axes.facecolor": SURFACE, "savefig.facecolor": SURFACE,
    "axes.edgecolor": GRID, "axes.labelcolor": TEXT, "text.color": TEXT, "xtick.color": MUTED, "ytick.color": MUTED,
    "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.6, "axes.spines.top": False,
    "axes.spines.right": False, "lines.linewidth": 2, "lines.markersize": 7, "font.size": 9,
    "legend.frameon": False, "axes.titlesize": 10, "axes.titleweight": "bold",
})


def load_run(directory):
    """Every scenario's steps: rate-sorted dicts of the verdict, telemetry and conflict counts."""
    run = {}
    for name in SCENARIOS:
        steps = []
        for step, path in scenario_steps(Path(directory) / name):
            metrics = json.loads((path / "summary.json").read_text())["metrics"]
            count = lambda metric: metrics.get(metric, {}).get("values", {}).get("count", 0)
            step["telemetry"] = telemetry(path) or {}
            step["attempts"] = count("iterations")
            step["conflicts"] = {metric: count(metric) for metric, _ in CONFLICTS}
            steps.append(step)
        if steps:
            run[name] = steps
    return run


def max_held(steps):
    return max((step["rate"] for step in steps if step["status"] == "held"), default=None)


def rate_axis(ax, rates):
    ax.set_xscale("log", base=2)
    ax.set_xticks(rates, [f"{rate:,}" for rate in rates])
    ax.minorticks_off()
    ax.set_xlabel("target iterations/s")


def plot_series(ax, steps, value, color, style, label=None):
    """One metric of one run: a line through every step, its markers showing held, broke or exhausted."""
    points = [(step["rate"], value(step), step["status"]) for step in steps]
    points = [(rate, y, status) for rate, y, status in points if y is not None]
    if not points:
        return
    ax.plot([p[0] for p in points], [p[1] for p in points], style, color=color, label=label)
    for rate, y, status in points:
        if status == "held":
            ax.plot(rate, y, "o", color=color, markeredgecolor=SURFACE, markeredgewidth=1.5)
        elif status == "broke":
            ax.plot(rate, y, "o", color=SURFACE, markeredgecolor=CRITICAL, markeredgewidth=2)
        else:
            ax.plot(rate, y, "o", color=MUTED, markeredgecolor=SURFACE, markeredgewidth=1.5)


def metric(name, telemetry_key=False):
    if telemetry_key:
        return lambda step: step["telemetry"].get(name)
    return lambda step: step.get(name)


def scenario_figure(name, runs, out):
    """Up to six panels sharing the rate axis; a panel per unit, so no chart needs a second y-axis."""
    contended = name in ("contention", "race")
    fig, axes = plt.subplots(3, 2, figsize=(11, 11), constrained_layout=True)
    throughput, latency, cpu, pool, waits, conflicts = axes.flat
    rates = sorted({step["rate"] for steps in runs.values() for step in steps})
    panels = [
        (throughput, "Throughput", "achieved iterations/s",
         [("achieved", metric("achieved"))]),
        (latency, "Latency per request", "ms",
         [("p50", metric("p50")), ("p95", metric("p95")), ("p99", metric("p99"))]),
        (cpu, "CPU (% of one core)", "%",
         [("java", metric("cpu_java", True)), ("postgres", metric("cpu_postgres", True)),
          ("k6", metric("cpu_k6", True))]),
        (pool, "Hikari connections", "connections",
         [("active (mean)", metric("hikari_active", True)), ("pending (peak)", metric("hikari_pending", True))]),
        (waits, "PostgreSQL active backends by wait (mean)", "backends",
         [("on CPU", metric("pg_active", True)), ("Lock", metric("pg_lock", True)),
          ("LWLock", metric("pg_lwlock", True)), ("IO", metric("pg_io", True)), ("other", metric("pg_other", True))]),
    ]
    if contended:
        share = lambda key: lambda step: 100 * step["conflicts"][key] / step["attempts"] if step["attempts"] else None
        panels.append((conflicts, "409s by reason", "% of attempts", [(label, share(key)) for key, label in CONFLICTS]))
    else:
        conflicts.remove()

    for ax, title, ylabel, series in panels:
        for (label, steps), style in zip(runs.items(), RUN_STYLES):
            for (series_label, value), color in zip(series, SERIES):
                plot_series(ax, steps, value, color, style, series_label if style == RUN_STYLES[0] else None)
        rate_axis(ax, rates)
        ax.set_title(title, loc="left")
        ax.set_ylabel(ylabel)
        if len(series) > 1:
            ax.legend(loc="best", fontsize=8)

    # Throughput reads against the target: on the diagonal, the system kept up.
    throughput.set_yscale("log", base=2, nonpositive="mask")
    throughput.set_yticks(rates, [f"{rate:,}" for rate in rates])
    throughput.plot(rates, rates, ":", color=MUTED, linewidth=1)
    throughput.annotate("target", (rates[-1], rates[-1]), textcoords="offset points", xytext=(-30, 4), color=MUTED)
    latency.axhline(MAX_P95_MS, color=MUTED, linestyle=":", linewidth=1)
    latency.annotate(f"p95 limit {MAX_P95_MS} ms", (rates[0], MAX_P95_MS), textcoords="offset points",
                     xytext=(0, 4), color=MUTED)
    cpu.axhline(K6_SATURATED, color=MUTED, linestyle=":", linewidth=1)
    cpu.annotate(f"k6 saturated {K6_SATURATED}%", (rates[0], K6_SATURATED), textcoords="offset points",
                 xytext=(0, 4), color=MUTED)
    bests = {}
    for (label, steps), style in zip(runs.items(), RUN_STYLES):
        best = max_held(steps)
        if best:
            throughput.axvline(best, color=MUTED, linestyle=style, linewidth=1)
            bests.setdefault(best, []).append(label)
    for i, (best, labels) in enumerate(sorted(bests.items())):
        text = f"max held {best:,}" + ("" if len(bests) == 1 else f" ({', '.join(labels)})")
        throughput.annotate(text, (best, rates[0]), textcoords="offset points", xytext=(4, 4 + 12 * i), color=MUTED)
    # One note at the first rate where k6 itself ran out of CPU in any run: higher steps may show k6's limit.
    k6_limited = [(step["rate"], step["achieved"]) for steps in runs.values() for step in steps
                  if (step["telemetry"].get("cpu_k6") or 0) >= K6_SATURATED and step["achieved"] > 0]
    if k6_limited:
        throughput.annotate("k6 limit from here", min(k6_limited), textcoords="offset points", xytext=(0, -18),
                            ha="right", color=CRITICAL, fontsize=8)

    fig.suptitle(f"{name}: where it stops holding", x=0.01, ha="left", fontsize=12, fontweight="bold")
    fig.legend(handles=key_handles(runs), loc="upper right", ncol=len(runs) + 3, fontsize=8)
    fig.savefig(out / f"{name}.png", dpi=110)
    plt.close(fig)


def key_handles(runs):
    """What the line styles and markers mean, shared by every panel."""
    handles = [Line2D([], [], color=TEXT, linestyle=style, label=label) for label, style in zip(runs, RUN_STYLES)]
    return handles + [
        Line2D([], [], marker="o", linestyle="", color=TEXT, label="held"),
        Line2D([], [], marker="o", linestyle="", color=SURFACE, markeredgecolor=CRITICAL, markeredgewidth=2,
               label="broke"),
        Line2D([], [], marker="o", linestyle="", color=MUTED, label="out of Seats"),
    ]


def summary_figure(runs_by_label, out):
    """Max sustainable throughput of every scenario, a bar per run."""
    names = [name for name in SCENARIOS if any(name in run for run in runs_by_label.values())]
    fig, ax = plt.subplots(figsize=(9, 4.2), constrained_layout=True)
    width = 0.8 / len(runs_by_label)
    for i, ((label, run), color) in enumerate(zip(runs_by_label.items(), SERIES)):
        for j, name in enumerate(names):
            best = max_held(run.get(name, []))
            x = j - 0.4 + width * (i + 0.5)
            ax.bar(x, best or 0, width - 0.04, color=color, label=label if j == 0 else None)
            ax.annotate(f"{best:,}" if best else "none", (x, best or 0), textcoords="offset points",
                        xytext=(0, 3), ha="center", fontsize=8, color=TEXT)
    ax.set_xticks(range(len(names)), names)
    ax.grid(axis="x", visible=False)
    ax.set_axisbelow(True)
    ax.margins(y=0.15)
    ax.set_ylabel("iterations/s")
    ax.set_title("Max sustainable throughput: the highest step that held", loc="left")
    if len(runs_by_label) > 1:
        fig.legend(loc="upper right", ncol=len(runs_by_label), fontsize=8)
    fig.savefig(out / "summary.png", dpi=110)
    plt.close(fig)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("runs", nargs="+", help="[label=]results directory")
    parser.add_argument("--out", default="loadtest/results/charts")
    args = parser.parse_args(argv)
    if len(args.runs) > len(RUN_STYLES):
        parser.error(f"at most {len(RUN_STYLES)} runs fit one chart")
    runs = {}
    for spec in args.runs:
        label, _, directory = spec.rpartition("=")
        runs[label or Path(directory).resolve().name] = load_run(directory)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for name in SCENARIOS:
        with_steps = {label: run[name] for label, run in runs.items() if name in run}
        if with_steps:
            scenario_figure(name, with_steps, out)
    summary_figure(runs, out)
    print(f"charts written to {out}")


if __name__ == "__main__":
    main()
