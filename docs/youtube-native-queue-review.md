# Native queue branch review — 2026-10-15

Reviewed the branch changes from `e54643954` through `c65bd9f99`, including queue
state and native DEX bridges, playback/gesture ownership, title/details requests,
ratings, Save playlist, integration into existing patches, resources, Gradle tasks,
tests and documentation. The review fixes are subsequent commits on the same branch.

## Project rules checked

- [README contribution and license rules](../README.md): supported application scope,
  retained attribution and distinct identification/branding of derivative builds.
- [NOTICE](../NOTICE) and [LICENSE](../LICENSE): preserve notices and identify the
  modified version without implying upstream authorship.
- [PR template](../.github/PULL_REQUEST_TEMPLATE.md): test minimum/maximum supported
  versions. This experimental patch intentionally supports only 21.20.405.
- [Build workflow](../.github/workflows/build_pull_request.yml): JDK 21 and
  `:patches:buildAndroid`. Existing resource-string formatting rules were checked
  with the project's resource checker. No AGENTS.md or CONTRIBUTING file was
  present in the checkout or its instruction-bearing parent directories.

## Findings fixed

### P2 — Initial rating cache survived account changes

The process-local cache keyed ratings only by video ID. A later panel refresh could
initialize the selected thumbs using another account's cached rating. The native
entity store was account scoped, but the queue's initial-state cache was not reset.

Both native global account-provider implementations now notify the queue before
returning their current identity. A changed identity clears cached ratings and
schedules a panel refresh after the provider releases its lock. The returned native
identity is unchanged and its contents are not logged. Regression tests cover
unchanged identities, switching accounts, returning to a prior account, sign-out,
incognito and clearing states collected before the first observed identity.
Native entity subscriptions, event dispatch, authentication and voting remain intact.

### Contribution scope correction

The source branch should retain upstream project metadata and README branding for
an eventual contribution. The earlier review incorrectly treated the experimental
preview's distribution identity as a reason to rebrand the project on this branch.
Those source-level branding changes have been reverted; the rating-cache fix and
its regression tests remain.

Unofficial preview builds should be clearly identified in their distribution
metadata and download documentation. Any preview-specific metadata changes belong
in a separate build checkout, outside the contribution diff. Preserve upstream
license notices and attribution in both source and distributed builds. The
repository's branding rules do not require renaming an upstream feature submission.

## Validation

- Patch test task: 52 passed, 5 unrelated Jam APK tests skipped; no failures.
  All 49 native-queue tests ran and passed, including the schema-reader tests.
- String-resource checker: 90,681 strings passed.
- Actual patch/dependency application to stock YouTube 21.20.405 passed, including
  account-hook assertions, DEX verification, cross-DEX class hierarchy verification
  and resource compilation.
- Android bundle built and checked for `classes.dex` and retained upstream
  license/notice resources. The contribution branch retains upstream metadata.
- `git diff --check` passed.

No live account vote or playlist-creation request was submitted in cloud checks.
The optional earlier JVM probe of Android protobuf classes did not run successfully
and is not counted as validation. No emulator or attached Android device was
available for this review.

## Remaining limits

The existing device reports confirm navigation, ordering/removal, modes, metadata,
ratings and Save playlist. The new account-cache invalidation needs a device retest,
especially account switching/incognito and votes already in flight. Pause/resume,
rotation, network-error rollback, normal-account-playlist isolation and interactions
with the full selected patch set remain targeted device checks. The patch is still
experimental and pinned to one APK version; build verification does not establish
behavior on other versions. See the [current usage and device checklist](youtube-native-queue.md).

## Contribution-style refactor

The repository has no separate patch contribution style guide. Its linked
[patcher conventions](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/3_structure_and_conventions.md),
[patch anatomy](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_2_patch_anatomy.md)
and [fingerprinting guidance](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_2_1_fingerprinting.md)
provide the relevant coding guidance. Fingerprinting explicitly states:
“Obfuscated class and method names should never be used.” The patcher repository's
own CONTRIBUTING file governs contributions to that repository; it is not an
additional contribution policy for this patch repository.

The prototype's fixed host-class/member lookups have been replaced with named
fingerprints and references derived from stable resources, strings, signatures,
initializers and native type relationships. Protocol field/extension numbers are
schema identifiers. The protobuf Lite metadata reader resolves their actual DEX
fields from the APK, including repeated-element types, presence, oneofs and enum
verifiers. All references are resolved before installing native queue hooks;
missing or ambiguous matches fail patching rather than selecting a fallback.

The patch declaration is separate from fingerprints, reference discovery, schema
inspection and feature hook installation. Existing shared fingerprints for player
metadata, flyout renderers, account identity and navigation intents are reused.
The installer uses shared mutable-method and reverse-instruction helpers. Runtime
queue logic remains in the Java extension and uses typed native bridges. The
source branch preserves upstream project metadata, README and notices.

The stock-APK regression retains known 21.20.405 host names as an independent test
oracle for the previously working bindings. These names are not used in production
discovery. Additional synthetic-schema tests cover renamed fields/types, repeated
messages, multi-character field numbers, closed enum metadata, oneofs and control
flow through the generated message-info branch. The advertised target remains
21.20.405; dynamic discovery alone does not establish support for other versions.

This is preparation for a contribution, not a guarantee of acceptance. Maintainers
still decide feature scope and design, and the refactored build needs device
regression testing before submission.
