# Native playlist queue — dev builds

Test bundles from the dev-based feature branch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: Patcher 2.0

- Source: [`4a14e8bed`](https://github.com/CaptKraken/morphe-patches/commit/4a14e8bedd7e633e37cecdc339a1209181ab5b1f) on [`feature/youtube-native-playlist-queue-dev`](https://github.com/CaptKraken/morphe-patches/tree/feature/youtube-native-playlist-queue-dev).
- Built: 2026-10-22. Bundle version: **1.48.0-dev.21**. Required Patcher: **2.0.0**.
- Supported YouTube versions: **20.51.39**, **21.16.256**, **21.20.405**. **20.21.37 is not supported.**
- Integrates current upstream dev, including its official Patcher/patches-library/Gradle-plugin 2.0 upgrade. Existing queue production code is unchanged from the tested previous build; the APK-check combination now includes upstream’s new Ask flyout menu.

[Download Patcher 2.0 MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-4a14e8bed.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Native playlist queue**. Disable Morphe’s **Premium-style queue** setting when testing this queue; the existing Morphe queue takes priority when enabled.

The generated confirmation after saving a queue to an existing playlist uses **5 seconds**, retaining Undo and native accessibility timing. The patch remains experimental. Queues are temporary and do not persist after restarting the app. Undo after clearing a queue is an intentional addition.

[Screenshots](SCREENSHOTS.md)

## Validation

Under Patcher 2.0: 14 patch tests passed with 5 unrelated Jam APK-dependent skips; all 87 YouTube extension tests passed. Combined real-APK SDK DEX/hierarchy/resource and native checks passed on all three supported versions, with 24 related patches selected. Ask intentionally skips 20.51.39 under its own version gate. Standalone and queue-unselected checks also passed on 21.20.405. All 105070 resource strings passed validation.

`:patches:buildAndroid` ran last and passed. The exact MPP passed ZIP CRC, Android DEX checksums and queue entry-point checks, current YouTube extension and Kotlin payload comparisons, and actual Patcher 2.0 loader checks (191 patches, queue selected by default). Both JVM metadata and root Android DEX contain the three supported versions and current description; the manifest requires Patcher 2.0.0.

These checks do not replace phone testing. Previous builds were user-tested successfully on all three versions, including the shorter Save confirmation. This Patcher 2.0 build still needs Manager import and phone regression testing. Start with **21.20.405**: creation/miniplayer, Play next/last, video menus alongside Ask, shuffle/repeat, reorder, removal/Clear and Undo, background skips, Shorts return, fullscreen, whole-queue Save to existing/new playlists, and ordinary playlist/player Save.

## Previous tested build

[Download e85946f95 MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-e85946f95.mpp): bundle 1.48.0-dev.18 / Patcher 1.15.1, retained as the previous tested fallback. Its shorter Save confirmation and Undo were reported working.

Historical PoC56 is broken and should not be used. PoC57 and the historical main-based source branch remain preserved.
