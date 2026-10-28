# Native playlist queue — dev builds

Test bundles for the **Native queue** option in **Add to queue**. The original implementation was merged in [PR #3953](https://github.com/MorpheApp/morphe-patches/pull/3953), and experimental-version compatibility fixes were merged in [PR #4076](https://github.com/MorpheApp/morphe-patches/pull/4076). This build contains the follow-up Native queue enhancements. Historical PoC builds remain on [`builds/youtube-native-playlist-queue`](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue).

## Latest build: playlist additions during standalone playback

- Source: [`feat/youtube-native-queue-enhancements`](https://github.com/CaptKraken/morphe-patches/tree/feat/youtube-native-queue-enhancements), commit [`c3132676b`](https://github.com/CaptKraken/morphe-patches/commit/c3132676b3f2480b5b6dfb230924cd52d1954f44), based on upstream `dev` commit [`e9f77d7c5`](https://github.com/MorpheApp/morphe-patches/commit/e9f77d7c5932499ababf91481b883c30e96ccd1c).
- Built: 2026-10-28. Bundle version: **1.49.0-dev.7**. Required Patcher: **2.0.0**.
- Uses YouTube's own repeat-state getter before creating a queue around a playing standalone video. This removes the concrete playlist-queue cast that could reject whole-playlist/Mix additions before attachment.
- Phone confirmation of this fix is **pending**. The first affected YouTube version has not been established. Patching and SDK checks are separate from phone playback tests.

[Download latest MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-c3132676b.mpp) · [Checksums](SHA256SUMS)

Import the MPP directly into a Morphe Manager version that supports **Patcher 2.0**. Select **Add to queue** when patching. In YouTube, choose **Morphe → Feed → Play next in queue type → Native queue**. Native is now an option within Add to queue; it is no longer a separate selectable patch. Other queue types keep their existing routing.

This build preserves the playing playlist when an outside video's **Play next in queue** converts it into a Native queue. The original source playlist loads further native pages as needed while the header retains the source total. Additional saved playlists are fetched completely, page by page in the background, before being inserted together. Outside additions keep their position and display metadata as more pages arrive. Shuffle and Save all finish loading the remaining pages in the background and show YouTube's native loading state before completing.

Playlist and Mix cards can also supply their whole list through **Play next / Play last in queue**, including private library playlists and Watch Later. These actions can start a queue or insert into an existing one. Library cards use their own mounted native command to identify the selected playlist. Mix menu actions now fetch the same full initial batch as the tap-dialog Add action, rather than adding only the seed video. They can start a queue or insert the batch through Next/Last. Normal music-video cards still add one video.

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

The current source passes **60 patch tests** and **157 YouTube extension tests**, with **5 optional APK-dependent tests skipped**, and the Android patch build. These totals include unrelated project tests. No new test methods were added for this fix; the existing standalone collection-start scenario now covers normal playlists and Mixes through Next/Last, preserving the current video, repeat state and playback without reopening the player.

Current-source real-APK application passed with Native queue selected on **21.20.405**, **21.42.241** and **21.43.523**, and unselected on **21.20.405**. Those runs enabled global fingerprint validation and passed SDK DEX/class hierarchy, native bridge/control-flow contracts and resources. Related patches include notifications, Loop video, playlist autoplay suppression, flyout overrides, metadata/rating hooks, miniplayer, Reload and player button visibility. The new native contract check rejects the earlier emitted bridge's concrete cast and verifies delegation to YouTube's original repeat getter.

On **21.16.256**, full strict fingerprint validation stops before the queue patch at the shared `AuthenticationChangeListenerLegacyFingerprint`, which matches two methods. The published preceding source has the same failure. Repeat-getter discovery was separately verified as unique on that APK with fingerprint validation enabled. An isolated combined run with global fingerprint validation disabled passed SDK DEX/class hierarchy, native bridge/control-flow, mounted-command and resource checks. This is **not** a full strict-validation pass for 21.16.256.

Phone validation is pending. The reported failure was on **21.43.523** when adding a Mix through its three-dot menu while a standalone video was playing; the same action already worked with an active queue or playing playlist. No earliest affected version or complete phone-tested version matrix is claimed.

For retesting: play a standalone video, open a Mix card's three-dot menu and choose **Play next in queue**. Playback should continue and the whole returned Mix batch should follow the current video. Repeat with a normal playlist card, and check the existing active-queue and playing-playlist cases.

The published MPP is the exact validated local Android build. ZIP CRC, root and extension DEX size/checksums, exact APK-tested class/extension payloads, source hashes and the actual Patcher 2.0 loader passed (193 patches, Add to queue selected by default, all four queue types present). No Gradle archive tasks ran after the final build and verification. SHA256SUMS includes every MPP stored on this branch.

## Previous builds

- [Whole-Mix menu build a0d8a291a](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-a0d8a291a.mpp), retained as the preceding phone-tested build before the standalone repeat-state bridge fix. The user confirmed its Mix menu fix on 405, then reported that all checks work without listing further version numbers; this does not confirm the newly reported standalone path.
- [Playlist enhancement build 5e8239650](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue-dev/native-playlist-queue-patcher2-5e8239650.mpp), retained as the build before the whole-Mix menu fix.
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
