# Conventions learned from the maintainer's queue review

Recorded 2026-10-25 from PR #3953. Apply these patterns to future changes and
reviews, including when porting our tested fixes onto the maintainer's branch.
Keep this document outside the upstream patch diff.

## User rule: boolean variable names

Recorded 2026-11-17: use `is...` for new boolean fields, local variables, and
parameters, such as `isQueue`, `isPressed`, and `isEnabled`. Use `IS_...` for new
boolean constants. Preserve framework/external API names and avoid unrelated
mass renames of existing declarations or shared settings. Boolean predicate
methods may still use meaningful existing `is...`, `has...`, or `can...` APIs.

This is the user's explicit convention, not a claim that upstream requires it.
The maintainer changed `queueButton` to `isQueue` in PR #4822 while retaining
other names such as `pressed`; the user chose to adopt a stricter variable rule.

For reusable implementation examples, consult [IMPLEMENTATION-PATTERNS.md](IMPLEMENTATION-PATTERNS.md).
It records the source, the reason to reuse a pattern, and its safety limits.

## Java: explicit blocks and readable control flow

In `df3d0e7e6`, the maintainer expanded single-statement branches throughout the
queue classes. Use braces for early returns, throws, continues, conditional
assignments, and `if` / `else if` chains, even when the body is one statement:

```java
if (index < 0 || index >= entries.size()) {
    throw new IndexOutOfBoundsException("Index out of range: " + index);
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

## Queue eligibility and playback ownership

- `AddToQueuePatch.isNativeQueueEnabled()` names the repeated queue-type test.
  Keep setting selection, patch inclusion, and active playback ownership distinct;
  a helper for one does not establish the others.
- `canQueueFlyoutVideo(videoId)` exposes eligibility directly. Menu preparation
  previously created a `Runnable` through `getFlyoutQueueAction(...)` merely to
  test whether the result was non-null. Use a predicate for a question and create
  an action only when one is needed.
- `isQueuePlayback(controller, playback)` shares the full ownership contract:
  accepted model identity, live backend identity, and local-queue ownership.
  Check all three when deciding whether the local queue owns playback.

[Queue eligibility and ownership refactor](https://github.com/MorpheApp/morphe-patches/commit/e2e2c7c078e677ff6c5fe61c9a076e88f1871bff)

## Restore the title when Native queue is disabled

The title hook still restores a previously widened title when Native queue
is disabled. The guard checks both queue selection and whether the view
still needs cleanup.

## Document injected entry points

Recorded 2026-10-26 from `66d9726f0`, after the original PR merged.

The refactor marks native callbacks with expanded Javadoc beginning
`Injection point.` and keeps timing/ownership explanations below it. Use that
pattern for new or touched injected entry points. A Java method can be called
only by injected DEX instructions; the absence of ordinary Java callers does not
make it dead code. Confirm patch-side references before removing a callback.

Evidence: [refactor 66d9726f0](https://github.com/MorpheApp/morphe-patches/commit/66d9726f085ff4281898459fa4e6f86919b4bf1a).

## Shared capture hooks can run during scrolling

In `5545e3c87`, `onElementLoaded` clones the input bytes before scheduling
metadata parsing and cache updates on a worker. It returns the original
bytes to its caller.

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

## Reporting validation results

Record programmatic `Patcher(validate=true)` and Android SDK checks under the
actual harness name. Record Desktop CLI validation and phone testing separately.

## PR #4404: shared discovery and failure cleanup (2026-11-09)

Observed in maintainer commit ec90bb620. These are concrete refactoring examples,
not new upstream requirements or reasons to rewrite unrelated tested code.

- **Separate discovery from installing a patch.** `resolveNativeAccountContract()`
  returns the account interface/getter contract. Both the queue and Series tracker
  consume it, while `wirePrivacy()` remains responsible for installing tracker
  hooks. Reuse a real shared resolver without selecting another feature or copying
  its discovery code. Recheck dependencies and the feature-unselected path.
- **An early return must complete the failed operation's state.** Missing playlist
  service/source now clears deferred `playlistNavigation` and reports the existing
  localized fetch failure. An empty/null result is not enough if a native Next/
  Previous intent is still waiting. Check pending tokens, loading state, continuation,
  and ownership for each concrete operation rather than clearing every field blindly.

Evidence: [maintainer refactor ec90bb620](https://github.com/MorpheApp/morphe-patches/commit/ec90bb620636854a97eb9e10ce8949b6bdae24df).
Local validation of that exact commit: 467 tests passed, 5 optional APK tests skipped;
Android build and Desktop CLI `--validate` + SDK verification passed on 21.30.216
and 21.44.528 with related patches including Series tracker. User subsequently
confirmed the refactored 21.30.216 build works. Evidence is retained in
`/workspace/scratch/native-queue-refactor-ec90bb620/`. A passing JVM suite by itself
would not have established the injected control-flow or device behavior.
