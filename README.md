# Native playlist queue — step 2

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step2.mpp)

Source: [6f49c25ce](https://github.com/CaptKraken/morphe-patches/tree/6f49c25ce8b8293cf73d74df6dea8f1bac2c119e)

Replace the previous local MPP source in Morphe Manager with this file. Patch an **untouched YouTube 21.20.405 APK**. **Native playlist queue is selected by default**. Use patches from this source only to avoid mixing full Morphe bundles.

## Changes

Queue rows reuse titles and formatted metadata already loaded in YouTube's video cards. The patch reads those models through the project's shared element hook and returns their bytes unchanged. It does not make additional queue-metadata requests.

YouTube's existing large-thumbnail playlist rows display the title, channel and views/upload age. The channel line uses the existing player/flyout channel name when it matches the card's text exactly; otherwise the original combined text is preserved. Unindexed mix/playlist cards are excluded so their list title cannot replace a video's title. Internal title-restoration markers are removed through the existing title helper, while original-title and DeArrow hooks remain active.

Metadata depends on the native card being captured. Missing fields retain the available title/channel fallback; this preview does not fetch missing metadata. A video opened directly without a captured card can therefore lack views/age.

Adding a video still shows the existing confirmation toast, preserves browsing without opening the panel, and keeps the current video playing. No code was copied from the old queue branch.

## Phone checks

1. Play a regular video A from the feed, outside an account playlist.
2. On B's feed/recommendation menu, choose **Play next in native queue**. Expect the toast and no automatic expansion.
3. Manually open Queue. Check A and B's titles, channel names, views and upload age against their native cards, with large thumbnails. A should keep playing without a restart or seek.
4. Add C and check A, C, B order and the same metadata. Try titles/channel names in another language.
5. Close and reopen the player, then create another queue. Existing Add to queue should remain independent. Ordinary account playlists should retain their normal behavior.
6. If using Restore original titles or DeArrow, check those preferences still affect titles and that no internal marker is visible.

This remains the presentation/insertion milestone. Row playback, automatic advancement, player Next/Previous, duration badges, editing, unique entries, shuffle/repeat, fullscreen access, ratings and saving are not implemented yet.

## Validation

Four focused queue/metadata tests passed. The full test task passed seven tests in total; five existing Jam tests were skipped because a YouTube Music APK was not supplied. The production parser also read all 20 cards from a captured native response.

Stock YouTube 21.20.405 was patched and verified with Android SDK DEX/resource tools with the queue selected alongside eight related patches, and with those eight patches selected without the queue. The final Android bundle and its default-selection metadata were checked. Phone validation of the layout and metadata capture remains pending.

LICENSE and NOTICE are preserved. Only distribution manifest metadata differs from the build output. Earlier bundles remain available for reproduction; use the step 2 download above.

SHA-256: `83b99bdc17ab0731f0858d826467598d5e97daf8b0836f3c2059050210a64b5b`
