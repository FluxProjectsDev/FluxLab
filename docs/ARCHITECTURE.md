# Architecture

FluxLab is split into five Android modules with one-way dependencies.

```text
app -> core:benchmark -> core:model
    -> core:data      -> core:model
    -> core:integration -> core:model
```

`core:model` defines immutable domain models and the `RootGateway`, `FluxRuntimeReader`, `SynthesisCoreReader`, `DeviceTelemetrySource`, `BenchmarkWorkload`, `BenchmarkSessionRepository`, and `ReportExporter` boundaries. It has no Android UI or persistence policy.

`core:integration` implements the bounded `su` gateway, Flux evidence aggregation, tolerant SynthesisCore parsing, and Android/procfs/sysfs telemetry. Root commands are represented by an enum mapped to constant command vectors. No arbitrary command API crosses the module boundary.

`core:benchmark` owns native C++ workloads, Kotlin orchestration, statistics, readiness policy, bounded storage work, progress state, cancellation, cleanup, and partial-session updates. C++ performs CPU and memory measurement; Kotlin owns lifecycle and persistence.

`core:data` owns Room entities, schema migration, repository mapping, direction-aware comparison, and versioned JSON/CSV serialization. Workload rows remain separate from session metadata instead of becoming a large opaque blob.

`app` is the Compose/Material 3 presentation and application composition root. `AppViewModel` communicates only with domain interfaces and orchestrators. `MonitoringService` is active only for user-initiated monitoring/benchmarks and displays an ongoing notification.

## State and failure model

Root and readiness use sealed states. Benchmark sessions move through preparing, warming up, running, cooling down, and terminal completed/cancelled/failed states. Every transition is persisted. Native failure, cancellation, database failure, missing root, malformed runtime data, and unavailable OEM nodes remain distinguishable rather than being converted into successful measurements.

Live telemetry is a cold Flow sampled on an IO dispatcher. The UI retains at most 60 CPU points. Benchmark environment snapshots and aggregate workload repetitions are stored; every high-frequency live sample is not written to Room.

## UI

The application uses a dark-first restrained Material 3 palette with a warm gold accent. Phones use bottom navigation; wider layouts use a navigation rail. Overview, Tests, Sessions, Reports, and Settings render capability-aware values. Charts use real timestamped samples, a fixed truthful CPU percentage range, and gaps for missing samples.
