# Native playlist queue — dev builds

Test bundles from the dev-based feature branch. [Draft PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953) describes the patch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: player-close Undo

- Source: [`92293b864`](https://github.com/CaptKraken/morphe-patches/commit/92293b8649aabe56d33719cd0a09c08789269ce1) on [`feature/youtube-native-playlist-queue-dev`](https://github.com/CaptKraken/morphe-patches/tree/feature/youtube-native-playlist-queue-dev).
- Built: 2026-10-23. Bundle version: **1.48.0-dev.21**. Required Patcher: **2.0.0**.
- Supported YouTube versions: **20.51.39**, **21.16.256**, **21.20.405**. **20.21.37 is not supported.**

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-92293b864.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Native playlist queue**. Disable Morphe’s **Premium-style queue** setting when testing this queue; the existing Morphe queue takes priority when enabled.

Changes since the previous published build:

- Closing the mini player clears our queue and shows **Queue cleared / Undo**. Undo restores the queue and reopens the selected video in the mini player near its saved position, resuming playback. Native timestamp URLs use whole seconds, so playback may resume up to one second earlier.
- The confirmation survives the native closing animation and appears once.
- Individual video removal through swipe or the row menu is silent. Clearing the whole queue from its header menu still shows Undo and keeps playback uninterrupted.
- Queue-clear confirmations last **5 seconds normally** or **20 seconds with accessibility touch exploration**; Undo expires with the confirmation. The existing Save confirmation remains five seconds with native accessibility handling.

The patch remains experimental. Queues are temporary and do not persist after restarting the app. Undo after clearing a queue is an intentional addition.

[Screenshots](SCREENSHOTS.md)

## Validation

Current source: **21 patch tests passed**, with **5 unrelated Jam APK-dependent skips**, and **85 YouTube extension tests passed**. The closing-state regression failed with the previous implementation and passes with this build. Latest combined real-APK application on **21.20.405**, including Miniplayer and 24 related patches, passed SDK DEX, class-hierarchy, resource and native-hook checks. The unchanged native discovery/injection also passed earlier checks on all three supported versions, plus standalone and queue-unselected checks on 21.20.405.

The user phone-tested player-close Undo and automatic playback resume. Negative checks passed for opening another video or queue before Undo expires (no stale queue residue), opening Shorts, expired Undo, closing ordinary playback/account playlists (no queue message), and closing only the queue panel. Accessibility touch-exploration timing has automated coverage but remains unverified on a phone. Earlier builds were user-tested successfully on all three supported versions.

`:patches:buildAndroid` ran last and passed. The exact published MPP passed ZIP CRC, Android DEX checksums and queue entry-point checks, current YouTube extension and Kotlin payload comparisons, and the actual Patcher 2.0 loader check (191 patches, queue selected by default). JVM and Android metadata contain the three supported versions and current description; the manifest requires Patcher 2.0.0. Archive payloads match the phone-tested local build from the same source commit; only the manifest build timestamp changed.

## Previous builds

- [Patcher 2.0 build 4a14e8bed](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-4a14e8bed.mpp), retained as the previous published Patcher 2.0 fallback.
- [Build e85946f95](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-e85946f95.mpp): bundle 1.48.0-dev.18 / Patcher 1.15.1, retained as an older tested fallback. Its shorter Save confirmation and Undo were reported working.

Historical PoC56 is broken and should not be used. PoC57 and the historical main-based source branch remain preserved.
