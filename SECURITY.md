# Security policy

Report security issues privately to `febricahya12345@gmail.com`. Include the affected FluxLab version, Android/root environment, reproduction steps, and impact. Do not include unrelated device logs, personal data, or root-manager credentials.

The highest-risk boundaries are `su` execution, exported files, native memory handling, and persisted report content. FluxLab mitigates them with fixed read-only commands, timeouts/cancellation, app-private temporary files, bounded allocations, JNI validation, Storage Access Framework destinations, no network permission, and no embedded secrets.

Supported security fixes target the current main development line. Public issues are appropriate for non-sensitive reliability bugs; suspected command injection, unauthorized writes, sandbox escape, or sensitive-data exposure should use private reporting.

## Production release signing

Production signing is performed only by GitHub Actions in the `production`
environment. The repository contains no keystore, password, alias value, or
signing property file. The release workflow receives the four environment
secrets only in the steps that materialize and use them; it never writes them
to `GITHUB_ENV`, an artifact, or a log.

The direct APK signing certificate is separate from any future Google Play
upload key. Play publishing is intentionally not configured here. Rotation,
recovery, certificate verification, and the boundary between public release
metadata and protected diagnostics are documented in
[`docs/SECURITY_SIGNING.md`](docs/SECURITY_SIGNING.md).

If a signing secret is suspected to be exposed, pause release runs, revoke or
rotate the affected GitHub Environment secret, verify the replacement
certificate fingerprint in a protected workflow run, and record the incident
without adding secret values to an issue or commit.
