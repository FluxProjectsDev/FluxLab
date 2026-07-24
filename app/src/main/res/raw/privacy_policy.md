# Privacy Policy

Last reviewed: FluxLab 0.1.0

FluxLab is local-first diagnostic and benchmarking software. It collects device telemetry only to render monitoring views and to calculate benchmark results requested by the user.

## Data kept on the device

- Live samples remain in bounded memory and are not uploaded by FluxLab.
- Benchmark sessions and application preferences are stored in the application sandbox through Room and DataStore.
- Hardware identity, Android build information, thermal conditions, battery conditions, and measurement warnings may be preserved in a benchmark session because they are needed to interpret reproducibility.

## Root and system access

Root access is optional. FluxLab uses it only for allowlisted diagnostic reads. A denied or unsupported capability remains unavailable and does not prevent non-root measurements.

## Export and sharing

Reports are written only after the user chooses a destination through the Android document picker. Sharing is initiated only by an explicit user action. Exported files are controlled by the selected destination provider and are not deleted by uninstalling FluxLab.

## Network and analytics

The current application does not request network access and does not include analytics, advertising, crash-upload, or silent telemetry-upload behavior. A future update source must be explicitly configured and documented before update checks are enabled.

## Retention and deletion

Uninstalling FluxLab removes its sandboxed database and preferences subject to Android backup behavior. The Settings screen can reset stored benchmark history. Files exported outside the sandbox must be managed at their destination.

## Contact and changes

This policy is maintained with the FluxLab source. Material changes will be described in the changelog distributed with the corresponding application version.
