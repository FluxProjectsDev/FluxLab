# Report schema

FluxLab report schema version 1 is UTF-8 and local-only. Schema version is independent from application and workload versions.

## Session JSON

Top-level fields are `schema_version`, `report_type`, `application_version`, `session`, `device`, `flux`, `environment`, `workloads`, `warnings`, and `failure_reason`. Workload entries include kind, workload version, unit, repetitions, median, minimum, maximum, standard deviation, coefficient of variation, and validation checksum. Nullable unsupported values are JSON `null`; results are never synthesized.

Device/build fingerprints and Flux context exist to establish comparison compatibility. Reports do not include account identifiers, user files, unrelated logs, root-manager tokens, or signing data.

## Session CSV

CSV uses RFC-style quoted strings and stable columns:

```text
report_schema_version,session_id,session_status,workload_kind,workload_version,unit,repetition_index,value,duration_ns,median,minimum,maximum,standard_deviation,coefficient_of_variation
```

Each measured repetition has one row, with its unit and duration. A session with no successful workload results contains only the header.

## Comparison JSON and CSV

JSON comparison reports contain baseline and candidate UUIDs, environment warnings, and per-workload compatibility, absolute delta, percentage delta, improvement direction, and confidence. Incompatible workload versions contain null deltas and inconclusive confidence.

Comparison CSV uses stable per-workload columns for the same compatibility, delta, direction, confidence, and warning fields.

Consumers must reject unsupported major schema versions, use workload versions for compatibility, and interpret values using the accompanying units and direction rules.
