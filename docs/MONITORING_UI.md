# Monitoring UI architecture

FluxLab presents one sampled telemetry stream across Overview, metric details,
and active benchmark execution. Compose screens do not instantiate hardware
providers. The application-owned telemetry repository retains the last valid
counter snapshot and bounded history, while the ViewModel exposes immutable
dashboard state to lifecycle-aware collectors.

## State and rendering

Source states are explicit: inactive, starting, collecting initial samples,
active, paused, temporarily unavailable, permission denied, unsupported,
malformed, stale, and failed. A first counter read is collecting, not
unsupported. A temporary read failure preserves the last valid snapshot and
retries with a warning.

Charts consume at most 120 real points. Overview sparklines use a smaller
derived window; detail charts show min/average/maximum and sample state.
Canvas rendering never performs filesystem I/O. Reduced benchmark visuals
disable or simplify motion so visual feedback does not pretend to be a
measurement.

## Hierarchy and disclosure

The Overview hero uses a high-tonal system surface. Metric cards use a lower
container surface, leading semantic icons, full-card click targets, and a
sparkline only when at least two valid points exist. Detail destinations use a
Material 3 TopAppBar and do not show top-level bottom navigation.

Normal users see normalized values and concise capability reasons first.
Procfs/sysfs paths, raw GPU models, parser warnings, storage topology traces,
raw thermal names, and source units stay behind technical disclosure. Reports
and exports retain raw values and warnings for reproducibility.

## Benchmark execution

The selected preset is frozen into the benchmark environment before execution.
The active route reads explicit engine stage, workload, repetition, elapsed
time, and real progress fields. Workload visuals are selected by the active
WorkloadKind and use the engine's progress fraction; they do not advance on
their own. Cancellation is cooperative and cleanup remains engine-owned.

## Accessibility and performance

Dynamic color, light/dark mode, system font scale, reduced motion, and fallback
colors are supported through the existing Material 3 theme. Semantic card
labels describe title, value, and supporting state. Sampling happens outside
the main thread, histories are bounded, and optional idle monitoring can stop.
Benchmark-required telemetry remains active. Visualization adds some observation
overhead; FluxLab does not claim zero overhead.

## Device limitations

OEM access to cpufreq policies, KGSL busy counters, PSI, ZRAM metadata, storage
descriptors, thermal zones, and battery capacity nodes varies. Unsupported or
permission-denied information remains unavailable rather than inferred from
device marketing names or guessed battery capacities. Garnet acceptance still
requires hosted APK testing on the target device.
