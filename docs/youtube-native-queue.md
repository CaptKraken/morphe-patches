# Experimental native YouTube queue

Select **Native temporary queue (experimental)** when patching YouTube **21.20.405**.
The patch is off by default and includes the existing Add to queue dependencies.
This is a prototype awaiting Android device testing, not a verified release feature.

Preview 2 addresses the first device report: adopting the queue stopped the playing
video, left its row labeled with an ID, and was followed by a crash on resume. The
native store switch explicitly invokes a forced stop/reopen, so this build suppresses
that playback dispatcher only inside the current thread's local queue edits. It
populates the detached store before attaching it and preserves the playing video's
existing endpoint. Normal user navigation and autoplay run outside that scope.
The reported crash itself was not present in the exported logs; the device retest
must verify both uninterrupted playback and successful pause/resume.

Import the replacement bundle as **Morphe Patches (queue preview 2)** and disable
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
   handles to change its order; use a row's three-dot menu to remove an entry.
4. Use **Clear temporary queue** in a video menu to return to normal playback.
   Opening an account playlist or a video outside the queue also ends the queue.

If the optional Save to Watch later/queue player button is installed, its queue
action opens the same native panel. Its Watch later action retains its existing
meaning. The old account-backed queue dialog and automatic queue synchronization
are bypassed when this experimental patch is selected.

Duplicates have distinct entry IDs and can be reordered or removed individually.
Titles for both the initial playing video and added videos load asynchronously
from YouTube's public oEmbed endpoint without account
credentials. A video ID remains as the label if metadata is unavailable. Thumbnail
images use YouTube's image host.

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

Automated JVM tests cover insertion order, stable identities for duplicates,
selection through moves/removals, immutable snapshots, late metadata, and native
panel/endpoint wire data, including preservation of the playing endpoint. Additional
tests cover nested edit scopes, exception cleanup, and isolation from playback on
other threads. APK regression checks verify that the playback-dispatch guard runs
before native side effects and that a new store is populated before attachment.
APK verification establishes that the patched DEX and
resources can be built; it does not establish that YouTube renders the UI or plays
the queue correctly on an Android device.

## Device checks still required

- Add next/last while playing; confirm the stream does not restart and the native
  collapsed bar and expanded list update immediately.
- Tap rows, use next/previous, and let videos end with the screen on and off;
  confirm the selected row and playback follow the same order.
- Enqueue the same video twice and select each copy. Drag entries before and after
  the current row and confirm next/previous use the new order.
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
being mixed into the local list. The preceding store and navigator are restored
when the session ends. Cached watch responses are restored before being released.

Local row gestures are intercepted before the native server-edit dispatcher.
Account playlist rows retain their native path. No synthetic playlist ID is put in
watch endpoints; local entry IDs identify repeated videos. These endpoint and
lifecycle assumptions still require the device checks above.
