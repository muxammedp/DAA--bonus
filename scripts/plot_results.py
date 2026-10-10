import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "results" / "plots"
SIZES = (100, 1000, 10000)
OPERATIONS = ("insert", "search", "delete")
TITLES = ("Insertion", "Searching", "Deletion")
STRUCTURES = {
    "BST": ("BST", "#1766A0", "o"),
    "HashTable": ("Hash Table", "#C65D17", "s"),
}


def load_results():
    samples = defaultdict(list)
    trials = defaultdict(set)
    with (ROOT / "results" / "raw.csv").open(newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            key = (row["scenario"], int(row["n"]), row["structure"], row["operation"])
            value = int(row["total_ns"]) / 1_000_000
            if value <= 0 or row["trial"] in trials[key]:
                raise ValueError(f"Invalid or duplicate measurement: {key}")
            samples[key].append(value)
            trials[key].add(row["trial"])

    expected = {
        (scenario, n, structure, operation)
        for scenario in ("random", "sorted", "collisions")
        for n in SIZES
        for structure in STRUCTURES
        for operation in OPERATIONS
    }
    if set(samples) != expected or any(len(values) != 9 for values in samples.values()):
        raise ValueError("Expected nine trials for each of the 54 measurement groups")

    checked = set()
    with (ROOT / "results" / "summary.csv").open(newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            key = (row["scenario"], int(row["n"]), row["structure"], row["operation"])
            if key in checked or key not in samples:
                raise ValueError(f"Unexpected summary row: {key}")
            if not math.isclose(statistics.median(samples[key]), float(row["median_ms"]), abs_tol=1e-9):
                raise ValueError(f"Raw data and summary disagree: {key}")
            checked.add(key)
    if checked != expected:
        raise ValueError("Summary data is incomplete")
    return samples


def draw(ax, samples, scenario, operation, per_operation=False, log_y=False, log_x=False):
    for structure, (label, color, marker) in STRUCTURES.items():
        medians = []
        lower = []
        upper = []
        for n in SIZES:
            factor = 1000 / n if per_operation else 1
            values = [value * factor for value in samples[(scenario, n, structure, operation)]]
            median = statistics.median(values)
            q1, _, q3 = statistics.quantiles(values, n=4, method="inclusive")
            medians.append(median)
            lower.append(median - q1)
            upper.append(q3 - median)
        ax.errorbar(
            SIZES, medians, yerr=[lower, upper], label=label,
            color=color, marker=marker, linewidth=2, markersize=6,
            capsize=4, elinewidth=1.2,
        )
    if log_x:
        ax.set_xscale("log")
        ax.set_xlim(75, 14000)
    else:
        ax.set_xlim(0, 10500)
    if log_y:
        ax.set_yscale("log")
    else:
        ax.set_ylim(bottom=0)
        ax.ticklabel_format(axis="y", style="plain", useOffset=False)
    ax.set_xticks(SIZES, [f"{n:,}" for n in SIZES])
    if not log_x:
        plt.setp(ax.get_xticklabels(), rotation=35, ha="right")
    ax.set_xlabel("Number of elements (n)")
    ax.set_ylabel("Time per operation (microseconds)" if per_operation else "Total batch time (ms)")
    ax.grid(axis="y", which="major", color="#DBE2E8", linewidth=0.8)
    ax.set_axisbelow(True)
    ax.spines[["top", "right"]].set_visible(False)
    ax.spines[["left", "bottom"]].set_color("#AAB7C2")
    if log_y:
        ax.yaxis.set_major_formatter(FuncFormatter(lambda value, position: f"{value:g}"))


def save(fig, axes, name, title, note):
    fig.suptitle(title, fontsize=19, fontweight="bold", y=0.98)
    handles, labels = axes.flat[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", bbox_to_anchor=(0.5, 0.93), ncol=2, frameon=False)
    fig.text(0.5, 0.025, note, ha="center", va="bottom", fontsize=10, color="#4C5A67")
    fig.tight_layout(rect=(0.015, 0.095, 0.99, 0.87), w_pad=2.5, h_pad=3)
    for extension in ("png", "svg"):
        path = OUTPUT / f"{name}.{extension}"
        fig.savefig(path, dpi=200, facecolor="white")
        print(f"Saved {path.relative_to(ROOT)}")
    plt.close(fig)


def main():
    samples = load_results()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.titlesize": 13})

    fig, axes = plt.subplots(1, 3, figsize=(15, 5.3))
    for ax, operation, title in zip(axes, OPERATIONS, TITLES):
        draw(ax, samples, "random", operation)
        ax.set_title(title, pad=12)
    save(fig, axes, "random_time_vs_n", "Random insertion order: number of elements vs time",
         "Linear axes. Each point is the median total time for n operations.\n"
         "Whiskers show the middle 50% of nine trials (IQR); lines connect measured sizes only.")

    fig, axes = plt.subplots(2, 3, figsize=(15, 8.8))
    for row, (scenario, label) in enumerate((("sorted", "Sorted input"), ("collisions", "Hash collisions"))):
        for column, (operation, title) in enumerate(zip(OPERATIONS, TITLES)):
            draw(axes[row, column], samples, scenario, operation, log_y=True, log_x=True)
            axes[row, column].set_title(f"{label}: {title.lower()}", pad=12)
    save(fig, axes, "stress_time_vs_n", "Stress tests: number of elements vs time",
         "Both axes use logarithmic scales; equal distances mean equal ratios. Panel y-ranges differ.\n"
         "Median batch time with IQR whiskers from nine trials. Whiskers are not confidence intervals.")

    fig, axes = plt.subplots(1, 3, figsize=(15, 5.3))
    for ax, operation, title in zip(axes, OPERATIONS, TITLES):
        draw(ax, samples, "random", operation, per_operation=True, log_x=True)
        ax.set_title(title, pad=12)
    save(fig, axes, "random_time_per_operation", "Random insertion order: time per operation",
         "Logarithmic x-axis; linear y-axis. Each batch time is divided by n and converted to microseconds.\n"
         "Median with IQR whiskers. These are averages within batches, not separately timed operations.")
    print("Verified all 54 measurement groups against summary.csv (486 raw measurements).")


if __name__ == "__main__":
    main()
