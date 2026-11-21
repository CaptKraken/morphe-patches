# Native playlist queue development

These instructions apply to `/workspace/morphe-patches` and its isolated
`/workspace/morphe-patches-dev-integration` worktree. They describe the user's
development workflow, not additional upstream contribution rules. Keep this file
and local review tooling outside the upstream patch diff. User instructions take
precedence; do not introduce a new permission step for routine authorized work.

## Scope and incremental delivery

- Work directly in the primary conversation. Do not use subagents, model assignment
  tables, delegated stages, or agent handoffs for this development flow. The user
  removed that workflow. Read and maintain
  `/workspace/.native-playlist-workflow/STATE.md` so work resumes after resets.
  Keep the mandatory pre-commit gate and proportionate validation below.

- Preserve `feature/youtube-native-playlist-queue`, based on clean main, as the
  historical implementation and PoC57 fallback. The user subsequently authorized
  an independent branch `feature/youtube-native-playlist-queue-dev`, based on
  upstream dev, in `/workspace/morphe-patches-dev-integration`. Continue current
  preparation there; keep its history clean and incremental, without merging or
  replaying failed PoCs/debug/fixups from the historical branch. Read STATE.md for
  the latest approved base and commits. The feature remains **Native playlist queue**.
  Do not copy code from the earlier, abandoned queue branch. The dev port uses the
  final working implementation from our current historical branch, as requested.
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
- Reuse YouTube's native behavior as much as possible to achieve the requested
  result. Trace the existing implementation first, including request builders,
  authentication, pagination, scroll/load triggers, playback, UI, and lifecycle
  cleanup. Prefer connecting those native paths over recreating their behavior
  or inventing thresholds, timing, and state. Use existing project helpers where
  they already expose the needed native capability. If custom coordination is
  necessary, keep it narrow and explain which native capability could not be
  reused and why. Verify the native contract on the actual target APK; do not
  claim native reuse merely because a custom implementation calls a YouTube API.
- No PR, publication, or other external action is authorized by this file alone.
  Preserve any authorization already given in the conversation.

## PR titles and descriptions

- Before drafting, reviewing, creating, or updating any Morphe Patches PR title or
  description (including text-only replies), read and apply the local skill
  `/workspace/.native-playlist-workflow/skills/morphe-pr-writing/SKILL.md`.
- Always include the user's early AI-assistance disclosure unless they explicitly
  ask to omit it. Re-read the current repository PR template and preserve every
  section and the exact Test results checklist; do not infer device test coverage
  from builds, unit tests, or APK verification. This is a local user preference,
  not an additional upstream contribution rule or publication permission step.

## Establish the actual project conventions

- Use `is...` names for new boolean fields, local variables, and parameters
  (`isQueue`, `isPressed`, `isEnabled`); use `IS_...` for new boolean constants.
  This is the user's coding convention. Preserve required external/API names and
  avoid unrelated mass renames of existing code or settings.
- Consult `/workspace/.native-playlist-workflow/IMPLEMENTATION-PATTERNS.md` for
  relevant reusable examples before implementation/review. Record useful reviewed
  patterns with source links, applicability, and safety limits for future work.
- Read `/workspace/.native-playlist-workflow/MAINTAINER-CONVENTIONS.md` before
  implementation and review. It records concrete PR #3953 maintainer edits and
  their source examples. Treat these as observed conventions, not invented
  upstream requirements. Apply them to new/touched code without bundling unrelated
  mass formatting or reverting tested behavior fixes.
- Follow the maintainer's explicit Java blocks (including one-line guards) and
  expanded Kotlin/Smali call formatting. Use eligibility checks where a
  caller only needs to know whether an action is available. Preserve ownership
  checks and disabled-path cleanup when refactoring.
- Read applicable agent instructions, the PR template, nearby maintained patches,
  and relevant shared APIs before implementing. Check the pinned dependency API
  when uncertain. Distinguish explicit rules from observed coding patterns.
- Kotlin uses the official style (`gradle.properties`). Follow nearby Java style,
  meaningful camelCase names, uppercase constants, narrow visibility, and existing
  package/file organization. Do not copy unexplained abbreviations or old style
  inconsistencies. Explain non-obvious native assumptions and injection contracts.
- Keep patch-time discovery/modification in Kotlin patches and runtime behavior in
  extensions.
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

- Keep reusable declarations in `Fingerprints.kt`. Document unusual anchors and
  check the matched owner, operands, and control flow separately from uniqueness.
  Resolve resource IDs by name and document why a native path can be absent.
- Inspect original read-only methods/classes before mutating them. Re-resolve
  instruction positions after edits that shift them. Verify that the pinned
  programmatic APK harness actually enables fingerprint validation.

## Injection and coexistence review

- Depend on `sharedExtensionPatch` and relevant shared hook/resource patches.
  Prefer registering callbacks with shared APIs over independently patching their
  methods. Understand `execute`/`finalize` order and callback ordering.
- Keep injected instructions small.
- Inspect the original control flow around every hook: validation, stale-response
  rejection, initialization, selection updates, return values, and exception paths.
  A correct method match does not establish that method entry is a safe hook point.
- Preserve branch targets and original execution with the established instruction
  and label helpers. Check register lifetime/type, parameter width, invoke encoding,
  move-result adjacency, and constructor initialization. Account for index shifts
  caused by parameter cloning and previous edits. Verify that any scratch register
  is free at the insertion point.
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
- Record which targets were actually verified for the queue feature separately
  from the shared YouTube compatibility list.

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
   For `upstream_contribution_guide`, read the target branch's `CONTRIBUTING.md`.
   Cite the relevant sections and explain how they apply to the staged change,
   including extension code style when Java injection points change. For
   workflow-only changes, record why this review area is not applicable.
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
- `youtube/video/videoid/VideoIdPatch.kt`: shared hooks and prefetch warnings.
- `youtube/video/playerresponse/PlayerResponseMethodHookPatch.kt`: register handling
  and ordered callback injection in finalize.
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
