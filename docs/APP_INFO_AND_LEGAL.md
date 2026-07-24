# About and Legal architecture

FluxLab exposes About and Legal as a navigable detail hierarchy below
Settings. It is not a bottom-navigation destination. The hub uses the same
Material 3 scaffold, Flux semantic accents, warm surfaces, bounded spacing,
and progressive disclosure as the monitoring dashboard.

## Content sources

- Package and build identity come from Android `PackageInfo`, `ApplicationInfo`,
  `BuildConfig`, and runtime ABI information.
- Changelog metadata is maintained in `AboutLegal.kt` as versioned structured
  release metadata. Production does not include future or sample versions.
- Privacy, terms, and license documents are bundled raw Markdown resources with
  English and Indonesian variants. The repository parses them off the main
  thread before the UI renders them.
- License links are limited to the license URLs represented by the bundled
  dependency inventory.

## Update-provider contract

`UpdateProvider` exposes checking, available, up-to-date, failure, network
unavailable, and source-not-configured states. The current production provider
is intentionally `UnconfiguredUpdateProvider` because this local-first build
has no network permission or project-owned update manifest. It therefore never
fabricates a latest version, release date, file size, or download action.

A future provider must supply the installed-version comparison, latest version,
release date, optional file size, channel, summary, release URL, and release
notes URL. It must preserve honest failure states and remain explicit about
network behavior.

## Credits and support

The designer card identifies FebriCahyaa as Android Developer & Designer. No
personal profile URL is displayed because none is configured. Support uses the
real FluxLab project page URL; donation actions remain hidden until a genuine
provider is configured.

## Accessibility and testing

Hub rows are full-card Material interactions with semantic roles and status
text. Version details support copy and share through Android system actions.
Changelog entries expand and collapse from structured data. Tests cover
Markdown parsing, localized changelog availability, update-source honesty, and
detail-route bottom-navigation exclusion. Garnet validation remains required
for device rendering and external-link behavior.
