# Conventions learned from the maintainer's queue review

Recorded 2026-10-25 from PR #3953. Apply these patterns to future changes and
reviews, including when porting our tested fixes onto the maintainer's branch.
Keep this document outside the upstream patch diff.

These are observations from concrete edits, not a newly discovered upstream
style specification. Existing user instructions and correctness requirements
still apply. A file can be a good formatting example while containing a runtime
bug we have since fixed; do not copy its old queue behavior back into our code.

## Java: explicit blocks and readable control flow

In `df3d0e7e6`, the maintainer expanded single-statement branches throughout the
queue classes. Use braces for early returns, throws, continues, conditional
assignments, and `if` / `else if` chains, even when the body is one statement:

```java
if (index < 0 || index >= entries.size()) {
    throw new IndexOutOfBoundsException(index);
}
```

Follow the surrounding spacing and leave useful separation between guards,
iteration, and the resulting action. Do not compress several decisions onto
one line. This does not prohibit simple ternary expressions, records, or `var`;
the maintainer retained those constructs.

Evidence: [PlaylistQueue.withRepeatMode/select/move](https://github.com/MorpheApp/morphe-patches/blob/df3d0e7e60cc00a1787a73956ee1783379b47e01/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/PlaylistQueue.java#L60).

## Kotlin and Smali: expand calls that already need multiple lines

The formatting commit expands `addBridgeMethod` and instruction-injection calls
so their arguments, the triple-quoted Smali block, and the closing parenthesis
are clearly separated. Prefer this shape over opening the raw string at the
end of a long argument list and closing everything with `""")`:

```kotlin
header.addBridgeMethod(
    "patch_getNavigator",
    emptyList(),
    "Ljava/lang/Object;",
    1,
    """
        iget-object v0, p0, $navigator
        return-object v0
    """
)
```

Keep short ordinary calls concise where the maintained code does. Do not invent
a line-length rule or claim a formatter has checked the code: the project uses
Kotlin official style, and these edits provide the concrete local examples.

Evidence: [PlaylistRepeatHook bridge formatting](https://github.com/MorpheApp/morphe-patches/blob/df3d0e7e60cc00a1787a73956ee1783379b47e01/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/PlaylistRepeatHook.kt#L75).

## Refactoring: share a real decision or operation

The `e2e2c7c07` cleanup gives several useful examples:

- `AddToQueuePatch.isNativeQueueEnabled()` names the repeated queue-type test.
  Keep setting selection, patch inclusion, and active playback ownership distinct;
  a helper for one does not establish the others.
- `canQueueFlyoutVideo(videoId)` exposes eligibility directly. Menu preparation
  previously created a `Runnable` through `getFlyoutQueueAction(...)` merely to
  test whether the result was non-null. Use a predicate for a question and create
  an action only when one is needed.
- `isQueuePlayback(controller, playback)` shares the full ownership contract:
  accepted model identity, live backend identity, and local-queue ownership.
  Extract repeated conditions without dropping the checks that make them safe.
- `getMixTapSender(...)` shares Mix-command validation and sender lookup between
  the music-video transform and intentional Mix-tap path.
- `FeedVideoCardsPatch.findTappedRootHost(...)` shares the actual view traversal
  used by individual-video and playlist-card classification.

A helper should represent current repeated behavior or clarify an important
contract. Do not turn these examples into a reason to add generic frameworks,
wrappers around every expression, or infrastructure for future features.

Evidence: [eligibility predicate](https://github.com/MorpheApp/morphe-patches/blob/e2e2c7c078e677ff6c5fe61c9a076e88f1871bff/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch.java#L1440),
[shared card-root traversal](https://github.com/MorpheApp/morphe-patches/blob/e2e2c7c078e677ff6c5fe61c9a076e88f1871bff/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/utils/FeedVideoCardsPatch.java#L409).

## Avoid unnecessary work and remove obsolete bookkeeping

Put cheap applicability guards before parsing protobufs, traversing views, or
scheduling main-thread work. Preserve cleanup when moving a guard: the new title
guard skips work only if Native queue is disabled **and** no previously widened
title needs restoring. A blanket disabled-path return would miss that cleanup.

The maintainer removed the unused `FlyoutUtils.isNativeQueueRowFlyout` flag, its
assignment/reset, and its import after the old custom-menu path was gone. When a
feature replaces an implementation, inspect the remaining readers and writers
and remove bookkeeping that no longer has a consumer. Do not just stop calling
the old entry point and leave its state behind.

Name repeated protocol values with meaningful constants, as with
`SENDER_VIEW_KEY` and `MIX_PLAYLIST_ID_PREFIX`. This is not a requirement to make
a constant for every literal or ordinary test count.

## API contracts, nullability, and logging

Annotate genuinely nullable callback/command inputs and results with the existing
AndroidX `@Nullable` convention. The cleanup added it to the optional load-video
callback and queue menu/Mix entry points. An annotation documents the contract;
the appropriate guards are still required.

For caught unexpected failures, follow the existing
`Logger.printException(() -> "methodName failure", exception)` pattern used by
`onPlaylistTitleBound`, `shouldIgnorePlaylistResponse`, and `completeQueueMenu`.
Keep useful diagnostic context when needed. These are developer logs; do not
replace localized user-facing feedback with method names.

## Native discovery: use the patcher API deliberately

In `7f8a99ea4`, the maintainer removed widespread `matchSingle()` calls, stating
that uniqueness is already covered when patching with `--validate`.

- Use a fingerprint's direct `.originalMethod`, `.method`, or
  `.instructionMatches` access for straightforward lookups, as the maintained
  code now does.
- Use `.match()` when retaining a resolved match to access several members.
  Do not mechanically put `matchSingle()` on every fingerprint.
- If following the maintainer's validation workflow, check the pinned patcher API
  and actually enable the relevant validation. Do not assume an ordinary lookup
  or every programmatic APK harness automatically checks uniqueness.
- Keep semantic relationship checks. Uniqueness does not establish that two
  fields belong to the same owner, that a boolean is the repeat capability,
  that a native backend is active, or that an injection point is safe.
- When ambiguity is a concrete concern, narrow the fingerprint or use an explicit
  bounded/unique match appropriate to the case. Missing required capability must
  still fail clearly.

The title cleanup combined redundant creation/view fingerprints into one target
fingerprint carrying both layout and title anchors. Prefer one sufficient
discovery contract over several overlapping matches plus a check that they
resolved to the same method.

The accepted-model hook derives its position from the matched store plus
`numberOfParameterRegistersLogical` after `cloneParameters()`, rather than
scanning the whole method for that field store again. Reuse the verified helper
contract where applicable, while accounting for other mutations; this is not
permission to keep stale indexes or add unexplained absolute offsets.

The latest controls fingerprint changes immediate adjacency to
`MatchAfterWithin(1)` for a verified extra register-restoration instruction in
21.43+. Broaden a relationship only as far as actual APK evidence justifies;
do not weaken a fingerprint simply to make a new version patch successfully.

A specific `clearMatch()` was also removed. Do not infer a general ban on cache
invalidation: inspect execution order, intervening mutations, and the pinned API
before adding or removing one.

Evidence: [lookup examples](https://github.com/MorpheApp/morphe-patches/blob/7f8a99ea4b69438dea3c6af41cd6ce083d75819b/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/PlaylistRepeatHook.kt#L25),
[post-clone injection index](https://github.com/MorpheApp/morphe-patches/blob/e2e2c7c078e677ff6c5fe61c9a076e88f1871bff/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/PlaylistPanelHook.kt#L201),
[verified 21.43 distance adjustment](https://github.com/MorpheApp/morphe-patches/blob/7f8a99ea4b69438dea3c6af41cd6ce083d75819b/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/Fingerprints.kt#L834).

## Follow-up review: local flows and native entry points

Recorded 2026-10-26 from `66d9726f0`, after the original PR merged.

The maintainer inlined `authorizeRowSelection`, `refreshRepeatHeader`,
`activePlaylistController`, the one-call `clearFeedback` overload, and
`removalAction` into their callers. These helpers split a local operation across
methods without providing a reused decision or a separate contract. Prefer
keeping such short flows together when that makes the caller easier to follow.
This is an observed preference, not a ban on helpers with one caller: retain an
extraction when it isolates a substantial responsibility or explains a native
contract. Shared predicates such as `AddToQueuePatch.isNativeQueueEnabled()`
remain useful; removing the duplicate inverse predicate reinforces our existing
reuse guidance.

The same commit marks native callbacks with expanded Javadoc beginning
`Injection point.` and keeps timing/ownership explanations below it. Use that
pattern for new or touched injected entry points. A Java method can be called
only by injected DEX instructions; the absence of ordinary Java callers does not
make it dead code. Confirm patch-side references before removing a callback.

Evidence: [refactor 66d9726f0](https://github.com/MorpheApp/morphe-patches/commit/66d9726f085ff4281898459fa4e6f86919b4bf1a).

## Android API availability includes constructors and failure paths

In `c5f9f94ee`, the maintainer replaced `new IndexOutOfBoundsException(index)`
with `new IndexOutOfBoundsException("Index out of range: " + index)`. The SDK API
database lists the integer constructor as API 33, while the String constructor
is available from API 1. Code compiled or JVM-tested against a recent Java
runtime can therefore still fail when that error path executes on older Android.

Check platform availability for newly used Java/Android methods and constructor
overloads against the applicable minimum Android version. Do not assume Java
source compatibility or a successful JVM test establishes device compatibility,
and do not assume desugaring supplies an API without checking the configured
toolchain. Apply this review to exceptions and other infrequently reached paths,
as well as normal playback. Do not add a broad compatibility test framework for
this observation alone.

Evidence: [constructor fix c5f9f94ee](https://github.com/MorpheApp/morphe-patches/commit/c5f9f94ee71a480be00a432ee364f4e7099b9537).
Local SDK evidence: `platforms/android-*/data/api-versions.xml`, class
`java/lang/IndexOutOfBoundsException`, constructors `(I)V` and
`(Ljava/lang/String;)V`.

## Shared capture hooks can run during scrolling

In `5545e3c87`, `onElementLoaded` stops parsing card metadata while Native queue
is disabled and moves eligible parsing/cache updates to `Utils.runOnBackgroundThread`.
It clones the input bytes before scheduling and still returns the original bytes
unchanged. This extends the existing cheap-guard guidance: establish which thread
a shared hook runs on and how frequently it runs before putting protobuf parsing
or other substantial work there.

When background capture is appropriate, copy mutable borrowed input before the
handoff, use thread-safe cache access, and preserve the hook's original return
contract. Keep synchronous UI/ownership decisions on their required thread;
this commit is not a rule to move every hook into a worker. In this case,
metadata can also be fetched when a video is queued, so disabled capture can be
skipped without requiring the feed to collect it first.

Evidence: [scrolling fix 5545e3c87](https://github.com/MorpheApp/morphe-patches/commit/5545e3c87458e60c5e6ef5653053c02d343dd3f8).

## PR URLs in file headers

Observed while reviewing follow-up PR #4076: recent fix PRs use their own PR URL
for newly introduced files. For example, the new `CaptionsAfterSeekPatch.kt` and
Java extension in fix #3986 point to #3986; the new translated-caption helpers
in fix #3837 point to #3837. A new helper introduced by a follow-up fix should
therefore identify that follow-up PR, rather than copy the original feature's
PR URL merely because it lives in the same package.

Existing files are less uniform: fix #4064 keeps the original #2628 header on
`DisablePlaylistAutoplayPatch.kt`, while #3955 appends its URL alongside #1322
on `MediaNotificationControlsPatch.java`. Preserve existing provenance; do not
replace every touched file's original URL or claim that appending each fix PR
is a mandatory repository rule. Keep copyright and NOTICE wording intact.

Evidence: [new fix file #3986](https://github.com/MorpheApp/morphe-patches/blob/e20076530e0af6061da9029bfaa4ad985fdd2f50/patches/src/main/kotlin/app/morphe/patches/youtube/layout/captions/CaptionsAfterSeekPatch.kt#L1),
[existing header retained in #4064](https://github.com/MorpheApp/morphe-patches/blob/f4c1f84e13f0ed9b5982adfccbdbec1529ce0091/patches/src/main/kotlin/app/morphe/patches/youtube/layout/playlistautoplay/DisablePlaylistAutoplayPatch.kt#L1),
[additional PR URL in #3955](https://github.com/MorpheApp/morphe-patches/blob/2b07abb628fec3bf625a09ab7d019f915c4dfa23/extensions/shared-youtube/library/src/main/java/app/morphe/extension/shared/patches/MediaNotificationControlsPatch.java#L1).

## Apply this during future work

1. Read maintained examples and the current dependency API before editing.
2. Use these formatting patterns in new or touched code; avoid unrelated mass
   reformatting in a behavior fix.
3. Review duplicated predicates, needless allocations/work, and dead state as
   part of finishing the feature, not only after the maintainer points them out.
4. Review guards, ownership, nullability, and injection contracts independently
   of formatting. A refactor can change behavior even when its title says cleanup.
5. Reconcile maintainer refactors with our tested fixes, then validate the actual
   combined source. Style alignment does not justify reverting a working fix.

Source commits: [cleanup e2e2c7c07](https://github.com/MorpheApp/morphe-patches/commit/e2e2c7c078e677ff6c5fe61c9a076e88f1871bff),
[formatting df3d0e7e6](https://github.com/MorpheApp/morphe-patches/commit/df3d0e7e60cc00a1787a73956ee1783379b47e01),
[matching/validation 7f8a99ea4](https://github.com/MorpheApp/morphe-patches/commit/7f8a99ea4b69438dea3c6af41cd6ce083d75819b).


## Explicit upstream contribution guide (2026-11-03)

Upstream added CONTRIBUTING.md in f77759c21. Unlike the observations above, these
are now explicit repository guidelines. Read the current file before preparing a
new PR or rebasing onto a newer base:
https://github.com/MorpheApp/morphe-patches/blob/f77759c21/CONTRIBUTING.md

Relevant rules: disabled patches should do no UI/background/hot-hook work; use
scoped Fingerprint/instruction filters rather than hand-scanning instructions or
custom lambdas; use classFingerprint for sibling discovery, known strings where
available, and justified version branches for actual shape differences. Required
fingerprints should not fail silently. Use getMethodCalled/getFieldAccessed and
project register/label helpers. Retain shared fingerprint invalidation when needed.
Use typed patch_ bridges rather than reflection, reuse real shared operations, and
keep comments ASCII except quoted literals. Keep settings/nullability/SDK behavior
accurate. No unrelated formatting or rewriting merely to retrofit every old file.

Testing explicitly names Morphe Desktop CLI --validate for latest non-experimental
and latest experimental targets, with minimum/maximum recommended app testing.
Our programmatic Patcher(validate=true)+SDK harness is useful evidence, but must
not be represented as a Desktop CLI run. Record unavailable APKs and device-only
checks honestly. Avoid asserting new-guide compliance solely from earlier builds.

The guide treats tool/assistant output as the contributor's own responsibility and
requires attribution of outside code/resources. It does not add a new approval
workflow or require posting an AI notice; the user's separate preference for an
early informational AI-assistance notice in PR descriptions remains applicable.


## PR #4404: shared discovery and failure cleanup (2026-11-09)

Observed in maintainer commit ec90bb620. These are concrete refactoring examples,
not new upstream requirements or reasons to rewrite unrelated tested code.

- **Separate discovery from installing a patch.** `resolveNativeAccountContract()`
  returns the account interface/getter contract. Both the queue and Series tracker
  consume it, while `wirePrivacy()` remains responsible for installing tracker
  hooks. Reuse a real shared resolver without selecting another feature or copying
  its discovery code. Recheck dependencies and the feature-unselected path.
- **Prefer an already established native relationship.** `installPlaylistPages()`
  obtains the exact response type and panel field from `NativeQueueReceiveFingerprint`
  and locates the page service with that return type. It replaces a custom predicate
  that searched candidate return classes for a field of the panel type. Likewise,
  the response navigation field comes from the existing scoped fingerprint.
  Known types/references make the match more direct; they do not remove the need
  for semantic checks, mutation ordering, or real-APK uniqueness validation.
- **Insert at control-flow labels, not just before an instruction index.**
  Constructor attachment uses `findInstructionIndicesReversedOrThrow(RETURN_VOID)`
  and `addInstructionsAtControlFlowLabel`. Cover every return and ensure branches
  targeting it also execute the injected callback. A physically earlier instruction
  can be skipped by a branch to the original return. Retain constructor initialization
  and register-safety checks; this is not permission to hook arbitrary entry points.
- **Give different native paths clear extension entry points.** Modern playlist
  menu clicks get `replacePlaylistItemClick`, while the legacy `replaceOnItemClick`
  retains its older video-menu role. Avoid hiding a modern protobuf path inside a
  handler documented for another version. Apply verified version guards to related
  lifecycle injections too; the native background hook is only installed for 21.15+.
- **An early return must complete the failed operation's state.** Missing playlist
  service/source now clears deferred `playlistNavigation` and reports the existing
  localized fetch failure. An empty/null result is not enough if a native Next/
  Previous intent is still waiting. Check pending tokens, loading state, continuation,
  and ownership for each concrete operation rather than clearing every field blindly.
- **Protect the whole injected source-reading boundary while retaining useful
  fallbacks.** `playlistSource` catches failures from eligibility, view classification,
  byte extraction and parsing, not only the mounted-command lookup. The inner mounted
  lookup still logs and permits the existing element fallback; non-Element parsing
  remains a specific debug-logged case. Do not generalize this into suppressing all
  exceptions: required patch discovery must still fail clearly, and runtime errors
  need appropriate logging and an explicit safe result.
- **Share actual repeated decisions and name their units.** Reuse `isMixStart` and
  the `RD` prefix instead of copying the validation expression. The timeout constant
  names seconds without changing the existing 15-second policy. Move state declarations
  before listeners using them rather than qualifying the class name merely to work
  around forward references. These edits do not justify unrelated formatting changes.

Evidence: [maintainer refactor ec90bb620](https://github.com/MorpheApp/morphe-patches/commit/ec90bb620636854a97eb9e10ce8949b6bdae24df).
Local validation of that exact commit: 467 tests passed, 5 optional APK tests skipped;
Android build and Desktop CLI `--validate` + SDK verification passed on 21.30.216
and 21.44.528 with related patches including Series tracker. User subsequently
confirmed the refactored 21.30.216 build works. Evidence is retained in
`/workspace/scratch/native-queue-refactor-ec90bb620/`. A passing JVM suite by itself
would not have established the injected control-flow or device behavior.
