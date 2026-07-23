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

## Phase 3 reliability and diagnostics

FluxLab keeps telemetry sampling separate from Compose rendering. Hardware reads
run from the existing coroutine telemetry source and the UI retains a bounded
120-snapshot history; chart interpolation is visual only and raw samples remain
unchanged in session/report data. Missing GPU, thermal, power, or storage data is
shown as an explicit capability limitation rather than a fabricated value.

The selected Quick, Standard, or Extended preset is resolved through
`BenchmarkPresetConfig.forPreset` and the immutable configuration is copied into
the benchmark environment at start. Progress is represented by explicit stage,
workload, and repetition fields. Cancellation is cooperative and invokes the
existing private-storage cleanup path. Reduced visual feedback is the default
benchmark mode to limit measurement interference; live monitoring can add small
observation overhead and should be recorded when comparing runs.

Battery diagnostics preserve BatteryManager raw units and only normalize charge
counter values to mAh when the source is µAh or mAh. Estimated battery health is
`fullChargeCapacity / designCapacity` and is explicitly an estimate; Android's
health enum and cycle-count availability are separate capabilities. Energy
capacity is not converted to charge capacity without a validated voltage basis.

Storage is separate from RAM. FluxLab dynamically probes block metadata and can
identify UFS, eMMC, virtual, unknown, or inaccessible topology. UFS/eMMC lifetime
fields are coarse descriptor buckets and are never presented as an exact health
percentage. Buffered app-file reads, durable writes, and fsync latency measure
application-visible filesystem behavior, not guaranteed physical UFS throughput.
Health/lifetime metadata and benchmark performance are reported independently.

The app follows the system Material 3 theme by default, uses dynamic color on
supported Android versions with a stable fallback palette, and enables edge to
edge. Raw paths and normalization details remain in technical sections so the
Overview stays readable while exports preserve source and warning metadata.


## Phase 3.1 measurement integrity notes

Preset allocation is read from the selected immutable 'BenchmarkPresetConfig' at run start: Quick,
Standard, and Extended use distinct allocation ceilings and repetition counts. The UI formats
that single byte value once; it does not concatenate a raw and localized representation.

Live telemetry is sampled on the configured interval from the existing IO-backed source. Compose
sparklines consume bounded histories of real samples and never perform filesystem reads during
animation. A full benchmark visual mode is available for workload-specific feedback; reduced
mode is the default and is persisted in session metadata because visuals can add observation
overhead. Full-mode visuals map CPU integer, CPU floating point, multithread,
memory, storage, and fsync workloads to distinct lightweight forms. Their position
is derived from actual engine progress; they do not advance independently and no
GPU animation is shown without a GPU workload. Neither mode changes native
measurements or pretends to provide progress that the engine has not reported.

The active-run destination is driven by explicit preflight, countdown, warm-up,
running, inter-workload cooldown, finalization, completed, cancelling, cancelled,
and failed states. Cancellation stops new work, cooperatively cancels the active
workload, removes recognized temporary benchmark files, and persists partial
diagnostics as a cancelled session. Hosted CI remains authoritative for build and
test results; garnet acceptance is a separate real-device check.
