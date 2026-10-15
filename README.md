# Native YouTube queue preview 9

Download [youtube-native-queue-21.20.405-preview9.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview9.mpp).

This update reads titles from YouTube's native card, History-menu and player-response data. It reduces the video-ID placeholders caused by failed public metadata lookups.

- Queue actions capture the video ID and title together before menu dismissal or another menu can change the context.
- Card-title matching stays within the same video's component and handles Unicode, punctuation and recoverable truncated labels. Ambiguous/truncated-only data uses the fallback rather than another video's title.
- History uses its structured native title field. The initial playing entry uses an ID/title pair from the same player response on YouTube 21.20.405, where the generic title hook is absent.
- Captured native titles take priority over late network responses. A bounded cache retains up to 200 titles.
- Missing titles use the public oEmbed fallback. Transient failures retry after one and four seconds, at most three requests; permanent access/not-found failures do not retry. Retries stop if a native title arrives or the entry is removed. An old response cannot rename a removed/re-added video.
- Title updates preserve entry identity, selection, repeat mode and the native shuffle mapping.

Earlier device testing confirmed native queue UI, automatic advance, player Next/Previous, drag reorder, Mix-style swipe removal, and the preview 8 uniqueness/shuffle/repeat update. The new native-title capture paths still need a device retest, including feed/search/History, Khmer and long titles, and rapidly switching menus. A video ID remains a fallback when neither native data nor public metadata provides a title.

Disable the preceding preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 9)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `07cc950dedf0a91217f9cefafe6429cb575fe9b3` on `feature/youtube-native-queue`.
Twenty-four queue tests and real-APK DEX/resource verification passed. Model regressions cover title extraction/isolation, cache precedence, immutable title snapshots, stale entry responses and bounded retry policy. APK checks require the paired player metadata hook, both menu capture routes surviving shrinking, and the History title renderer matching its existing video-ID interface. Build verification does not establish live card-layout coverage or playback behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/07cc950dedf0a91217f9cefafe6429cb575fe9b3/docs/youtube-native-queue.md

SHA256: `c29ac862741ff0373bea7b16c83687f94b042fea7aa054eb4e1d33d01be727b3`

The older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
