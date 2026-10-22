# Native playlist queue — dev builds

Test bundles built from the independent dev-based feature branch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build

- Source: [`f6d38511a`](https://github.com/CaptKraken/morphe-patches/commit/f6d38511ad6c933311b7375462db0fea0e7d6e02) on [`feature/youtube-native-playlist-queue-dev`](https://github.com/CaptKraken/morphe-patches/tree/feature/youtube-native-playlist-queue-dev).
- Built: 2026-10-22. Bundle version: `1.48.0-dev.18`.
- Supported YouTube versions: **20.51.39**, **21.16.256**, **21.20.405**.
- **20.21.37 is not supported.** Its experimental changes are excluded.

[Download ZIP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-f6d38511a.zip) · [Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-f6d38511a.mpp) · [Checksums](SHA256SUMS)

Extract the ZIP and import the MPP into Morphe Manager. Select **Native playlist queue**. Disable Morphe's **Premium-style queue** setting when testing this queue; the existing Morphe queue takes priority when enabled.

The patch remains experimental. Queues are temporary and do not persist after restarting the app. Undo after clearing a queue is an intentional addition.

## Validation

The exact archive passed ZIP CRC, Android DEX checksums and queue entry-point checks, current YouTube extension and Kotlin payload comparisons, and the actual patch loader check (190 patches, queue selected by default). Both JVM metadata and Android DEX were checked for the supported targets above.

For this rebuild, `:patches:test` passed from Gradle cache (14 passed, 5 unrelated APK-dependent skips); `:patches:buildAndroid` ran last and passed. Earlier real-APK SDK DEX/hierarchy/resource and related-patch checks cover the unchanged committed implementation on all three supported targets; these expensive checks were not repeated for this rebuild.

Earlier phone tests were reported successful on all three versions. A fresh **21.20.405** retest of this bundle is pending. Check creation/miniplayer, Play next/last, shuffle/repeat, reorder, remove/Clear and Undo, background skips, Shorts return, fullscreen, whole-queue Save to existing/new playlists, and ordinary playlist/player Save.

Historical PoC56 is broken and should not be used. The older PoC57 fallback and historical source branch remain preserved.
