# FluxLab production signing security

This document describes direct Android APK signing for FluxLab. It does not
configure or authorize Google Play publishing.

## Trust boundaries

The production signing key is manually created and stored in GitHub Environment
secrets under `production`. The repository does not contain a keystore or a
credential-bearing Gradle property file. The expected secret names are:

- `FLUXLAB_RELEASE_KEYSTORE_B64`
- `FLUXLAB_RELEASE_STORE_PASSWORD`
- `FLUXLAB_RELEASE_KEY_ALIAS`
- `FLUXLAB_RELEASE_KEY_PASSWORD`

Secret values must never be copied into source, `gradle.properties`,
`local.properties`, workflow literals, logs, or artifacts. The Gradle signing
configuration consumes only these process environment variables:

- `FLUXLAB_RELEASE_KEYSTORE_PATH`
- `FLUXLAB_RELEASE_STORE_PASSWORD`
- `FLUXLAB_RELEASE_KEY_ALIAS`
- `FLUXLAB_RELEASE_KEY_PASSWORD`

Debug builds do not require these variables. Release tasks fail with a
sanitized message when a variable is missing or the temporary keystore is not
a regular file.

## Workflow handling

The signed workflow uses the `production` environment and least-privilege
permissions: `contents: read`, `id-token: write`, and `attestations: write`.
There is no publishing job and therefore no `contents: write` permission.

The Base64 keystore is decoded only inside `$RUNNER_TEMP`, with `umask 077`
and an explicit mode of `600`. Only the temporary path is exported to
`FLUXLAB_RELEASE_KEYSTORE_PATH`. The workflow does not enable shell tracing or
echo secret variables. An `always()` cleanup step removes the temporary file,
including when validation fails.

The final APK is copied to a temporary output path before verification. ZIP
alignment and `apksigner verify --verbose --print-certs --Werr` inspect those
final bytes. No step mutates the APK after signature verification. The
workflow rejects the debug certificate and checks package, version, native
arm64 library, and adaptive launcher resources.

## R8 and native boundary

Release builds use the optimized Android default rules, resource shrinking,
and focused project rules. The only explicit JNI keep rule retains the
concrete `NativeBridge` class and its two native methods. Room, Compose,
serialization/report models, Flux, and SynthesisCore use direct references or
generated code in the current implementation; there is no package-wide
reflection keep rule. Any future reflective entry point must add a narrow rule
and a release smoke test with its actual class or member name.

R8 mapping and native diagnostics are not part of the public release artifact.
They may be requested manually as a separate short-retention diagnostic
artifact for protected incident analysis.

## Public outputs and provenance

A successful run publishes only the signed APK, its SHA-256 file, public
certificate metadata, build metadata, the AndroidTest APK, and validation
reports as workflow artifacts. The final signed APK is also submitted to
GitHub artifact attestation. Metadata includes package/version, SDK levels,
commit, tag or manual version, workflow run ID, timestamp, APK digest, and
certificate SHA-256 fingerprint.

Never upload the keystore, Base64 keystore, passwords, environment dumps, or
diagnostic files containing credential-bearing paths.

## Rotation and recovery

Direct APK signing depends on the existing certificate. To rotate it, create a
new approved key outside the repository, replace the four `production`
environment secrets through the repository’s protected administration path,
and compare the new fingerprint with the protected workflow output before
distributing an APK. An APK signed by a different direct-signing certificate
is a new trust identity for Android update purposes and must be handled as a
migration decision, not as a routine password change.

A future Google Play upload key is a separate operational credential. It must
not be substituted for the direct APK signing key and is outside this task.
