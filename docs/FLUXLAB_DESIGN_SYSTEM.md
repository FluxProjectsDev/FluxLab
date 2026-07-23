# FluxLab Material 3 design system

FluxLab uses Material 3 for interaction behavior, typography, navigation,
state layers, accessibility semantics, system bars, and adaptive layout. The
FluxLab layer supplies the monitoring identity: warm charcoal surfaces, a
restrained coral primary accent, semantic CPU/GPU/memory/storage/thermal/
battery colors, compact bounded graphs, circular gauges, and workload visuals.

## Theme and color modes

Appearance is independent from color style. Appearance defaults to Follow
system and also supports Light and Dark. Color style defaults to Flux and also
supports Dynamic system on Android versions that expose dynamic color. Dynamic
system color changes Material surfaces and controls only; metric colors remain
the stable semantic Flux palette so a GPU never becomes indistinguishable from
CPU telemetry.

`FluxSpacing`, `FluxShapes`, `FluxTypography`, `FluxMotion`,
`FluxChartTokens`, and `FluxElevation` are centralized in
`FluxDesignSystem.kt`. Light and dark metric variants are defined by
`FluxMetricColors`.

## Layout and disclosure

Overview uses a compact hero, real monitoring controls, system-status cards,
and a two-column phone / three-column wide metric grid. Detail destinations
use a Material 3 top app bar and hide top-level navigation. Technical paths,
parser warnings, storage topology, GPU driver paths, and raw thermal names are
progressively disclosed instead of being the primary metric.

Cards are complete in every capability state. Valid values remain visible when
only part of a provider is available; unavailable values explain the missing
capability rather than rendering a fabricated zero or sample.

## Graphs and motion

Graphs read immutable bounded histories from the telemetry repository. Canvas
code does not read files, query hardware, or launch one coroutine per point.
Missing samples create gaps; they are not filled with invented values. Gauges
show a track when a metric is unavailable. Accessibility semantics include a
plain-language chart summary in addition to color and shape.

Reduced benchmark visuals remove decorative layers and blur while preserving
stage, workload, repetition, and progress information. Full workload visuals
are driven by actual engine progress and worker count. Visualization has
measurable observation overhead; FluxLab does not claim zero overhead.

## Interaction contract

Every Overview card opens its corresponding validated provider detail. Start
and stop controls call the shared telemetry repository. Preset cards update the
persisted benchmark configuration, readiness is evaluated from live providers,
active-run cancellation is cooperative, sessions come from Room, and report
share uses the URI returned by the system document picker.

## Accessibility and regression strategy

Interactive cards and controls expose roles, labels, and state text, with
minimum Material touch targets and visible status beyond color. System font
scale, display scale, insets, light/dark appearance, dynamic color, and reduced
motion are respected. Hosted screenshot/golden tests should cover 360dp and
412dp phone widths, both Flux appearances, large text, capability gaps, and
benchmark stages. Garnet real-device validation remains required for hardware
capability and visual parity claims.
