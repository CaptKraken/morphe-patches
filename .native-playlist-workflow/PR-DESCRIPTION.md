### Description

Adds a `Native playlist queue` patch that supplies a temporary video queue to YouTube’s existing playlist interface and playback engine. Queued videos appear in the playlist panel and fullscreen playlist view, and use the player and notification Next/Previous controls, including during background playback.

Supported actions:

- Play next and Play last from video menus. Play next can create a queue without an open player, keeping playback in the miniplayer so browsing can continue.
- Reorder, swipe to remove, clear the queue, and Undo removal or clearing without restarting current playback.
- Shuffle as a command that visibly reorders the rows, matching YouTube Android’s playlist behavior; repeat uses the native controls.
- Native Save, Download and Share for individual rows, and save the whole queue to an existing or new playlist with confirmation and Undo.
- Opening another regular video while a queue is active inserts and selects it after the playing entry. Visiting Shorts preserves the queue and playback position.

Patch-time Kotlin discovers native methods and adds typed bridges. The Java extension supplies local rows, maintains the in-memory snapshot, and intercepts operations owned by that queue. YouTube handles playlist rendering, sequencing, selection, and the account-facing Save/Share/Download flows. Ordinary playback and account-playlist actions retain their native paths.

Shared changes support queue menu insertion, minimized startup, watch-intent context, and metadata capture. The watch-intent hook preserves the existing observers; fullscreen startup suppression applies to an explicit queue-start request. Title-marker normalization also benefits the shared title helper’s existing callers.

Related to #3770. This is a separate alternative to the existing Morphe queue, rather than a replacement or a fix to that implementation.

### Screenshots

| Play next from a video menu | Queue playlist panel |
| --- | --- |
| <img src="https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/screenshots/native-playlist-queue/play-next-menu.jpg" alt="Play next in native queue in a video menu" width="260"> | <img src="https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/screenshots/native-playlist-queue/playlist-panel.jpg" alt="Native queue playlist panel" width="260"> |

| Save the whole queue | Swipe to remove a video |
| --- | --- |
| <img src="https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/screenshots/native-playlist-queue/save-queue.jpg" alt="Save the queue to a playlist" width="260"> | <img src="https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/screenshots/native-playlist-queue/swipe-to-remove.jpg" alt="Swipe a queue video to remove it" width="260"> |

Fullscreen playlist:

<img src="https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/screenshots/native-playlist-queue/fullscreen-playlist.jpg" alt="Native queue in the fullscreen playlist view" width="800">

### Additional context

- Supports **20.51.39**, **21.16.256**, and **21.20.405**. Uses Patcher/patches-library 2.0. No support is declared for 20.21.37 or newer experimental YouTube versions.
- If Morphe’s existing queue patch is installed and its **Premium-style queue** setting is enabled, that queue takes priority. Disable that setting to use Native playlist queue. Existing saved queue items are preserved.
- The patch is marked experimental and currently selected by default. Queue state does not persist after restarting the app. Undo after clearing a queue is an intentional addition.
- Custom labels use the normal resource/localization mechanism; new strings currently have English source text. No Premium server entitlement is unlocked.

### Test results

- [x] Tested on both the **minimum** and **maximum** supported versions
- [ ] Tested on **experimental** supported versions (Optional)

The author reported successful phone testing on **20.51.39**, **21.16.256**, and **21.20.405** during development, and reported the Patcher 2.0 build still working after the requested 21.20.405 retest. This is not a new complete three-version device regression of the 2.0 build.

Automated validation under Patcher 2.0:

- Patch tests: **14 passed**, with **5 unrelated YouTube Music Jam APK-dependent skips**. YouTube extension tests: **87 passed**.
- Real-APK application, SDK DEX/hierarchy/resource checks and native hook assertions passed on all three supported versions with the queue and 24 related patches selected. Ask flyout menu skips 20.51.39 under its own version gate.
- Standalone and queue-unselected checks passed on 21.20.405. All **105070** resource strings passed validation.
- `:patches:buildAndroid` passed. The exact MPP and its downloaded copy passed archive/DEX integrity, current payload, supported-target metadata and actual Patcher 2.0 loader checks.

[Test MPP and build notes](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue-dev)
