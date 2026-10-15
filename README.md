# Native YouTube queue preview 3

Download [youtube-native-queue-21.20.405-preview3.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview3.mpp).

The device tester confirmed that the native temporary playlist appears and advances automatically, but the video player's Next button opened a recommendation outside the queue. Preview 3 routes both player Next and Previous through the local queue navigator, updates their enabled states, and prevents a manual click at a queue boundary from opening an unrelated recommendation. These manual-control changes still need a device retest. Preview 2's playback-preservation changes are included.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 3)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings; the tester needed it to see the queue actions.

Built from source commit `3deb9e5068dffd811e0de56cd41fd5e2b8d045e5` on `feature/youtube-native-queue`.
Ten queue tests and real-APK DEX/resource verification passed, including regression checks for both manual player controls. Build verification does not establish playback behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/3deb9e5068dffd811e0de56cd41fd5e2b8d045e5/docs/youtube-native-queue.md

SHA256: `1613bd22a9cd296a796c46455429673568a383ccb576413cad553bd6fe6ded23`

The older preview 1 and preview 2 files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
