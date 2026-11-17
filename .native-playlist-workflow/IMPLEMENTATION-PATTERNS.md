# Reusable implementation patterns

Reviewed 2026-11-17 against PR #4822 commit
[`e1928dd95`](https://github.com/MorpheApp/morphe-patches/commit/e1928dd9502844e55811894c615dfd410c59e904).
These are concrete examples to consult before adding similar code. Adapt the
contract to the current caller; a useful pattern does not justify a new generic
framework or an unrelated rewrite. The boolean naming rule in AGENTS.md is an
explicit user preference; the patterns below are observations from reviewed code.

## Find an example by task

| Task | Pattern / example |
| --- | --- |
| Walk a view tree without repeating lookups or allocating at each candidate | [Traversal-local inputs and scratch objects](#traversal-local-inputs-and-scratch-objects) |
| Hide several controls for the same reason | [Guard the owning group](#guard-the-owning-group) |
| Support distinct icons without boolean combinations | [Separate decisions, share the operation](#separate-decisions-share-the-operation) |
| Avoid duplicating a resource lookup or geometry contract | [Reuse stable values](#reuse-stable-values) |
| Run an action against the current video card | [Read authoritative state at action time](#read-authoritative-state-at-action-time) |
| Add a typed bridge to native code | [Use an explicit local register](#use-an-explicit-local-register) |
| Determine which page owns a card | [Use the card's local page context](#use-the-cards-local-page-context) |

## Traversal-local inputs and scratch objects

In `findSideCard`, read `getLearnedPattern(root)` once at the entry point, allocate
two `Rect` objects, and pass them through the recursive traversal. Candidates
reuse those objects instead of repeating the lookup and allocating more rectangles.

```java
return findSideCard(root, root, getLearnedPattern(root), new Rect(), new Rect());
```

Use when an input is invariant during one operation and temporary objects do not
escape it. Keep scratch objects local to that operation; shared static mutable
scratch can break concurrent or reentrant calls. Reuse across recursion is safe
only if the caller no longer needs the previous contents after the recursive call,
or recomputes them before use. Do not hoist a value that must change during a walk.

Source: [findSideCard entry and recursive overload](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L921).

## Guard the owning group

`ThumbnailButtonContainer.draw` checks inline playback and playlist/Mix-card
eligibility once before drawing either child button. This keeps the shared
visibility decision with the group rather than repeating it in each child.

Use when every child has the same condition. Preserve individual visibility and
touch/action guards; skipping drawing alone does not prove a control is untappable.
Keep required lifecycle cleanup effective when moving an early return.

Source: [container draw](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L408).

## Separate decisions, share the operation

Replace `getBadgeIcon(saved, queue)` with `getQueueIcon()` and
`getWatchLaterIcon(saved)`, sharing only `loadBadgeIcon(name)` for resource loading,
mutation, and tinting. This removes irrelevant combinations of boolean arguments
while keeping the real repeated operation in one place.

Use when callers choose distinct operations with different meaningful inputs.
Keep nullable resource handling and ownership rules for mutable cached drawables.
Do not extract a helper for every expression or combine unrelated operations.

Source: [icon loaders](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L516).

## Reuse stable values

Resolve `FRAGMENT_VIEW_TAG` once, rather than during every ancestor walk. Reuse
`FeedVideoCardsPatch.MIN_SIDE_THUMBNAIL_WIDTH_DP` instead of maintaining a second
120dp declaration for the same contract.

Cache only values stable for that lifetime and available at initialization. Do
not cache theme-, account-, or configuration-dependent data as though it were a
resource ID. Share a constant when its meaning is shared, not merely its number.

Sources: [fragment tag](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L93),
[thumbnail width](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/utils/FeedVideoCardsPatch.java#L959).

## Read authoritative state at action time

`runThumbnailAction` reads the card video when the action runs, matching the
Watch later path, instead of keeping a separate `gestureVideoId` snapshot.

Use when the action is defined against the current host and its current data.
Preserve gesture ownership, movement cancellation, and downstream eligibility.
For actions bound to a particular pressed item or async operation, a snapshot
and identity check may be necessary; do not remove them as a blanket cleanup.

Source: [runThumbnailAction](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L1439).

## Use an explicit local register

The no-argument instance browse-ID bridge uses register count 2: `v0` is the local
and `p0` remains the receiver. With count 1, `v0` and `p0` alias. The explicit local
keeps receiver lifetime and verifier types easier to reason about. The null path
loads a separate null constant and returns it instead of merging incompatible
endpoint and String types at one return.

Recalculate registers for each signature, including wide parameters; 2 is not a
general bridge register count. Inspect control flow and validate actual patched DEX.

Source: [browse-ID bridge](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/feedactions/FeedVideoActionsPatch.kt#L43).

## Use the card's local page context

`isOnPlaylistPage` walks the card's ancestors to the owning fragment and reads its
browse ID. A global last-opened page can describe the underlying playlist while
the player shows recommendation cards above it. The maintainer retained this
design and documented why the distinction matters.

Use when multiple page/view layers coexist. Verify the owner/tag/ID contract on
target APKs and preserve the specified behavior when context is unavailable.

Source: [isOnPlaylistPage](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L237).

## Record future patterns

Before recording a pattern, inspect the actual implementation and callers. Add
the task it solves, a stable commit/file/method link, when it applies, and the
assumptions that must hold. Distinguish existing design retained in a refactor
from behavior the maintainer introduced. Extend an existing entry when it covers
the same idea. Source review establishes a reusable example, not device coverage
for a future implementation; validate the new use independently.
