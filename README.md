# Native playlist queue — dev builds

Test bundles for the **Native queue** option in **Add to queue**. The original implementation was merged in [PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953). Follow-up compatibility fixes are on [`fix/youtube-native-queue-experimental-versions`](https://github.com/CaptKraken/morphe-patches/tree/fix/youtube-native-queue-experimental-versions). Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: experimental-version compatibility fixes

- Source: [`395467a74`](https://github.com/CaptKraken/morphe-patches/commit/395467a749ad9290cbde528dccf92f7eeae6e98d), pushed on `fix/youtube-native-queue-experimental-versions`, based on upstream `dev` commit [`6407d5efa`](https://github.com/MorpheApp/morphe-patches/commit/6407d5efa0f7d99fb54909bc39e2074e8f60ac42).
- Three reviewable commits: [playback backend discovery](https://github.com/CaptKraken/morphe-patches/commit/f1242058cbcfa97e2bc5fd8e731091183579b78a), [shuffle and repeat](https://github.com/CaptKraken/morphe-patches/commit/a1952e147e8bd17aaee0e8d38ffb87f67d032bf2), and [queue navigation](https://github.com/CaptKraken/morphe-patches/commit/395467a749ad9290cbde528dccf92f7eeae6e98d).
- Built: 2026-10-25. Bundle version: **1.49.0-dev.5**. Required Patcher: **2.0.0**.
- Experimental targets checked: **21.28.208**, **21.42.241**, **21.43.523**. These targets remain marked experimental.
- Phone testing reported successful on the listed targets from **21.20.405** through **21.43.523**. Other versions in the parent patch's compatibility list were not verified by this test round.

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-395467a74.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Add to queue** when patching. In YouTube, choose **Morphe → Feed → Play next in queue type → Native queue**. Native is now an option within Add to queue; it is no longer a separate selectable patch. Other queue types keep their existing routing.

This build connects Native queue to the active player's native playback backend, restores the shuffle button and repeat state, and retains repeat when selecting another queue card. It also fixes disabled Previous, unresponsive Next, and navigation failing at queue boundaries while keeping YouTube's native selection and playback loading.

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

Each of the three source commits passed the patch and YouTube extension checks (**39 patch tests**, **93 extension tests**; **5 optional APK-dependent tests skipped**), the JVM patch build, and **`:patches:buildAndroid`**. All **106308 resource strings** passed validation for the final source tree.

Real-APK application passed with Native queue selected and unselected on **21.28.208**, **21.42.241**, and **21.43.523**, plus a selected regression check on **21.23.492**. All seven checks enabled fingerprint validation and passed SDK DEX, class hierarchy, native bridge/control-flow contracts and resources. The related-patch selection includes notifications, Loop video, playlist autoplay suppression, flyout overrides, metadata/rating hooks, miniplayer, Reload and player button visibility.

The user reported successful phone tests on the listed targets **21.20.405**, **21.23.492**, **21.28.208**, **21.42.241**, and **21.43.523**, and confirmed background/screen-off notification navigation. This does not establish support for unlisted intermediate releases or older targets.

The commit split preserved the complete phone-tested source tree. The final YouTube extension and all packaged class payloads match the inputs used for the APK checks. The published MPP is the verified final-commit Android build: ZIP CRC, patch/extension DEX checksums and the actual Patcher 2.0 loader passed (191 patches, Add to queue selected by default, all four queue types present). No Gradle archive tasks ran after its final build and verification. SHA256SUMS includes every MPP stored on this branch.

## Previous builds

- [Earlier 21.42 compatibility build fb0b7bfed](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-fb0b7bfed.mpp), retained as the build before the Next/Previous navigation fix and full experimental-version checks.
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
