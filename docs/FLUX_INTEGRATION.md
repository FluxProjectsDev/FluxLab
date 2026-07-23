# Flux and SynthesisCore integration

FluxLab uses read-only runtime evidence. It does not embed Flux or SynthesisCore source and does not use submodules or symlinks.

## Paths and signals

The adapter checks:

- `/data/adb/modules/flux` for module installation.
- `/data/adb/modules/flux/module.prop` for `id`, `version`, `versionCode`, and descriptive enabled state.
- `/data/adb/.config/flux` for runtime availability.
- `/data/adb/.config/flux/current_profile` and optional runtime status for the active profile.
- `/data/adb/.config/flux/synthesis_core.json` and its modification time.
- `pidof fluxd` for daemon evidence.

Signals are combined into nullable/capability-driven fields. Installation does not imply daemon availability, and absence of one optional file does not erase other evidence.

## Safe root boundary

`SuRootGateway` starts `su -c` only for an internal `RootCommand` enum. Each enum maps to a constant shell statement; user text is never concatenated. Results include stdout, stderr, exit code, timeout, and cancellation. A four-second timeout destroys a hung process. Availability is cached to prevent permission-request loops. Root denial leaves the public telemetry and benchmark paths operational.

The allowlist contains only identity checks, file existence/read/stat operations, and daemon lookup. No command writes Flux configuration, sysfs, governors, thermal nodes, profiles, or module state.

## SynthesisCore formats

The current reference implementation writes schema-2, line-oriented key/value snapshots using atomic rename and a roughly two-second heartbeat, despite the historical `.json` filename. FluxLab therefore accepts both:

```json
{"schema_version":2,"focused_package":"com.example","focused_pid":123}
```

```text
schema_version 2
focused_app com.example 123 10123
screen_awake 1
```

The flat parser accepts `key=value` and whitespace separators, comments, missing fields, Boolean aliases, unknown extension fields, and legacy focused-app triples. Numeric ranges are validated and invalid values become null with warnings. A reader retries three times with a 75 ms delay when a concurrent update appears incomplete. File age is classified as fresh (up to 3 s), delayed (up to 10 s), or stale; missing modification time remains unknown.

The integration deliberately does not require strict schema 2 so older and future Flux releases degrade gracefully.
