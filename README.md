# K-Means Clustering — Sequential, Concurrent & Parallel Implementations in Java

3 implementations of K-Means clustering algorithm demonstrating different concurrency approaches for the WIF3011 course assignment.


## Three Implementations

All three follow the same **Lloyd's Algorithm** loop:

- Pick K initial centroids (randomly from the dataset)
- Repeat until convergence or maxIterations:
    - Assign each point to its nearest centroid
    - Recompute each centroid as the mean of its assigned points
    - Stop if no centroid moves > ε (1e-6)


### Sequential KMeans

| Aspect | Detail |
|---|---|
| Threading | main thread only |
| Assignment | Simple `for` loop over all points |
| Update | Simple `for` loop over K clusters |
| Synchronization | None |
| Convergence | Yes (centroid shift < 1e-6) |
| Best for | Baseline comparison, small datasets, debugging |

**Design rationale:** The simplest implementation. No concurrency overhead. Serves as the baseline for measuring speedup of the other versions.

---

### Concurrent KMeans

| Aspect | Detail |
|---|---|
| Threading | `new Thread()` + `.start()` + `.join()` *(manually managed)* |
| Assignment | Points partitioned into `threadCount` chunks; each thread processes its own range |
| Update | **Partial-sum reduction**: each thread accumulates `partialSums[threadIdx][clusterIdx][dim]` into **thread-local arrays** (no locks in the hot loop). After `joinAll()`, the main thread merges partial results and moves centroids. |
| Synchronization | Assignment: `synchronized(cluster)` when adding points. Update: lock-free via thread-local accumulation. `assignments[]` array for cross-phase communication (write disjoint ranges → no race). |
| Convergence | Yes (centroid shift < 1e-6) |
| Best for | Studying manual thread management overhead, understanding happens-before relationships |

**Design rationale:**
- Uses `assignments[i]` to record which cluster each point belongs to during assignment. This array is read by the update phase — correctness relies on the JMM happens-before chain: assignment `join()` → update `start()`, so update workers see all assignments.
- `joinAll()` retries on `InterruptedException` to guarantee every thread finishes before merging partial sums (prevents silent data corruption).
- Centroid IDs equal cluster list indices (invariant set at initialization), so `assignments[i]` can be used directly to index cluster arrays.

---

### Parallel KMeans (ExecutorService)

| Aspect | Detail |
|---|---|
| Threading | `Executors.newFixedThreadPool(threadCount)` |
| Assignment | Points partitioned into `subList` chunks; each submitted as a task -> `Future.get()` awaits completion |
| Update | Each cluster update submitted as a separate task -> all `Future.get()` awaited before next iteration |
| Synchronization | `synchronized(cluster)` during assignment. Update tasks are independent (each modifies a different cluster). |
| Convergence | Yes (snapshot centroids before update -> compare old vs new positions after update) |
| Best for | Production use, comparing framework overhead vs manual threads |

**Design rationale:**
- Uses Java's standard `ExecutorService` for thread pool management (thread reuse, no manual lifecycle code).
- **Critical fix applied**: the original code submitted update tasks without awaiting them, causing a race condition with `clearPoints()` in the next iteration. Now all update futures are collected and awaited via `Future.get()`.
- Partitions use `List.subList()` (zero-copy views) rather than copying data.

---

### Key Differences at a Glance

| | Sequential | Concurrent | Parallel |
|---|---|---|---|
| Thread creation | N/A | `new Thread` per iteration | Thread pool (reused) |
| Update strategy | Direct loop | Thread-local partial sums + merge | Per-cluster tasks |
| Lock contention | None | Only `synchronized(cluster)` in assignment | Only `synchronized(cluster)` in assignment |
| Code complexity | Low | High | Medium |


## How to Run

### Prerequisites

- **JDK 17+** (or the JDK bundled with your IDE)
- **Gradle** (the project uses the Gradle Wrapper — `gradlew.bat` on Windows, `./gradlew` on macOS/Linux — no separate Gradle install needed)

### Run the Benchmark

```bash
# On Windows:
.\gradlew.bat run

# On macOS / Linux:
./gradlew run
```

This runs all three implementations across multiple dataset sizes, dimensions, K values, and thread counts (see configurable parameters below). Results are written to:

```
benchmark_results_YYYYMMDD_HHmmss.csv
```

### Generate Charts

```bash
# On Windows:
.\gradlew.bat runVisualizer

# On macOS / Linux:
./gradlew runVisualizer
```

Reads the latest CSV and produces **7 PNG charts** in `charts/`:
- 01_time_vs_size.png
    - Bar chart: time vs dataset size (10K / 100K / 1M)
- 02_speedup_vs_threads.png
    - Line chart: speedup vs thread count (1 / 2 / 4 / 8)
- 03_time_vs_dimension.png
    - Bar chart: time vs dimension (2 / 10 / 50)
- 04_memory_comparison.png
    - Bar chart: memory usage comparison
- 05_time_vs_threads.png
    - Line chart: Concurrent & Parallel time vs threads (Sequential as reference)
- 06_speedup_heatmap.png
    - Grouped bar: Parallel speedup across sizes × threads
- 07_efficiency.png 
    - Line chart: parallel efficiency (speedup / threads × 100%)

## Configurable Parameters

Edit `src/main/java/Main.java` to control what the benchmark measures:

```java
// Dataset sizes (number of points)
private static final int[] DATASET_SIZES = {10000, 100000, 1000000};

// Dimensions (number of coordinates per point)
private static final int[] DIMENSIONS = {2, 10, 50};

// K values (number of clusters)
private static final int[] K_VALUES = {3, 5, 10};

// Thread pool sizes for Concurrent and Parallel versions
private static final int[] THREAD_COUNTS = {1, 2, 4, 8};

// Maximum K-Means iterations (early convergence may stop sooner)
private static final int MAX_ITERATIONS = 100;
```

## CSV Output Format

| Column | Description |
|---|---|
| `Algorithm` | `Sequential`, `Concurrent`, or `Parallel` |
| `Threads` | Thread count (1 for Sequential) |
| `DataSetSize` | Number of data points |
| `Dimension` | Number of coordinates per point |
| `K` | Number of clusters |
| `TimeMs` | Execution time in milliseconds |
| `MemoryMB` | Approximate memory delta in megabytes |

## Dependencies

| Library | Purpose |
|---|---|
| **JUnit Jupiter 5** | Unit testing framework (test scope) |
| **XChart 3.8.8** | Pure Java charting library for visualization |


