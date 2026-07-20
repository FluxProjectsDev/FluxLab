# FluxLab

**Measure every change. Verify every gain.**

FluxLab is a native Android performance measurement, validation, and regression-testing application for measuring the real-world effects of the Flux runtime optimization module. It combines environment capture, live device telemetry, repeatable native workloads, persistent sessions, baseline comparison, and local report export.

FluxLab is **not** a second Flux control panel. It does not change profiles, governors, thermal configuration, sysfs nodes, or Flux configuration. Flux applies optimizations, SynthesisCore observes Android state, and FluxLab measures and reports what happened.

## Current milestone

The Quick Test runs deterministic C++/JNI integer, floating-point, multithreaded CPU, memory-copy, memory-fill, and pointer-chasing workloads. Optional storage measurements cover bounded sequential write/read and fsync latency using only FluxLab's private cache. A completed storage run requests at most approximately 29 MiB of writes and removes its temporary file on success, failure, and cancellation.

Results retain every measured repetition plus median, minimum, maximum, sample standard deviation, coefficient of variation, validation checksum, workload version, unit, and timing. There is deliberately no overall score.

## Root and limited mode

FluxLab uses the normal Android `su` entry point through a fixed command allowlist. Root status is explicit: unknown, checking, available, denied, unavailable, or error. Commands have timeouts and capture stdout, stderr, and exit status; cancellation terminates the process. No composable or ViewModel can submit shell text.

With root, FluxLab reads evidence from `/data/adb/modules/flux`, `/data/adb/.config/flux`, module metadata, the Flux daemon process, profile/runtime files, and `/data/adb/.config/flux/synthesis_core.json`. Without root, the app remains usable for public Android telemetry and benchmarking while marking Flux-only fields unavailable.

## Telemetry

Telemetry is read locally from Android framework APIs and Linux procfs/sysfs:

- CPU usage deltas from `/proc/stat`, per-core frequency/online/governor data when readable.
- Memory totals from `/proc/meminfo`, memory PSI, major page faults, swap, and detectable ZRAM.
- Android thermal status/headroom, named thermal zones, and battery temperature.
- Battery level, charging source, current, voltage, charge counter, and estimated instantaneous power only when inputs are valid.
- GPU vendor/model and reliable frequency nodes only; GPU utilization is not reported.
- Device/build/kernel identity, uptime, GKI indication, and display refresh rate.

Monitoring runs only while the user starts a benchmark or explicitly enables live monitoring. Samples are bounded in memory and are not uploaded.

## Measurement limitations

Quick Test is an application-level comparative workload, not a substitute for laboratory power equipment, a full storage qualification suite, or device-wide profiling. Android scheduling, background work, charging, thermal control, filesystem cache, OEM kernel interfaces, and ambient conditions can change results. Small deltas inside observed variability are classified as inconclusive. Thermal zones retain their source names; an unknown zone is never relabeled as CPU temperature. Estimated battery power is not energy consumption.

## Build

Requirements: JDK 21, Android SDK 36, NDK `28.2.13676358`, and CMake/Ninja. On a standard x86_64 Linux CI host:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Google currently distributes the Linux Android tools used here as x86_64 binaries. The repository wrapper transparently runs AAPT2 directly on x86_64. For an arm64 Linux development host, install `qemu-x86_64`; the project also uses native Clang with the official NDK sysroot. These host adaptations do not affect Android runtime code or x86_64 CI.

## Privacy and reports

FluxLab declares no internet permission, includes no analytics or upload SDK, and keeps telemetry, settings, Room sessions, and reports local. JSON and CSV exports use Android's Storage Access Framework, so no broad storage permission is requested. Reports include device/build and Flux environment context necessary for comparison; they exclude accounts, unrelated logs, user files, root-manager tokens, and secrets.

## Flux and SynthesisCore relationship

Flux and SynthesisCore remain independent projects and are not copied, linked, or embedded. FluxLab integrates through stable runtime adapters and read-only files/commands. The SynthesisCore adapter accepts flat JSON objects, key-value lines, legacy focused-app lines, missing fields, unknown future fields, stale snapshots, malformed/partial writes, and older schemas without crashing.

See [architecture](docs/ARCHITECTURE.md), [methodology](docs/BENCHMARK_METHODOLOGY.md), [integration contract](docs/FLUX_INTEGRATION.md), [report schema](docs/REPORT_SCHEMA.md), and [privacy](docs/PRIVACY.md).

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
