# Privacy

FluxLab is local-first measurement software.

- The application has no internet permission and no network client, analytics SDK, advertising SDK, crash upload, or telemetry upload.
- Live telemetry stays in bounded memory. Benchmark sessions and preferences stay in the app sandbox through Room and DataStore.
- Export occurs only after the user chooses a destination through Android's Storage Access Framework. FluxLab requests no broad storage permission.
- Reports contain hardware/model, Android build fingerprint, kernel, Flux state, and measurement environment because those fields are necessary to evaluate compatibility.
- Reports exclude accounts, contacts, location, unrelated logs, user documents, root-manager secrets, and Flux configuration contents unrelated to measurement.
- Root is used only for allowlisted read operations. Denial does not trigger repeated prompts and does not disable non-root features.

Uninstalling FluxLab removes its sandboxed database and preferences subject to Android backup settings. The Settings screen provides an explicit local-history reset. Files exported by the user remain under the destination provider's control and must be deleted there.
