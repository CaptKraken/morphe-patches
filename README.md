# Native YouTube queue preview 5

Download [youtube-native-queue-21.20.405-preview5.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview5.mpp).

Preview 5 adds a Remove button to the native row swipe layout. Swipe left to reveal the red trash button, then tap it to remove that queue entry. In right-to-left layouts, swipe toward the start edge instead. The action uses the same local removal path as the row menu. Rebinding or recycling clears the action and resets the swipe position so it cannot carry over to another entry or an account playlist row. Swipe removal still needs a device retest.

Device testing of preview 4 confirmed the native queue UI, automatic advance, player Next/Previous, and drag reordering. Repeat and shuffle remain unimplemented.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 5)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `192f803c61d23518748e1b2da8e41ed27a2963d6` on `feature/youtube-native-queue`.
Eleven queue tests and real-APK DEX/resource verification passed, including checks for native swipe setup and cleanup, and removal of an exact duplicate entry after reordering. Build verification does not establish gesture or playback behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/192f803c61d23518748e1b2da8e41ed27a2963d6/docs/youtube-native-queue.md

SHA256: `f11838c8dfab78a52a9b5a73b71096b76154d0af5c0bca5390fcdcdec3646ea6`

The older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
