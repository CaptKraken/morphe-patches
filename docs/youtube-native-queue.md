# Experimental native YouTube queue

This patch adds **Native temporary queue (experimental)** for YouTube **21.20.405**.
It is opt-in and depends on the existing Add to queue patch. Other YouTube versions
are deliberately unsupported until their resolved native paths pass APK verification
and device testing.

## Install and use

1. Disable the preceding experimental source and import the replacement `.mpp` in
   Morphe Manager. Select all desired patches from the replacement source alone.
2. Patch an untouched YouTube 21.20.405 APK. An already patched APK or a selection
   mixing the official and experimental sources can apply shared setup twice.
   For non-root installation, include the usual GmsCore patches from the same source.
3. Enable **Override feed flyout queue button** in the app's queue settings if the
   native queue menu actions are missing. **Add feed flyout queue menu item**
   controls insertion of the additional actions.
4. Play a regular video outside an account playlist. In another video's menu,
   choose **Play next in queue** or **Play last in queue**. The current video stays
   in the queue and should continue playing without restarting.
5. Expand the playlist bar to see the native Queue panel. Tap rows, use the player
   Next/Previous buttons, or let playback advance automatically. Drag the native
   handles to reorder entries. Swipe toward the start edge (left in left-to-right
   layouts) and tap the native crossed-out-eye action to remove an entry. Its
   three-dot menu also offers Remove.
6. Use the native shuffle and repeat buttons. Repeat cycles off → all → one → off.
   Repeat one affects automatic advance; manual Next/Previous still advance.
   Shuffle changes playback navigation while keeping visible list order.
7. The selected row's native thumbs change the signed-in account's rating for
   that video. Tap the selected thumb again to remove the rating, or switch thumbs.
   YouTube handles sign-in, requests, rating events and failure rollback.
8. The header's three-dot menu beside repeat/shuffle contains **Save playlist**
   without Share. It opens YouTube's native name/privacy dialog. Confirm to create
   a new account playlist containing all queue videos in visible order, including
   the current and preceding entries. Shuffle does not change the saved order.
   Opening or cancelling the dialog does not create a playlist.
9. Use **Clear temporary queue** in a video menu to return to normal playback.
   Opening an account playlist or a video outside the queue also ends the queue.

Each video appears at most once. Adding a duplicate shows **Already in queue**
without moving it or changing playback. A removed video can be added again.
Removing the current entry chooses its successor, or the preceding entry if it was
last. Removing the final entry clears the queue. Modes reset when the queue ends.

Saving creates a separate copy. Later queue edits or clearing do not update or
delete the saved playlist. Each open creation dialog retains its original queue
snapshot. Saving again creates another playlist. Ordinary queue additions,
reordering and removal do not create or edit an account playlist.

The optional Save to Watch later/queue player button opens the same panel for its
queue action. Its Watch later action retains its existing meaning. The old
account-backed queue dialog and automatic queue synchronization are bypassed only
when this experimental patch is included.

## Titles and metadata

Titles come from the same native card that opened the menu, the native History
renderer, or a player response containing the video ID and title. Menu actions
capture an immutable ID/title pair. Card extraction scopes text to the matching
video, preserves punctuation and non-Latin text, and rejects ambiguous or
truncated-only data. Original-title replacements are consulted without waiting
for another request.

Missing titles use YouTube's public oEmbed endpoint. Transient failures retry after
one and four seconds, with at most three attempts. Native titles beat fallback
responses. A stale response cannot rename a removed and re-added entry. An ID
remains the label when no source supplies a title.

Channel, views and upload-age strings use the native playlist row fields. History
and player metadata are captured directly. Missing fields use the existing
unauthenticated localized video-ID lookup; only a renderer matching the exact ID
is accepted. Source precedence is independent per field. Localized strings and
live-viewer labels are preserved; missing values stay blank. Transient errors have
at most two retries, and stale entry responses are discarded. Both metadata caches
are bounded to 200 videos. Thumbnails use YouTube's image host.

Metadata updates retain entry identity, selection, repeat mode and shuffle mapping.
The native row retains YouTube's fonts, spacing and responsive metadata visibility.

Initial rating states are cached separately, for at most 200 videos. Observing a
changed native global account identity clears this cache and schedules a panel
refresh. Native account-scoped entity subscriptions and rating events remain in
charge of live state; no account identity or token is logged by the queue.

## Implementation and limits

Local state supplies playlist-panel protobuf data to the native panel and watch
navigator. The host's parser and extension registry decode it. Typed DEX bridges
are generated from resolved members; runtime reflection is not used. The Kotlin
patch separates its declaration, fingerprints, reference discovery, protobuf schema
reader and feature hook installation. Discovery uses stable strings/resources,
shared player/flyout fingerprints and native type/member relationships. Protobuf
fields are resolved by wire number through Lite schema metadata, including the
actual field names supplied by that APK. Production discovery does not pin
obfuscated class, method or field names.

An isolated native playlist store prevents autoplay recommendations from entering
the queue. The native shuffle navigator supplies random successor/predecessor
arrays and repeat behavior through the native playback backend. Shuffle mappings rebuild only after structural
edits or enabling shuffle. Modes and metadata updates do not reload playback.

Queue edits populate the detached store before attaching it and preserve the
playing endpoint. A thread-local edit scope suppresses the native stop/reopen
operation only during those edits. The preceding store, backend and cached watch
response fields are restored when ownership ends. Manual overlay controls use
the local navigator before their normal delegates and consume queue-boundary taps.

Owned row gestures are intercepted before the native server-edit dispatcher;
account-playlist rows retain their normal path. Stable local entry IDs reject stale
removal events. Native swipe and rating presenters retain their own styling,
listeners, entity/event subscriptions and recycling. Save uses a native creation
dialog endpoint with explicit snapshot video IDs and no synthetic source playlist.

The queue lives only for the app process. Starting it from an existing account
playlist is rejected with a toast. Shorts, Cast/TV queues, persistence across
process death and synchronizing later edits to a saved playlist are unsupported.

## Validation

Use JDK 21 and a configured Android SDK. From the repository root:

```sh
./gradlew :patches:test :patches:checkStringResources
./gradlew :patches:validateNativeQueue -PqueueApk=/absolute/path/youtube.apk
./gradlew :patches:buildAndroid
```

The validation task applies the actual patch and dependencies to the stock APK,
checks native hooks and ABI assumptions, rebuilds resources, and runs Android SDK
DEX and cross-DEX hierarchy verification. An incompatible APK or missing native
member aborts patching. This patch supports one version, so its minimum and maximum
supported version are both 21.20.405.

The distributable is `patches/build/libs/patches-1.47.0.mpp`. Run `buildAndroid` last
if another task rebuilt a JVM JAR at the same path; the Android bundle must contain
`classes.dex`. Add `-PqueueOutput=/absolute/path/queue-unsigned.apk` for an unsigned
APK intended for a separate signing/testing workflow. The validation task does not
add GmsCore support, change the package name, sign or install the APK.

Model tests cover queue ordering, uniqueness, selection and stable IDs, immutable
snapshots, stale metadata/removal events, per-source metadata precedence, bounded
caches, retry limits, native wire data, playback preservation, ratings/account
cache invalidation and save-dialog snapshots. APK checks cover native transport
registration, forwarding/delegates, storage attachment, mode updates, gestures,
metadata hooks, rating lifecycle, account callbacks and the Save dialog's
confirmation/authentication path. The resource checker validates all bundled locales.

Automated checks submit no account votes or playlist creation requests. A separate
attempt to run the APK's Android protobuf classes on the JVM stopped at an
optimized abstract class (`axaj`); it is not counted as passing validation.

## Device verification

The tester confirmed on a Samsung SM-S928U running Android 16 with YouTube
21.20.405 that the native panel, automatic and manual navigation, reordering,
swipe removal, unique entries, shuffle/repeat, titles, channel/statistics,
ratings/undo and Save playlist work. Save was confirmed after preview 12.

The review's account-cache invalidation change still needs a device retest.
Build verification does not establish live account behavior or prove every
interaction with other selected patches. Remaining targeted checks include:

- Switch accounts, sign out and enter/leave incognito with an existing queue.
  Verify initial thumbs and main-player state, including an in-flight vote.
- Cancel Save, save a named private playlist, and verify Library contents/order.
  Repeat after reordering, shuffle and removal; later queue edits must leave the
  saved copy intact. Test sign-in and interrupted-network errors.
- Exercise pause/resume, screen-off completion, rotation, single-entry queues,
  queue boundaries and clearing during pending metadata/gesture callbacks.
- Open a normal account playlist afterward and check its editing, modes, ratings
  and menu. Local reordering/removal must not send `browse/edit_playlist` requests.
- Check missing/live/unlisted and localized metadata, rapid menu changes,
  remove/re-add during requests, and the selected original-title/DeArrow settings.
- Retest with the user's full selected patch set, including loop, sleep-timer,
  player-control and layout options.
