# Native YouTube queue preview 4

Download [youtube-native-queue-21.20.405-preview4.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview4.mpp).

The device tester confirmed that the native temporary playlist appears and advances automatically, but Next from the first of two queued videos still opened a recommendation and left an “End of playlist” bar. Preview 3 rejected the composite controls used by the stock player factory. Preview 4 captures handlers at their actual registration with the overlay and intercepts the alternate Next implementation as well as the base Next/Previous handlers. A queue control consumes a click if local navigation is unavailable, preventing recommendation fallback. Device playback still needs a retest. Earlier playback-preservation changes are included.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 4)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings; the tester needed it to see the queue actions.

Built from source commit `9db3c18a8afdc930cf8fde1579f6930bb004630f` on `feature/youtube-native-queue`.
Ten queue tests and real-APK DEX/resource verification passed, including regression checks for composite overlay registration and both Next implementations. Build verification does not establish playback behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/9db3c18a8afdc930cf8fde1579f6930bb004630f/docs/youtube-native-queue.md

SHA256: `6864d5aa7482de2a8c85d5d8f97358044b1dd14f4926416d560a19601cd8aa39`

The older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
