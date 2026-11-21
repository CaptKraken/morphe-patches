# Native playlist queue development

Use this local workflow in `/workspace/morphe-patches` and
`/workspace/morphe-patches-dev-integration`. Keep workflow notes, review tools,
test artifacts, and logs outside upstream patch diffs. Follow the user's latest
instructions and existing authorization.

## Scope and current work

- Work in the primary conversation without subagents or delegated stages. Read
  and update `/workspace/.native-playlist-workflow/STATE.md`. Confirm the actual
  worktree, branch, and local changes before resuming.
- Take one agreed feature or fix at a time. Before editing, state the intended
  behavior, affected native paths, examples, and validation. Keep the diff small
  and feature-specific. Preserve existing work and historical fallback branches.
- Use the agreed base, target versions, and test defaults for the current
  feature. Keep declared compatibility, patch selection defaults, runtime setting
  defaults, and spoofed client versions distinct. Report the versions actually
  verified for this change.
- Trace the native UI, request, authentication, playback, and lifecycle paths
  relevant to the change on the target APK. Reuse native behavior where possible.
  If custom coordination is needed, explain the missing native capability.
  Queue additions use the existing confirmation toast and preserve browsing.
- Keep source, builds, screenshots, and external publication within the user's
  authorization. Clean up redundant experiments after recording useful results.
  Check ownership before deleting. Preserve original APKs, current artifacts,
  reusable tools, and backups.

## Code and PR conventions

- Use `is...` for new boolean fields, locals, and parameters and `IS_...` for new
  boolean constants. Preserve required API names and existing unrelated names.
- Before implementation and review, consult the local `MAINTAINER-CONVENTIONS.md`
  and `IMPLEMENTATION-PATTERNS.md`, nearby maintained patches, and relevant shared
  APIs. Record useful new patterns with source links and limits.
- Keep patch-time discovery and bytecode edits in Kotlin and runtime behavior in
  extensions. Use existing UI, resource, localization, and settings utilities.
  Explain unusual native assumptions and injection contracts.
- Use conventional commits with a specific scope and behavior, such as
  `feat(YouTube - Native playlist queue): <behavior>`.
- For every Morphe Patches PR title or description, including drafts in chat,
  read and apply
  `/workspace/.native-playlist-workflow/skills/morphe-pr-writing/SKILL.md`.
  Preserve the current template and exact Test results checklist. Include the
  user's early AI-assistance disclosure unless they ask to omit it.

## Patch review

- Identify the source of truth for playback and queue ownership. Review
  initialization, disposal, stale callbacks, partial updates, and rollback where
  relevant. Distinguish the displayed panel, prefetched metadata, active
  playback, and navigator ownership.
- Inspect original methods before mutation. Verify each fingerprint's owner,
  operands, and control flow. Check the actual injection point, including
  registers, branch targets, constructor initialization, and callback order.
- Declare required shared extension and hook dependencies. Prefer shared hook
  registration or listener chaining. Intercept only events
  owned by the active queue and allowed by the setting. Preserve original
  callbacks and behavior for ordinary playback and account playlists.
- Trace direct and transitive dependencies and affected callers. For each actual
  overlap, record the shared path, expected combined behavior, and evidence.
  Verify both unselected and disabled behavior where relevant. Use existing
  patch-inclusion helpers when shared code must distinguish the two states.
  Resolve genuine conflicts before changing another patch's behavior.

## Validation and delivery

- Use focused behavior tests where they catch a realistic regression. Verify
  native discovery and injection on real APKs with fingerprint validation and
  appropriate DEX/resource checks. Test relevant patch combinations and any
  affected unselected path.
- Run the checks justified by the change, such as `:patches:test`,
  `:patches:checkStringResources`, and `:patches:buildAndroid`. For MPP delivery,
  follow the final-archive checklist in
  `/workspace/.native-playlist-workflow/WORKFLOW.md`.
- Report unit tests, APK checks, and actual app runs separately. Distinguish
  passes, failures, skips, and unrun checks. Use existing captures and logs.
  The user declined further log captures. Preserve that decision.
- For UI or playback changes, follow the local
  `/workspace/.native-playlist-workflow/RUNTIME-PROOF.md` protocol when available.
  On an authorized Android runtime, exercise the affected behavior and inspect
  screenshots or recordings. Tie evidence to the exact artifact, app and Android
  versions, settings, and steps. Distinguish tester reports from direct results.
  Without runtime evidence, record `not_run` when no app run took place and say
  **not visually verified**. Authorized test builds can be delivered with that
  limitation. Confirm the affected scenario before calling a device failure fixed.

## Mandatory pre-commit gate

1. Stage the intended files. Review the complete staged diff and file list and
   the cumulative feature diff against its base. Check that the diff contains
   only the agreed change and excludes generated files, scratch tools, secrets,
   and accidental dependency or release edits.
2. Review code quality, conventions and reuse, fingerprints, injection and control
   flow, patch interactions, versions and resources, and focused tests. For
   `upstream_contribution_guide`, read the target branch's `CONTRIBUTING.md` and
   cite how its relevant sections apply, including extension style for modified
   Java injection points. For workflow-only changes, record why this area is
   inapplicable. Resolve blocking findings before committing.
3. Run the applicable checks on the staged implementation and save actual command
   outcomes, failures, and limitations. List device checks still pending.
4. Generate a review record in the current worktree:

   ```sh
   hook_path=$(git rev-parse --git-path hooks/pre-commit)
   review_path=$(git rev-parse --git-path native-playlist-queue-review.json)
   "$hook_path" --template > "$review_path"
   ```

   Complete each area with evidence and `pass` or justified `not_applicable`.
   Record validation results and device status. A pending device check can be
   recorded when the code has passed review. Complete the review template.
5. Run `"$hook_path"`, then commit normally after it passes. Regenerate the
   record if HEAD, branch, or staged content changes. The hook checks the record
   against the exact staged tree. Keep the semantic review and evidence accurate.
   Never bypass the gate. If blocked, report the specific missing check or result.
