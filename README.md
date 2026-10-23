# Native playlist queue — dev builds

Test bundles from the dev-based feature branch. [Draft PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953) describes the patch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: swipe-close Undo and feed-preview protection

- Source: [`082bec94b`](https://github.com/CaptKraken/morphe-patches/commit/082bec94b11a83cca1fe019109967e3917bd44d3) on [`feature/youtube-native-playlist-queue-dev`](https://github.com/CaptKraken/morphe-patches/tree/feature/youtube-native-playlist-queue-dev).
- Built: 2026-10-23. Bundle version: **1.48.0-dev.21**. Required Patcher: **2.0.0**.
- Supported YouTube versions: **20.51.39**, **21.16.256**, **21.20.405**. **20.21.37 is not supported.**

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-082bec94b.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Native playlist queue**. Disable Morphe’s **Premium-style queue** setting when testing this queue; the existing Morphe queue takes priority when enabled.

Changes since the previous published build:

- Closing the queue miniplayer with a downward drag or fling now shows **Queue cleared / Undo**, as closing with × already did. The original native gesture and dismissal remain intact. Undo restores the queue and reopens its selected video near the saved position in the miniplayer.
- Queue Undo now reuses Reload video's native feed-playback protection while reopening a closed player. It temporarily blocks feed previews when Undo is tapped, then releases its block after the restored player accepts the queue, on cancellation, or on timeout. This addresses the reported preview takeover; reporter verification remains pending.

Individual video removal remains silent. Clearing the queue from its header still shows Undo and keeps playback uninterrupted. Queue-clear confirmations last **5 seconds normally** or **20 seconds with accessibility touch exploration**. Saved playback positions use whole-second URLs, so restoration may resume up to one second earlier.

The patch remains experimental. Queues are temporary and do not persist after restarting the app. Undo after clearing a queue is an intentional addition.

[Screenshots](SCREENSHOTS.md)

## Validation

The final source passed **22 patch tests** and **85 YouTube extension tests**, with **5 unrelated Jam APK-dependent skips**. Final combined real-APK application on **20.51.39**, **21.16.256**, and **21.20.405**, including Reload video, Miniplayer and 24 other related patches, passed full SDK DEX, class-hierarchy, resource and native-hook checks. A queue-unselected check on **21.20.405** also passed. The gesture fix was additionally checked independently on 21.20.405 before committing the separate fixes.

The user confirmed swipe-down close handling works with the local candidate. The feed-preview issue could not be reproduced on their phone with the old build; verification of the new protection by the reporter remains pending. Earlier player-close Undo tests confirmed playback resume and negative cases for new playback, Shorts, expired Undo, ordinary/account playlists, and closing only the queue panel. Accessibility touch-exploration timing has automated coverage but remains unverified on a phone. Earlier builds were user-tested on all three supported versions.

`:patches:buildAndroid` ran last and passed. The published MPP passed ZIP CRC and Android DEX checksums, queue discovery/runtime entry-point checks, current YouTube extension and Kotlin payload comparisons, and the actual Patcher 2.0 loader check (191 patches, queue selected by default). JVM and Android metadata contain the three supported versions and current description; the manifest requires Patcher 2.0.0. Archive payloads match the local test ZIP; only the manifest build timestamp changed.

## Previous builds

- [Player-close Undo build 92293b864](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-92293b864.mpp), retained as the previous published build before the gesture and feed-preview fixes.
- [Patcher 2.0 build 4a14e8bed](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-4a14e8bed.mpp), retained as an earlier Patcher 2.0 fallback.
- [Build e85946f95](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-e85946f95.mpp): bundle 1.48.0-dev.18 / Patcher 1.15.1, retained as an older tested fallback. Its shorter Save confirmation and Undo were reported working.

Historical PoC56 is broken and should not be used. PoC57 and the historical main-based source branch remain preserved.
