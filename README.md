# Native playlist queue — dev builds

Test bundles from the dev-based feature branch. [PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953) describes the patch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: native queue playback and repeat fixes

- Source fix: local commit `fb0b7bfedcebbd6881dcf878a5d1dc9b96065453`, based on maintainer commit [`7f8a99ea4`](https://github.com/CaptKraken/morphe-patches/commit/7f8a99ea4b69438dea3c6af41cd6ce083d75819b). **The feature fix has not been pushed to the source branch yet.**
- Built: 2026-10-25. Bundle version: **1.49.0-dev.5**. Required Patcher: **2.0.0**.
- Native queue APK checks completed on **21.16.256**, **21.20.405**, **21.23.492**, and experimental **21.42.241**. Native mode is not offered on YouTube 20.x. The parent compatibility list also contains targets not verified for Native queue in this build, including **21.43.523**.

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-fb0b7bfed.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Add to queue** when patching. In YouTube, choose **Morphe → Feed → Play next in queue type → Native queue**. Native is now an option within Add to queue; it is no longer a separate selectable patch. Other queue types keep their existing routing.

This build fixes Native queue startup on 21.42.241 by using the active player's native playback backend. It restores the native shuffle button and repeat-all option, keeps repeat when selecting another queue card, and avoids rebuilding the whole panel for repeat-only updates.

The current build also includes:

- With a Native queue active, tapping a Mix card opens YouTube's native **Watch / Add to queue** dialog. Watch opens the original Mix; Add appends its initial batch while playback continues. The returned batch size is not fixed at 50.
- Swipe-to-remove, the row **Remove** action and **Clear queue** use YouTube's native trash icons.
- With a Native queue active, tapping a normal music-video card in Search or Home now plays that video within the queue instead of starting an automatic Mix. Without an active Native queue, YouTube's normal music-video behavior is preserved.
- Reload video clears an active Native queue and offers **Queue cleared / Undo**. Undo restores the queue onto the reloaded player without another reload or seek. Ordinary account playlists retain their original reload behavior.
- Native queue actions replace YouTube's built-in **Play next/last in queue** rows, with no separate custom section. Play last appears only while a Native queue exists.
- Creating a Native queue from an active playlist or Mix keeps the current video playing without reloading. Initial creation does not duplicate the playing video, and the Queue header keeps its measured text width.
- Closing the queue miniplayer with ×, a downward drag, or a fling offers Undo. Closed-player Undo restores the selected video near its saved position and uses the existing feed-preview protection while reopening it.

Individual video removal is silent. Clearing the queue from its header offers Undo and keeps playback uninterrupted. Queue-clear confirmations last **5 seconds normally** or **20 seconds with accessibility touch exploration**. Closed-player restoration uses whole-second URLs, so it may resume up to one second earlier.

Queues are temporary and do not persist after restarting the app. Clear queue Undo is an intentional addition.

[Screenshots](SCREENSHOTS.md)

## Validation

All **93 YouTube extension tests** and **39 patch tests** passed; **5 optional APK-dependent tests** were skipped and are not counted as APK checks. All **106298 resource strings** passed validation.

Real-APK application passed on **21.16.256**, **21.20.405**, **21.23.492**, and **21.42.241**, including SDK DEX, class-hierarchy, native bridge/control-flow contracts and resources. The related-patch selection includes Remember video quality, notifications, Loop video, flyout overrides, metadata/rating hooks, miniplayer and Reload. A 21.42.241 build with Native queue unselected passed isolation checks.

Full fingerprint validation passed on 21.20.405, 21.23.492 and 21.42.241. On 21.16.256, it stops in the unchanged upstream authentication hook because that fingerprint matches two methods; this also reproduces with Native queue unselected. Normal 21.16.256 patching and SDK/native checks passed. No authentication-hook change is included in this fix.

The user confirmed queue creation, repeat retention and playback through the whole queue on 21.42.241 using the preceding local candidate. This build additionally includes the maintainer's latest refactors and upstream sync; a phone regression check of this combined build is still pending.

The published MPP matches the validated native/Kotlin payloads. ZIP CRC, all extension/patch DEX checksums and the actual Patcher 2.0 loader passed (191 patches, Add to queue selected by default, all four queue types present). `:patches:buildAndroid` ran last. SHA256SUMS includes every MPP stored on this branch.

## Previous builds

- [Mix choice build 8c3dfae50](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-8c3dfae50.mpp), retained as the earlier build before the 21.42 playback, shuffle and repeat fixes.
- [Removal icon build 135e23313](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-135e23313.mpp), retained as the earlier build before the Mix-tap choice.
- [Music-video tap fix fe736a6dd](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-fe736a6dd.mpp), retained as the earlier build before the removal icon update.
- [Reload Undo build 884fc4368](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-884fc4368.mpp), retained as the earlier Add to queue integration build.
- [Menu override and seamless playlist test build](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-queue-creation-header-test.mpp), preserved from the user's earlier upload.
- [Swipe-close Undo and feed-preview protection build 082bec94b](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-082bec94b.mpp), retained as an earlier dedicated-patch build.
- [Player-close Undo build 92293b864](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-92293b864.mpp), retained as an earlier build before the gesture and feed-preview fixes.
- [Patcher 2.0 build 4a14e8bed](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-4a14e8bed.mpp), retained as an earlier Patcher 2.0 fallback.
- [Build e85946f95](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-e85946f95.mpp): bundle 1.48.0-dev.18 / Patcher 1.15.1, retained as an older tested fallback.

Historical PoC56 is broken and should not be used. PoC57 and the historical main-based source branch remain preserved.
