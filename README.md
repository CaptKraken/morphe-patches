# Native playlist queue — dev builds

Test bundles for the **Native queue** option in **Add to queue**. The original implementation was merged in [PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953), and experimental-version compatibility fixes were merged in [PR #4076](https://github.com/MorpheApp/morphe-patches/pull/4076). This build contains the follow-up Native queue enhancements. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: playlist preservation and whole-playlist additions

- Source: `feat/youtube-native-queue-enhancements`, commit `5e8239650bf3269a77d4427d6ee057b1c7b756ef`, based on upstream `dev` commit [`e9f77d7c5`](https://github.com/MorpheApp/morphe-patches/commit/e9f77d7c5932499ababf91481b883c30e96ccd1c).
- The feature source branch has not been pushed yet; this publication updates only the build branch.
- Built: 2026-10-27. Bundle version: **1.49.0-dev.7**. Required Patcher: **2.0.0**.
- Real-APK checks passed on **21.20.405**, **21.42.241**, and **21.43.523**. Phone testing after the rebase is pending.

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-5e8239650.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Add to queue** when patching. In YouTube, choose **Morphe → Feed → Play next in queue type → Native queue**. Native is now an option within Add to queue; it is no longer a separate selectable patch. Other queue types keep their existing routing.

This build preserves the playing playlist when an outside video's **Play next in queue** converts it into a Native queue. Large playlists load further native pages as needed while the header retains the source total. Outside additions keep their position and display metadata as more pages arrive. Shuffle and Save all finish loading the remaining pages in the background and show YouTube's native loading state before completing.

Playlist and Mix cards can also supply their whole list through **Play next / Play last in queue**, including private library playlists and Watch Later. These actions can start a queue or insert into an existing one. Library cards use their own mounted native command to identify the selected playlist. Mixes retain their initial generated batch.

The previously merged playback compatibility fixes remain included: native backend discovery, shuffle and repeat state, and Previous/Next navigation.

The current build also includes:

- With a Native queue active, tapping a Mix card opens YouTube's native **Watch / Add to queue** dialog. Watch opens the original Mix; Add appends its initial batch while playback continues. The returned batch size is not fixed at 50.
- Swipe-to-remove, the row **Remove** action and **Clear queue** use YouTube's native trash icons.
- With a Native queue active, tapping a normal music-video card in Search or Home now plays that video within the queue instead of starting an automatic Mix. Without an active Native queue, YouTube's normal music-video behavior is preserved.
- Reload video clears an active Native queue and offers **Queue cleared / Undo**. Undo restores the queue onto the reloaded player without another reload or seek. Ordinary account playlists retain their original reload behavior.
- Native queue actions replace YouTube's built-in **Play next/last in queue** rows, with no separate custom section. For single videos, Play last appears only while a Native queue exists. Playlist cards can start a queue through either Next or Last.
- Creating a Native queue from an active playlist or Mix keeps the current video playing without reloading. Initial creation does not duplicate the playing video, and the Queue header keeps its measured text width.
- Closing the queue miniplayer with ×, a downward drag, or a fling offers Undo. Closed-player Undo restores the selected video near its saved position and uses the existing feed-preview protection while reopening it.

Individual video removal is silent. Clearing the queue from its header offers Undo and keeps playback uninterrupted. Queue-clear confirmations last **5 seconds normally** or **20 seconds with accessibility touch exploration**. Closed-player restoration uses whole-second URLs, so it may resume up to one second earlier.

Queues are temporary and do not persist after restarting the app. Clear queue Undo is an intentional addition.

[Screenshots](SCREENSHOTS.md)

## Validation

The final source passes the patch and YouTube extension checks (**60 patch tests**, **156 extension tests**; **5 optional APK-dependent tests skipped**), the Android patch build, and validation of **108532 resource strings**. These totals include unrelated project tests. The two newest commits were checked separately; the final cleanup test run restored matching Gradle test-cache results.

Real-APK application passed with Native queue selected on **21.20.405**, **21.42.241**, and **21.43.523**, and unselected on **21.20.405**. The checks enabled global fingerprint validation and passed SDK DEX, class hierarchy, native bridge/control-flow contracts and resources. The intermediate library-card fix was also independently patched and checked on **21.42.241** before its commit. The final source is identical to the complete tree used for the three-version checks; that matrix was reused after splitting the commits.

The related-patch selection covers notifications, Loop video, playlist autoplay suppression, flyout overrides, metadata/rating hooks, miniplayer, Reload and player button visibility. The mounted-card accessors were compared with the original native click-action and command-decoding paths; they do not execute the card's navigation.

Earlier feature behavior was confirmed on the user's phone on **21.20.405** and **21.43.523**, with a partial playlist-conversion check on **21.23.492**. A phone smoke test of this rebased build is still pending. The current branch has not revalidated **21.16.256**; the checks above do not establish support for unlisted releases.

The published MPP is the exact validated local Android build. ZIP CRC, patch/extension DEX checksums, exact tested class/extension payloads, source hashes and the actual Patcher 2.0 loader passed (193 patches, Add to queue selected by default, all four queue types present). No Gradle archive tasks ran after the final build and verification. SHA256SUMS includes every MPP stored on this branch.

## Previous builds

- [Rebased compatibility build d9d8e9c49](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-d9d8e9c49.mpp), retained as the phone-tested compatibility build before playlist preservation and whole-playlist additions.
- [Pre-rebase compatibility build 395467a74](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-395467a74.mpp), retained as the tested build before the latest upstream refactor and fixes.
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
