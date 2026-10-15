# Native YouTube queue preview 12

Download [youtube-native-queue-21.20.405-preview12.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview12.mpp).

This update adds the native three-dot button at the right of the repeat/shuffle row. Its native bottom sheet contains only **Save playlist**, with YouTube's bookmark icon, and no Share action.

Choosing Save opens YouTube's native creation dialog. Enter a playlist name, choose privacy, and confirm to create a new account playlist containing every queue video in visible order, including the playing and preceding entries. Shuffle does not change the saved order. Opening or cancelling the dialog does not create a playlist.

- The native creation command receives an immutable snapshot of explicit video IDs. It does not require or invent a source playlist ID.
- YouTube retains its normal sign-in, name validation, privacy controls, authenticated creation request and success/error handling.
- Saving creates a separate copy. The queue keeps its local identity; later edits or clearing do not update/delete the saved playlist. Saving again creates another playlist.
- The preview 11 rating actions were confirmed working on the device. This new save flow needs a device retest, including checking Library contents/order, cancellation, sign-in and request failures.

Disable the preceding preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 12)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `c65bd9f99b35a10343ced86d9475e43d09ec5010` on `feature/youtube-native-queue`.
Forty-three queue tests and real-APK DEX/resource verification passed. Save tests cover the single menu item, localized label/bookmark, exact ordered video IDs, duplicates/removals/reordering, pending-dialog snapshots, empty/single-item queues and preservation of playback/local state. APK checks require the existing native header listener and bottom sheet, creation command, authentication, name/privacy dialog and confirmation-to-request path. An optional JVM execution probe could not initialize the APK's optimized abstract protobuf classes and is not counted as passing. No account playlist was created during cloud validation. The actual dialog/save requires phone testing.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/c65bd9f99b35a10343ced86d9475e43d09ec5010/docs/youtube-native-queue.md

SHA256: `80ac0aa03d8eaedb6cb7f2a372dabcf5c2393f25cff5c1b4d0c0f9fae56afffc`

Older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
