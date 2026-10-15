# Native YouTube queue preview 11

Download [youtube-native-queue-21.20.405-preview11.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview11.mpp).

This update enables the selected queue row's native thumbs-up and thumbs-down buttons. Tap a selected thumb again to remove that rating, or switch directly to the other thumb. These actions change the signed-in account's rating for that video.

- The queue supplies native toggle-button renderers and video-specific LikeCommand endpoints, including undo endpoints. YouTube creates and handles the buttons itself.
- The native command retains the account check, sign-in flow, authenticated requests, error handling and rating rollback. No custom HTTP voting or credentials are introduced.
- Native account entity subscriptions and legacy rating events keep the presenters updated. A bounded cache of observed native ratings initializes refreshed rows. Native presenter recycling releases its subscriptions.
- Queue edits still do not create or edit an account playlist. Native rating targets contain only the video's ID.

The preview 10 channel and video-statistics update was confirmed on the device. This rating update needs a device retest: like, unlike, dislike, undo dislike, switching thumbs, synchronization with the main player, initial ratings, sign-in/account changes and failed-request rollback. No real account rating was submitted during cloud validation.

Disable the preceding preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 11)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `3f625b7552ea39228f1a24645578fa2e393b5f47` on `feature/youtube-native-queue`.
Thirty-eight queue tests and real-APK DEX/resource verification passed. Rating tests cover per-video default/undo command targets after reordering, native icons and toggle states, localized accessibility, bounded caching, and preservation of playback endpoints. APK checks verify native dispatch, sign-in, entity/event lifecycle and rollback paths. These checks establish build compatibility, not live account behavior on the device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/3f625b7552ea39228f1a24645578fa2e393b5f47/docs/youtube-native-queue.md

SHA256: `9b15d079c696f7e81d67edbc039eacafef094625d0c5f3db66d38559be17aeab`

Older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
