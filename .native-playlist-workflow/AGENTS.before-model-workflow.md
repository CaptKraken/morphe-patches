# Native playlist queue development

These instructions apply to `/workspace/morphe-patches`. They describe the user's
development workflow, not additional upstream contribution rules. Keep this file
and local review tooling outside the upstream patch diff. User instructions take
precedence; do not introduce a new permission step for routine authorized work.

## Scope and incremental delivery

- Work on `feature/youtube-native-playlist-queue`, starting from clean main.
  The feature is named **Native playlist queue**. Preserve the earlier queue
  branch as history. Do not copy code from the old queue branch into this rebuild.
  Implement fresh from the stock APK and current project APIs/patterns; old behavior
  and bug reports can inform the acceptance checks.
- Implement one agreed feature or fix at a time. State its observable behavior,
  affected native paths, existing project examples, and validation before editing.
  Do not add future-feature infrastructure or bundle unrelated cleanups.
- After the current channel-binding regression is fixed, prioritize playlist
  functionality. Defer further metadata and UI polish unless separately requested.
- Start with YouTube **21.20.405**. Broader version support is deferred until
  separately requested and verified. Do not advertise unverified targets.
- During iterative phone testing, keep Native playlist queue selected by default
  (`default = true`), as explicitly requested by the user. Queue additions should
  confirm with the existing toast and preserve browsing instead of opening the panel.
- Prefer YouTube's existing playlist UI and actions. Verify the native behavior
  before wiring it; do not recreate controls merely because hooking is harder.
- No PR, publication, or other external action is authorized by this file alone.
  Preserve any authorization already given in the conversation.

## Establish the actual project conventions

- Read applicable agent instructions, the PR template, nearby maintained patches,
  and relevant shared APIs before implementing. Check the pinned dependency API
  when uncertain. Distinguish explicit rules from observed coding patterns.
- Kotlin uses the official style (`gradle.properties`). Follow nearby Java style,
  meaningful camelCase names, uppercase constants, narrow visibility, and existing
  package/file organization. Do not copy unexplained abbreviations or old style
  inconsistencies. Explain non-obvious native assumptions and injection contracts.
- Keep patch-time discovery/modification in Kotlin patches and runtime behavior in
  extensions. Use existing settings, resources, logging, threading, events, and
  lifecycle utilities. Add a helper only when the current feature needs it.
- Preserve existing copyright, license, and NOTICE content. Do not invent upstream
  requirements or claim conformance from a formatter that is not configured.
- Keep APKs, research scripts, logs, generated outputs, and feature design/review
  notes outside the tracked patch diff. Release-generated patch lists and README
  sections normally belong to release tooling, not manual feature edits.
- Use conventional commits such as
  `feat(YouTube - Native playlist queue): <specific behavior>`.
  Upstream development PRs normally target `dev`; our chosen starting base is main.

## Code quality review

- Review correctness and maintainability, not just formatting. Keep methods and
  classes responsible for a clear job, names descriptive, and native assumptions
  documented. Avoid unnecessary abstractions, duplicate state, unchecked casts,
  reflection, and generic catch-and-continue paths that hide a failed operation.
- Identify the authoritative owner of each state value. Check initialization,
  disposal, threading, weak-reference lifetime, stale asynchronous callbacks,
  partial updates, and rollback where relevant. A visible queue is not proof that
  its native playback backend is still attached.
- Check nullability, error propagation, resource ownership, and unnecessary work
  on hot/UI paths. Report actionable errors using project utilities; do not turn
  expected lifecycle transitions into user-facing failures.
- Simplify code that needs repeated exceptions or brittle fallback assumptions.
  Resolve concrete review findings before committing. Do not label source-text
  checks or a successful build as a complete code-quality review.

## Fingerprint and native discovery review

- Use `app.morphe.patcher.Fingerprint` and existing instruction filters. Anchor on
  stable diagnostics, enum values, named resources, stable framework calls, method
  signatures, and relationships to already discovered classes/members.
- Keep reusable declarations in `Fingerprints.kt`. Scope related matches through
  `classFingerprint` or discovered types/references. Bounded instruction distance
  may express a verified relationship; arbitrary absolute offsets are not a
  substitute for discovery.
- Do not hardcode obfuscated host descriptors, member names, or numeric resource
  IDs in production discovery. Stable framework/unobfuscated names and references
  extracted from matches are appropriate. Document unusual stable anchors.
- Derive injection indexes, registers, and native references from matched
  instructions. Prefer original read-only methods/classes for inspection and
  request mutable ones for changes. Re-resolve when mutation invalidates positions.
- `.method` finds a matching method; it does not establish uniqueness. Use the
  library's `matchSingle()` or a bounded `matchAll` when ambiguity is a real risk.
  Required missing/ambiguous capability must fail clearly; do not guess a fallback
  or silently omit required hooks. Optional paths need an explicit reason.

## Injection and coexistence review

- Depend on `sharedExtensionPatch` and relevant shared hook/resource patches.
  Prefer registering callbacks with shared APIs over independently patching their
  methods. Understand `execute`/`finalize` order and callback ordering.
- Keep injected instructions small. Runtime logic belongs in the extension.
  Typed interfaces with generated `patch_*` bridge methods are an existing option
  for calling discovered native methods; do not invent reflection frameworks.
- Inspect the original control flow around every hook: validation, stale-response
  rejection, initialization, selection updates, return values, and exception paths.
  A correct method match does not establish that method entry is a safe hook point.
- Preserve branch targets and original execution with the established instruction
  and label helpers. Check register lifetime/type, parameter width, invoke encoding,
  move-result adjacency, and constructor initialization. Use `findFreeRegister`,
  `cloneParameters`, or an established safe register when needed; account for index
  shifts caused by cloning and previous edits. Do not assume `v0` is free mid-method.
- A shared fingerprint can cache stale indexes: follow existing `clearMatch()`
  usage when reusing a fingerprint after its target was modified. Never store an
  instruction index as though other patches cannot change it.
- List other patches using each changed hook, method, helper, setting, or native
  path. Keep their behavior and settings effective. Only intercept events actually
  owned by the local queue; let ordinary playback/account playlists fall through.
- Trace both direct and transitive dependencies, including patches that depend on
  any helper we change. Check that our feature is not pulled into unrelated patch
  selections and that shared code does not require our feature to be installed.
- Review whether an early return, replaced callback/listener, event consumption,
  state write, or forced visibility prevents another patch's action from running.
  Calling the same extension hook does not prove coexistence: follow the affected
  execution paths and check the user's enabled settings still have their effect.
- For each actual overlap, record the other patch, shared path, expected combined
  behavior, and evidence. Examples to investigate when relevant are playlist
  autoplay suppression, Loop video, player Next/Previous visibility, fullscreen
  action visibility, flyout overrides, metadata replacements, and rating hooks.
  Do not silently give this queue precedence over those patches. Resolve intended
  behavior from existing rules/user intent, and surface genuine unresolved product
  conflicts before implementing a conflicting behavior.
- Prefer chaining or shared callback registration to replacing existing listeners.
  Guard interception by the specific event, active queue ownership, and setting.
  Verify unrelated/account-playlist paths retain the original callbacks/actions.
- Verify behavior with this patch unselected/disabled. Use established
  `isPatchIncluded()` / `setExtensionIsPatchIncluded()` where shared runtime helpers
  need to distinguish inclusion. Avoid making other features depend on this one.
- Treat visible panel models, prefetched metadata, active playback, and navigator
  ownership as different things. Discover which callback is authoritative. Review
  late callbacks and close/reopen/handoff behavior where the feature touches them.

## Resources and versions

- Prefer native layouts/drawables/dimensions and `ResourceUtils`, `ThemeUtils`,
  `StringRef`, `Dim`, or existing UI helpers. Use existing localization/settings
  mechanisms; avoid hardcoded user-facing strings and ad hoc density conversion.
- `Dim.dp48` exists, but does not prove a native control should be 48dp. Reuse
  layout measurement/dimension resources when applicable; otherwise use a named,
  explained value only after checking the native layout.
- Keep declared compatibility, patch-time version gates, runtime version gates,
  and spoofed client versions distinct. Reuse `versionCheckPatch` flags and runtime
  `VersionCheckPatch` when a verified implementation difference needs branching.
- Current patch selection uses `default`; runtime setting defaults are separate.
  Newness alone does not determine either. Set policy deliberately for our stage.
- Do not copy the shared YouTube compatibility list and imply every listed target
  works. The PR template requests minimum/maximum supported-version testing and
  makes experimental-version testing optional. Build success alone is not support.

## Proportionate validation

- Add the smallest tests that catch a distinct realistic failure. Prefer behavior
  and boundary cases for actual production logic, not getters, source-text/regex
  snapshots, exact internal layouts, or tests that restate the implementation.
- Add tests with the current feature. Do not port the previous large suite or add
  broad test infrastructure in anticipation of future features. Parameterize only
  where it makes cases clearer. Fix a regression with a focused test when useful.
- Native discovery/injection changes need real-APK resolution and patch application
  plus DEX/resource verification appropriate to the change. Use existing patcher
  APIs and SDK verification facilities; avoid a second native runtime emulation.
- Exercise relevant shared-patch combinations when touching shared paths, and
  patch-unselected behavior. Choose combinations based on actual overlap, not every
  possible patch. Do not rerun expensive checks without a change or concern.
- Use repository checks as applicable: `:patches:test`,
  `:patches:checkStringResources` for resource strings, and `:patches:buildAndroid`
  for the distributable. Follow actual environment setup; these command names do
  not establish that prerequisites or validation have already passed.
- Keep passed, failed, skipped, and unrun checks distinct. No-APK skipped tests do
  not count as APK validation. A model test or JVM mock is not an Android playback
  test. Report device-only checks still needed without implying they passed.
- For local verification, use existing APKs and captured logs. The user has declined
  further log captures; do not ask again. Do not claim device reproduction without
  an actual attached device/emulator and evidence.

## Mandatory pre-commit gate

1. Inspect the complete staged diff and file list, plus the cumulative feature
   diff against the chosen base. Review all touched/created files, not only the
   latest edit. Confirm the change implements one agreed step and includes no
   generated files, scratch tools, secrets, accidental dependency, or release edits.
2. Review code quality, conventions/reuse, fingerprints, injection/control flow,
   patch interactions, versions/resources, and the focused test plan. Cite concrete
   source examples or explain why a category is not applicable. Resolve blocking
   findings; don't merely record them and commit anyway.
3. Run the checks justified by the actual change. Save real command/outcome
   evidence, including failures or limitations. Do not mark a check passed because
   similar code passed earlier. Evidence must cover the staged implementation;
   staging a different file version invalidates the review.
4. Stage the intended files and inspect `git diff --cached`. Generate a pending
   review record with:
   `.git/hooks/pre-commit --template > .git/native-playlist-queue-review.json`.
   Complete each review with `pass` or justified `not_applicable`, fill the actual
   validation evidence, and list outstanding device checks. The template starts
   pending; generating it is not a completed review.
5. Run `.git/hooks/pre-commit`, then commit normally only after it passes. The hook
   binds the record to HEAD, branch, and the exact staged tree and checks whitespace.
   It validates the presence of review/evidence, not the truth of semantic claims;
   code inspection and honest results remain mandatory.
6. Any staged change or HEAD/branch change requires a fresh review record. Never
   bypass the gate with `--no-verify`, alternate hooks, or fabricated evidence. If
   a required check cannot run, finish independent work and explain the specific
   blocker; do not claim completion or create a new permission requirement merely
   because a routine choice needs judgment. Device validation can remain pending
   for a code commit when clearly reported and no blocking code issue remains.
7. Report what changed, why, validation actually performed, and remaining device
   checks. Do not publish a new test bundle unless that step is authorized.

## Concrete references checked at the clean-main baseline

- `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/build_pull_request.yml`,
  `gradle.properties`, `patches/build.gradle.kts`, and `NOTICE`.
- `youtube/layout/buttons/action/Fingerprints.kt` and
  `QuickActionsMarginPatch.kt`: resource/cast match and extracted view register.
- `youtube/misc/loadvideo/LoadVideoHookPatch.kt`: typed native interface bridges.
- `youtube/video/videoid/VideoIdPatch.kt`: shared hooks and prefetch warnings.
- `youtube/video/playerresponse/PlayerResponseMethodHookPatch.kt`: register handling
  and ordered callback injection in finalize.
- `youtube/layout/buttons/action/HideVideoActionButtonsPatch.kt`: shared fingerprint
  invalidation after other patches may modify the target.
- `youtube/layout/shortsnoresume/DisableShortsResumingOnStartupPatch.kt`: verified
  version branches with separate fingerprints.
- `extensions/youtube/.../patches/utils/FlyoutUtils.java`: native layout inflation,
  dimensions, theme, and version fallback; reuse this utility when applicable.
- `extensions/youtube/.../patches/SaveToWatchLaterPatch.java`: inclusion marker,
  existing runtime utilities, and localized feedback.
- The pinned patches-library supplies `ResourceUtils`, `Dim`, `matchSingle`,
  `setExtensionIsPatchIncluded`, and bytecode helpers. Inspect its pinned API rather
  than relying on memory or copying a similarly named helper.
- Upstream PR #3758 review removed unused listener infrastructure:
  https://github.com/MorpheApp/morphe-patches/pull/3758#discussion_r4302221275

Reference paths beginning `youtube/` are relative to
`patches/src/main/kotlin/app/morphe/patches/`. Recheck guidance when the base or
dependency versions change; these examples are not a claim that every old file
follows a single perfectly consistent style.
