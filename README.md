# Native playlist queue — dev builds

Test bundles from the dev-based feature branch. [Draft PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953) describes the patch. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: trash icons for queue removal

- Source: [`135e23313`](https://github.com/CaptKraken/morphe-patches/commit/135e233130603c348ac964be4b7635de9cccb919) on [`feature/youtube-native-playlist-queue-dev`](https://github.com/CaptKraken/morphe-patches/tree/feature/youtube-native-playlist-queue-dev).
- Built: 2026-10-24. Bundle version: **1.48.0**. Required Patcher: **2.0.0**.
- Verified Native queue targets for this build: **21.16.256** and **21.20.405**. Native mode is not offered on YouTube 20.x, including **20.51.39** and **20.21.37**. Other queue types use the parent Add to queue patch compatibility list.

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-135e23313.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Add to queue** when patching. In YouTube, choose **Morphe → Feed → Play next in queue type → Native queue**. Native is now an option within Add to queue; it is no longer a separate selectable patch. Other queue types keep their existing routing.

Swipe-to-remove and the queue row's **Remove** menu item now use YouTube's native trash icon, matching **Clear queue**. Removal behavior is unchanged.

The current build also includes:

- With a Native queue active, tapping a normal music-video card in Search or Home now plays that video within the queue instead of starting an automatic Mix. Tapping an explicit Mix card still opens the Mix normally. Without an active Native queue, YouTube's normal music-video behavior is preserved.
- Reload video clears an active Native queue and offers **Queue cleared / Undo**. Undo restores the queue onto the reloaded player without another reload or seek. Ordinary account playlists retain their original reload behavior.
- Native queue actions replace YouTube's built-in **Play next/last in queue** rows, with no separate custom section. Play last appears only while a Native queue exists.
- Creating a Native queue from an active playlist or Mix keeps the current video playing without reloading. Initial creation does not duplicate the playing video, and the Queue header keeps its measured text width.
- Closing the queue miniplayer with ×, a downward drag, or a fling offers Undo. Closed-player Undo restores the selected video near its saved position and uses the existing feed-preview protection while reopening it.

Individual video removal is silent. Clearing the queue from its header offers Undo and keeps playback uninterrupted. Queue-clear confirmations last **5 seconds normally** or **20 seconds with accessibility touch exploration**. Closed-player restoration uses whole-second URLs, so it may resume up to one second earlier.

Queues are temporary and do not persist after restarting the app. Clear queue Undo is an intentional addition.

[Screenshots](SCREENSHOTS.md)

## Validation

For this icon update, all **90 YouTube extension tests** passed. The earlier music-tap implementation also passed **37 patch tests**, with **5 unrelated optional APK-dependent skips**. Earlier combined real-APK checks on **21.16.256** and **21.20.405**, plus a Native-unselected check on **21.20.405**, passed SDK DEX, class-hierarchy, resource and native-hook verification. This update changes two icon values; native hooks and removal actions are unchanged.

The user confirmed normal music-video and explicit Mix taps, Home-feed taps, Next/Previous through the preserved queue, and stock music behavior without an active queue. Earlier Reload/Undo, queue creation, menu overrides, seamless playlist switching, duplicate handling and header fixes were also phone-confirmed. These phone confirmations cover the reported flows on the user's device; separate real-APK checks cover both Native targets.

The MPP was rebuilt from the committed source after validation. Its archive payloads match the local icon test build; only the manifest build timestamp changed. ZIP CRC, Android DEX checksums, current YouTube extension and Kotlin payload comparisons, and the actual Patcher 2.0 loader check passed (191 patches, Add to queue selected by default, all four queue types present). `:patches:buildAndroid` ran last. SHA256SUMS includes every MPP stored on this branch.

## Previous builds

- [Music-video tap fix fe736a6dd](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-fe736a6dd.mpp), retained as the earlier build before the removal icon update.
- [Reload Undo build 884fc4368](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-884fc4368.mpp), retained as the earlier Add to queue integration build.
- [Menu override and seamless playlist test build](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-queue-creation-header-test.mpp), preserved from the user's earlier upload.
- [Swipe-close Undo and feed-preview protection build 082bec94b](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-082bec94b.mpp), retained as an earlier dedicated-patch build.
- [Player-close Undo build 92293b864](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-92293b864.mpp), retained as an earlier build before the gesture and feed-preview fixes.
- [Patcher 2.0 build 4a14e8bed](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-4a14e8bed.mpp), retained as an earlier Patcher 2.0 fallback.
- [Build e85946f95](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-e85946f95.mpp): bundle 1.48.0-dev.18 / Patcher 1.15.1, retained as an older tested fallback.

Historical PoC56 is broken and should not be used. PoC57 and the historical main-based source branch remain preserved.
