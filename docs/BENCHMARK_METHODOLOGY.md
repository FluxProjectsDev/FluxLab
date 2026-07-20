# Benchmark methodology

Quick Test is intended for controlled before/after comparisons on the same device. Each workload has a version, deterministic seed or byte pattern, warm-up, measured repetitions, monotonic timing, validation checksum, bounded allocation, and cancellation path.

## Native workloads

- CPU integer: seeded integer mixing reported as operations per second.
- CPU floating point: deterministic arithmetic reported as operations per second.
- CPU multithreaded: the integer kernel distributed over 2–8 worker threads, based on available processors.
- Memory copy and fill: 16 MiB buffers with repeated transfers, reported in MiB/s.
- Memory latency: deterministic pointer chasing over a 16 MiB working set, reported in ns/access.

The native implementation uses C++20, `std::chrono::steady_clock`, observable checksums, and an atomic cancellation flag. FluxLab does not force CPU affinity in this milestone; the recorded affinity flag remains false.

## Storage workloads

Storage uses a bounded temporary file in FluxLab's private cache, never the Flux module directory. The exact preset configuration controls warm-up count, measured repetitions, workload size, and allocation ceiling. Before allocation, FluxLab checks usable private space and reserves a 32 MiB safety margin (or the configured margin). Cancellation and failures clean up recognized `fluxlab-benchmark-` and legacy `quick-test-` files; startup cleanup never touches unrelated files.

The stable report identifiers remain `STORAGE_WRITE`, `STORAGE_READ`, and `STORAGE_FSYNC`, while user-facing labels are **Buffered sequential write**, **Buffered app-file read**, and **Durable write with fsync**. Buffered write, read, and fsync timing are separated; each read validates the deterministic CRC32 checksum. The read result is an app-level buffered read, not guaranteed physical UFS speed: Linux page cache, filesystem, encryption, controller cache, background I/O, and thermal state can all affect it. FluxLab never drops global page caches or changes kernel VM state.

## Statistics

The raw repetition list is retained. FluxLab calculates median, minimum, maximum, sample standard deviation (`n - 1`), and coefficient of variation (`standard deviation / |mean|`). Invalid or non-finite measurements fail the workload. Wall-clock time is used only for session timestamps; measurements use native steady clock or `SystemClock.elapsedRealtimeNanos()`.

Comparison is permitted only for matching workload versions. Throughput workloads improve upward; latency workloads improve downward. Confidence is inconclusive when effect size does not exceed variability or environments differ materially, possible when evidence is mixed, and likely only when the effect clearly exceeds variability with compatible environments. There is no composite score.

## Readiness and interpretation

The guard blocks duplicate runs, critically low private storage, severe-or-higher Android thermal status, and invalid execution state. Charging, low battery, stale Flux state, active monitoring, and high background CPU produce warnings. Charging is not an unconditional block. FluxLab relies on Android's device-specific thermal severity instead of applying a universal temperature threshold.

For useful comparisons, keep device, Android build, kernel, Flux profile, charging state, ambient temperature, and foreground conditions consistent. Repeat sessions and treat small noisy changes as inconclusive.
