# FluxLab release process

FluxLab releases are built and verified by GitHub Actions. Local Termux/proot
workspaces are limited to source review, editing, and Git operations; they do
not run Gradle, Android Build Tools, or APK signing.

## Inputs

The release workflow runs from:

- a `vX.Y.Z` tag, which supplies `versionName` and derives a positive
  `versionCode` from the checked-out commit history; or
- `workflow_dispatch`, which requires `version_name` in `X.Y.Z` form and a
  positive `version_code`.

The job waits for the Gradle wrapper validation job and then uses the
`production` GitHub Environment. Environment approval and secret access are
controlled by repository administration, not by source files.

## Hosted validation order

The workflow checks the wrapper, checks out source, installs Java and Android
SDK components, configures the one Gradle cache, validates dependency
configuration, runs unit tests and lint, compiles the AndroidTest APK, and
builds the minified/shrunk signed release. It then verifies ZIP alignment,
runs `apksigner verify --verbose --print-certs --Werr`, inspects package and
version metadata, checks release debuggability, rejects the Android debug
certificate, confirms the native arm64 benchmark library and adaptive icon,
generates SHA-256/certificate/build metadata, attests the final APK bytes,
uploads public artifacts and reports, and removes the temporary keystore.

A release task with missing signing inputs fails before a signed APK can be
produced. Secret values are not included in failure messages.

## Artifact names

A successful version `X.Y.Z` run produces these public files:

- `FluxLab-vX.Y.Z-release.apk`
- `FluxLab-vX.Y.Z-release.apk.sha256`
- `FluxLab-vX.Y.Z-certificate.txt`
- `FluxLab-vX.Y.Z-build-metadata.json`

The AndroidTest APK and test/lint reports are separate artifacts. R8 mapping
and native diagnostics are excluded from public release artifacts by default;
a manual run may request them as a short-retention protected diagnostic
artifact.

## Verification and distribution

Review the certificate fingerprint and APK digest from the workflow artifacts
before distribution. The build metadata records the package
`com.febricahyaa.fluxlab`, version, SDK levels, commit, tag/manual source,
workflow run, timestamp, APK SHA-256, and certificate fingerprint.

This process signs a directly installable APK. It does not upload to Google
Play, create a GitHub Release, create tags, or manage a Play App Signing key.
A future Play upload key and its publishing workflow require a separate threat
model, permission review, and recovery procedure.

## Failure and recovery

Do not bypass the production environment, copy the keystore to a workstation,
or disable signature verification to recover a failed run. Inspect sanitized
logs and validation reports. If the key or a credential may be exposed, stop
distribution and follow the rotation procedure in
[`SECURITY_SIGNING.md`](SECURITY_SIGNING.md). Historical commit-signing or DCO
issues are reviewed separately and are not repaired by rewriting release
history.
