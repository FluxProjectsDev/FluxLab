# Contributing

Use JDK 21 and the pinned Android/Gradle toolchain. Keep changes focused and run:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

New metrics must name a real source, expose units and missing-data behavior, and include parser/validation tests. Never infer unsupported GPU utilization, FPS, power, or temperature labels. Benchmark changes require a workload-version increment, deterministic validation, bounded resource use, cancellation cleanup, and an update to the methodology and report documentation.

Runtime integration must remain read-only and must use the `RootCommand` allowlist. Do not add arbitrary shell input, network telemetry, broad storage permission, signing secrets, copied Flux/SynthesisCore source, submodules, or symlinks.

Commits should be reviewable and include:

```text
Signed-off-by: FebriCahyaa <febricahya12345@gmail.com>
```
