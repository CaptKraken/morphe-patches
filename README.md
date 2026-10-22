# Native playlist queue — dev builds

Test bundles built from the independent dev-based feature branch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build

- Source: [`e85946f95`](https://github.com/CaptKraken/morphe-patches/commit/e85946f956fcd656dcd6614513ad7c3ceb6628d4) on [`feature/youtube-native-playlist-queue-dev`](https://github.com/CaptKraken/morphe-patches/tree/feature/youtube-native-playlist-queue-dev).
- Built: 2026-10-22. Bundle version: `1.48.0-dev.18`.
- Supported YouTube versions: **20.51.39**, **21.16.256**, **21.20.405**.
- **20.21.37 is not supported.** Its experimental changes are excluded.
- Latest change: our generated confirmation after saving a queue to an existing playlist now uses **5 seconds**, retaining Undo. Native accessibility timing still applies; ordinary YouTube confirmations and queue removal/Clear Undo are unchanged.

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-e85946f95.mpp) · [Checksums](SHA256SUMS)

Download the MPP and import it directly into Morphe Manager. Select **Native playlist queue**. Disable Morphe's **Premium-style queue** setting when testing this queue; the existing Morphe queue takes priority when enabled.

The patch remains experimental. Queues are temporary and do not persist after restarting the app. Undo after clearing a queue is an intentional addition.

## Validation

The exact archive passed ZIP CRC, Android DEX checksums and queue entry-point checks, current YouTube extension and Kotlin payload comparisons, and the actual patch loader check (190 patches, queue selected by default). Both JVM metadata and Android DEX were checked for the supported targets above.

For this change, patch tests passed (14 passed, 5 unrelated APK-dependent skips) and all 87 extension tests passed. Combined real-APK SDK DEX/hierarchy/resource and native checks passed on all three supported targets; the 21.20.405 build with Native playlist queue unselected also passed. The native duration guard and original builder setter were verified. `:patches:buildAndroid` ran last and passed.

Earlier phone tests were reported successful on all three versions. The new five-second confirmation and Undo still need phone testing; a fresh **21.20.405** retest is pending. Check creation/miniplayer, Play next/last, shuffle/repeat, reorder, remove/Clear and Undo, background skips, Shorts return, fullscreen, whole-queue Save to existing/new playlists, and ordinary playlist/player Save.

Historical PoC56 is broken and should not be used. The older PoC57 fallback and historical source branch remain preserved.
