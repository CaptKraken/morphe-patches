# Native YouTube queue preview 10

Download [youtube-native-queue-21.20.405-preview10.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview10.mpp).

This update adds the channel name and a second line with YouTube's formatted views and upload age to the existing native playlist rows, matching the metadata arrangement of a normal Mix.

- Native History rows supply their existing channel/statistics text. The playing video's channel is captured from the same native player response as its ID and title.
- Missing details load asynchronously through the existing mobile-web localized video-ID lookup. Only a structured video renderer with the exact queue video ID is accepted; nearby search results are ignored.
- Native field values take priority independently. Fallback responses fill missing fields without replacing native metadata. Locale-specific text, live-viewer labels and unknown dates are preserved; unavailable fields remain blank.
- Details are cached for up to 200 videos. Transient request failures have bounded retries, and responses for removed/re-added entries are discarded before updating the cache or row.
- The host binds the channel and video-info fields using its own fonts, colors, spacing and responsive visibility. Entry IDs, titles, selection, repeat mode and shuffle mapping are preserved when details arrive.

The preview 9 native-title update was confirmed on the device. This additional metadata needs a device retest, including asynchronous updates and missing/unlisted/live metadata. The new lookup does not require account credentials or create/edit account playlists.

Disable the preceding preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 10)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `ebf22fbf90133d6b60064beef7148db1d5733e64` on `feature/youtube-native-queue`.
Thirty-two queue tests and real-APK DEX/resource verification passed. Metadata tests cover exact-ID result isolation, localized/live/missing fields, independent source precedence, stable playback state, stale entry responses and the native channel/video-info wire fields. APK checks require native History metadata fields and the original native row TextView bindings. Build verification does not establish live lookup availability or layout behavior on the device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/ebf22fbf90133d6b60064beef7148db1d5733e64/docs/youtube-native-queue.md

SHA256: `b1121fec151adbf88684af504fce672f8013259c41d7d0a359513417de351640`

Older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
