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
| Run an action against the current video card | [Read authoritative state at action time](#read-authoritative-state-at-action-time) |
| Determine which page owns a card | [Use the card's local page context](#use-the-cards-local-page-context) |
| Several native menu classes dispatch the same feature command | [Use the shared command router for tagged menu actions](#use-the-shared-command-router-for-tagged-menu-actions) |

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

## Read authoritative state at action time

`runThumbnailAction` reads the card video when the action runs, matching the
Watch later path, instead of keeping a separate `gestureVideoId` snapshot.

Use when the action is defined against the current host and its current data.
Preserve gesture ownership, movement cancellation, and downstream eligibility.
For actions bound to a particular pressed item or async operation, a snapshot
and identity check may be necessary; do not remove them as a blanket cleanup.

Source: [runThumbnailAction](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L1439).

## Use the card's local page context

`isOnPlaylistPage` walks the card's ancestors to the owning fragment and reads its
browse ID. A global last-opened page can describe the underlying playlist while
the player shows recommendation cards above it. The maintainer retained this
design and documented why the distinction matters.

Use when multiple page/view layers coexist. Verify the owner/tag/ID contract on
target APKs and preserve the specified behavior when context is unavailable.

Source: [isOnPlaylistPage](https://github.com/MorpheApp/morphe-patches/blob/e1928dd9502844e55811894c615dfd410c59e904/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/FeedVideoActionsPatch.java#L237).

## Use the shared command router for tagged menu actions

In PR #5088, the playlist toolbar still binds the existing Play next and
new Play last rows to their own playlist commands. The account-scoped router
now dispatches both row implementations through one hook. The tagged command
identifies the action and playlist. Ordinary commands fall through to YouTube,
and the Native queue setting is still checked before obtaining a queue action.

Reuse this pattern when verified native paths converge on the same dispatcher.
Trace the router context and all relevant callers for each supported target,
then retain narrow ownership and stock fallthrough. A one-way menu-bound flag
can skip work before initial binding. After that point, the command marker is
the ownership check and the shared hook may still parse unrelated commands.

[Toolbar hook](https://github.com/MorpheApp/morphe-patches/blob/2f8174e1eb0e50184e2fd87bdc3a1c512e5be98e/patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/QueueFlyoutHook.kt#L127-L165) · [Runtime dispatch](https://github.com/MorpheApp/morphe-patches/blob/2f8174e1eb0e50184e2fd87bdc3a1c512e5be98e/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueFlyoutActions.java#L172-L201)

## Record future patterns

Before recording a pattern, inspect the actual implementation and callers. Add
the task it solves, a stable commit/file/method link, when it applies, and the
assumptions that must hold. Distinguish existing design retained in a refactor
from behavior the maintainer introduced. Extend an existing entry when it covers
the same idea. Source review establishes a reusable example, not device coverage
for a future implementation; validate the new use independently.
