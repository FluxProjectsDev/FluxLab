# Security policy

Report security issues privately to `febricahya12345@gmail.com`. Include the affected FluxLab version, Android/root environment, reproduction steps, and impact. Do not include unrelated device logs, personal data, or root-manager credentials.

The highest-risk boundaries are `su` execution, exported files, native memory handling, and persisted report content. FluxLab mitigates them with fixed read-only commands, timeouts/cancellation, app-private temporary files, bounded allocations, JNI validation, Storage Access Framework destinations, no network permission, and no embedded secrets.

Supported security fixes target the current main development line. Public issues are appropriate for non-sensitive reliability bugs; suspected command injection, unauthorized writes, sandbox escape, or sensitive-data exposure should use private reporting.
