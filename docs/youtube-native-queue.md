# Experimental native YouTube queue

Select **Native temporary queue (experimental)** when patching YouTube **21.20.405**.
The patch is off by default and includes the existing Add to queue dependencies.
This is a prototype undergoing Android device testing.

Device testing confirmed that the native temporary playlist appears and advances
automatically when the current video ends. The player Next button still opened a
recommendation outside the two-video queue. Investigation found that preview 3's
ownership test rejected the composite controls used by the actual overlay factory.
An alternate Next implementation could also bypass the patched base method.
The preview 4 device retest confirmed player Next/Previous and drag reordering.

Preview 4 captures handlers at the overlay's actual registration point, so a
composite controls wrapper is supported. It tracks live overlay handlers weakly
and intercepts both the base and alternate Next implementation, plus Previous.
If local navigation is unavailable, a registered queue control consumes the click
instead of falling back to a recommendation.

Preview 7 supplies YouTube's icon-only swipe-button renderer in each row's action
data, with the native crossed-out-eye icon (`VISIBILITY_OFF`). Omitting visible
text selects the host's compact layout and spacing. Preview 6 used the same native
presenter but incorrectly supplied a trash icon and visible text, producing the
wide button seen in the device comparison. The host creates, styles, binds, and
recycles the button. Swipe toward the start edge (left in left-to-right layouts),
then tap the crossed-out-eye action. The native HideEnclosingCommand emits the
tagged row to our existing local removal handler, without a server playlist-edit
endpoint. The Watch Later full-swipe behavior is a different reference and is not
enabled for this queue.

The preview 7 device retest confirmed the Mix-style swipe action. Preview 8 makes
the queue unique by video ID. Both add actions reject a video already anywhere in
the queue, including the playing video, without moving it or changing playback.
The app shows **Already in queue**. A video can be added again after removal or
clearing the queue. Starting a queue with the playing video creates just one entry.

Preview 8 also connects the existing native shuffle and repeat buttons to the
isolated local playlist navigator. Repeat cycles **off → repeat all → repeat one
→ off** using YouTube's native drawable states. Repeat one affects automatic
advance; manual Next/Previous still select another entry. Shuffle randomizes
playback navigation while keeping the visible list order and current video.
Its native successor/predecessor mapping remains stable through title updates
and video changes, and is rebuilt after additions, removals, or drag reordering.
Toggling shuffle off restores navigation in visible list order. Modes reset when
the queue ends. The preview 8 device retest confirmed that update works.

Queue edits populate the detached native store before attaching it, preserve the
playing video's existing endpoint, and suppress native stop/reopen dispatch only
on the editing thread. Normal user navigation and autoplay run outside that scope.
The original resume-crash stack was absent from exported logs; pause/resume still
needs device testing.

Preview 9 captures native titles instead of relying on a metadata request for
every entry. Menu actions retain an immutable video ID/title pair, and the playing
video is seeded from its native player response. Captured titles take priority over
network responses. The preview 9 device retest confirmed native title capture.

Preview 10 adds the channel name and a second metadata line containing YouTube's
formatted view count and time since upload, using the native playlist row fields.
History metadata and the native player channel are captured directly. Missing
fields load asynchronously from the existing unauthenticated localized video-ID
lookup, accepting only a video renderer whose ID exactly matches the queue entry.
The row keeps YouTube's fonts, colors, spacing and responsive metadata visibility.
The preview 10 device retest confirmed this metadata update.

Preview 11 supplies real native Like/Dislike toggle renderers for the selected row,
replacing the host's disabled placeholders. Tapping a thumb sends YouTube's native
video rating command; tapping the selected thumb again removes that rating. These
actions change the signed-in account's rating for that video. They use the host's
normal account check, sign-in flow, authenticated request, error handling and
rollback. They do not create or edit an account playlist. Native entity and event
subscriptions update the row icons, including changes made from the main player.
A bounded cache of observed native ratings initializes refreshed rows. The native
presenter still owns clicks, selected icons, accessibility and recycling. This
rating update needs a device retest; build verification cannot exercise a live
account or prove state synchronization on the phone.

Import the replacement bundle as **Morphe Patches (queue preview 11)** and disable
the preceding preview source. Select all desired YouTube patches from the new
source alone. Starting from an untouched APK avoids applying shared setup twice.

The queue lives in memory for the app process. It uses YouTube's native playlist
panel, collapsed playlist bar, selected-row indicator, and playback navigator.
Adding and rearranging videos does not create or edit a playlist in your account.
There is no save-to-account operation in this prototype.

## Using it

1. Open a regular video outside an existing playlist.
2. In another video's menu, choose **Play next in queue** or **Play last in queue**.
   The currently playing video remains in the list and playback should continue.
3. Expand the playlist bar in the watch screen to see the queue. Drag the native
   handles to change its order. Swipe a row to reveal the crossed-out-eye action,
   or use its three-dot menu to remove that entry. Removing an upcoming entry keeps
   the current video playing; removing the current entry selects its successor (or the preceding
   entry if it was last). Removing the final entry clears the queue.
4. Tap the native repeat button to cycle modes, or toggle shuffle. Use the selected
   row to follow shuffled playback; switching modes should not restart the video.
5. Use the selected row's thumbs to like or dislike its video. Tap the selected
   thumb again to remove that rating; switching thumbs replaces the prior rating.
6. Use **Clear temporary queue** in a video menu to return to normal playback.
   Opening an account playlist or a video outside the queue also ends the queue.

If the queue actions are missing, enable **Override feed flyout queue button**
in the Morphe queue settings. The device tester needed this setting enabled.
**Add feed flyout queue menu item** controls insertion of the additional actions.

If the optional Save to Watch later/queue player button is installed, its queue
action opens the same native panel. Its Watch later action retains its existing
meaning. The old account-backed queue dialog and automatic queue synchronization
are bypassed when this experimental patch is selected.

Each video appears at most once. Stable entry IDs keep row actions attached to the
correct entry across reordering and protect a re-added video from stale removal
events.
Titles are read from the same video card that opened the menu, from the native
History renderer, or from a player response containing both the video ID and title.
Card extraction uses the full title field associated with the accessibility label,
scoped to that video's component; it does not split the label on commas or hyphens.
The source data can contain multiple video cards, so ambiguous or truncated-only
titles fall back to metadata rather than borrowing a nearby video's title.
A bounded process-local cache retains up to 200 titles. Existing original-title
replacements are consulted without starting or waiting for another request.

When no native title is available, YouTube's public oEmbed endpoint supplies the
fallback without account credentials. Transient failures retry after one and four
seconds (at most three attempts); permanent access/not-found errors do not retry.
A native title cancels remaining retries. Responses are scoped to the stable queue
entry, so an old request cannot rename a removed/re-added video. Late network
results cannot overwrite native card or player titles. Title updates retain entry
identity, selection, repeat state and the shuffle mapping. A video ID remains as
the label if neither source provides a title. Thumbnail images use YouTube's image
host.

Channel, views and upload-age strings have independent source precedence: a
fallback can fill missing fields without replacing a native value. Existing
localized strings (including live-viewer labels) are preserved; the patch does not
convert view counts or guess dates. Missing fields remain blank. The lookup uses
the same mobile-web client as original-title support and sends only the video ID
and locale, without account authentication. Transient errors retry at most twice;
a valid response without the video is not retried. Late responses for removed
entries are discarded before updating the cache or row. No account playlist is
created or edited by this lookup.

Starting the queue while already playing an account playlist is currently rejected
with a toast. Shorts, Cast/TV queues, persistence across process death, and saving
the queue to a real playlist are outside the current implementation.

## Validation

Use JDK 21 and a configured Android SDK. From the repository root:

```sh
./gradlew :patches:test
./gradlew :patches:validateNativeQueue -PqueueApk=/absolute/path/youtube.apk
./gradlew :patches:buildAndroid
```

`validateNativeQueue` applies the actual patch and dependencies to a supplied APK,
rebuilds its resources, and invokes the Android SDK DEX and cross-DEX hierarchy
verifiers. An incompatible version or missing native member aborts patching.
The native ABI and protobuf field numbers are intentionally pinned to the supplied
21.20.405 APK; additional versions require further discovery and validation.

The patch bundle is `patches/build/libs/patches-1.47.0.mpp`. Run `buildAndroid` last
if another task has rebuilt the JAR at that same path; the distributable bundle
must contain `classes.dex`.

For an unsigned APK intended for a separate signing/testing workflow, add
`-PqueueOutput=/absolute/path/queue-unsigned.apk` to the validation command.
This task does not add GmsCore support, change the package name, sign the APK, or
install it. For normal non-root use, select the experimental patch alongside the
usual YouTube/GmsCore patches in Morphe Manager.

Automated JVM tests cover insertion order, duplicate rejection in both add modes,
selection through moves/removals, immutable snapshots, late metadata, and native
panel/endpoint wire data, including preservation of the playing endpoint. Additional
tests cover nested edit scopes, exception cleanup, and isolation from playback on
other threads. APK regression checks verify that the playback-dispatch guard runs
before native side effects and that a new store is populated before attachment.
They also check that manual overlay controls consult the local queue before their
original delegates, including the alternate Next implementation, and that ownership
is captured where the composite controller registers with the actual overlay.
Navigation must commit a queue descriptor before loading it.
Additional checks cover the native swipe presenter lifecycle, renderer/command tags,
and the host's tagged-row removal event. Model regressions cover the icon-only
native hide action with a localized accessibility label and removing/re-adding a
video after reordering while preserving playback and rejecting stale removal events.
Playback-mode tests cover preserving modes across edits, resetting modes at the end
of a queue, and invalidating shuffle mappings only for structural changes. Real-APK
checks also require the native random navigator, native button listeners and state
rendering, and rebuilding random mappings before notifying availability observers.
Title regressions cover punctuation and Khmer text, localized/truncated labels,
multi-video card isolation, immutable menu snapshots, bounded caching, source
precedence, removed-entry responses, and transient versus permanent retry limits.
APK checks require the paired player-response hook on 21.20.405, the menu hooks
surviving extension shrinking, and the native History renderer matching the same
video-ID interface.
Additional metadata regressions cover exact-ID renderer selection, localized text,
live/missing fields, per-field precedence, immutable snapshots, stale-entry
responses and the native channel/video-info protobuf fields (2 and 31). APK checks
require the native History metadata fields and the existing row TextViews to
remain bound by YouTube's renderer.
Rating regressions cover video-bound native default/undo endpoints, all initial
rating states, native icon IDs, localized accessibility, bounded caching and
preservation of the live playback endpoint. APK checks require model initialization
before subscribing, native click dispatch, entity subscriptions and recycling,
cache hooks surviving shrinking, and the native account/sign-in and rollback paths.
No real account rating is submitted during these automated checks.
APK verification establishes that the patched DEX and
resources can be built; it does not establish that YouTube renders the UI or plays
the queue correctly on an Android device.

## Device checks still required

- On a signed-in account, like, undo like, dislike, undo dislike, and switch
  directly between thumbs. Check the main player's rating agrees, then change it
  from the main player and reopen the queue. Repeat after metadata updates,
  reordering and navigation; every vote must affect only the selected video.
  Confirm existing ratings initialize correctly. Disconnect the network during
  a vote and verify the host's error/rollback behavior. Check signed-out behavior
  follows YouTube's sign-in flow and retest after switching accounts. Open a normal
  account playlist afterward and verify its rating controls retain native behavior.
- Add videos from several channels and verify the channel line and
  `views • upload age` line match YouTube's source cards. Allow a moment for missing
  metadata to load. Include History, the initial playing entry, localized text,
  live videos and unavailable/unlisted items. Unknown fields should stay blank.
  Remove/re-add while details load; the old response must be ignored. Retest
  reorder, shuffle and repeat after metadata appears.
- Add videos from Home, search, related videos, subscriptions and History. Check
  full titles (including Khmer and long titles) and the initial playing entry.
  Open different menus quickly; each action must retain the correct ID/title.
  Repeat with original-title and DeArrow settings, and after scrolling cards out
  of view and back. Ambiguous layouts should use the fallback, not a wrong title.
- Simulate an interrupted connection while adding a video without a native title.
  Check fallback retries and that a later native title wins. Remove/re-add while
  a request is pending and verify that the old response is ignored.

- Add next/last while playing; confirm the stream does not restart and the native
  collapsed bar and expanded list update immediately.
- Tap rows, use next/previous, and let videos end with the screen on and off;
  confirm the selected row and playback follow the same order.
- Try adding the playing, previous, and upcoming videos again using both add actions;
  confirm there is still one entry per video and playback/order do not change.
  Remove a video and add it again. Drag entries before and after the current row
  and confirm next/previous use the new order.
- Toggle shuffle without interrupting playback. Follow Next/Previous and automatic
  advance, then toggle it off and check visible list order. Add, remove and reorder
  while shuffled, and check that navigation stays inside the queue. Update a title
  or change the current video and check that the random mapping remains stable.
- Cycle repeat off/all/one. Verify wrapping at either boundary for repeat all,
  replaying the same video on completion for repeat one, and manual Next/Previous
  advancing normally with repeat one. Check a single-entry queue, and combined
  repeat/shuffle. Clear and start a queue; both modes should reset.
- Remove upcoming/current/final entries, clear the queue, rotate the screen, and
  reopen the player. Confirm there are no stale entries or duplicate advances.
- Open and edit a normal account playlist afterward. Check that normal editing
  still works and that local reordering/removal sends no `browse/edit_playlist`
  requests. Check any selected loop and sleep-timer patches together with this one.

## Implementation notes

Local state supplies playlist-panel protobuf data to both the native panel model
and watch navigator. The host's own protobuf parser and extension registry decode
the data; host access is installed as typed DEX bridges, without runtime reflection.
An isolated native playlist backing store prevents normal autoplay candidates from
being mixed into the local list. The native `nig` navigator supplies repeat handling
and random successor/predecessor arrays, wrapped in the existing `aokc` playback
navigator. Only owned local headers enable their existing native click listeners;
normal account-playlist controls retain their original path. Local mode requests
update the native backend inside the playback-preservation scope, without loading
a video or republishing the playlist. The preceding store and navigator are restored
when the session ends. Cached watch responses are restored before being released.

Local row gestures are intercepted before the native server-edit dispatcher.
Account playlist rows retain their native path. No synthetic playlist ID is put in
watch endpoints; local entry IDs distinguish removed entries from later re-additions.
These endpoint and lifecycle assumptions still require the device checks above.
