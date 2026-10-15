# Native YouTube queue preview 8

Download [youtube-native-queue-21.20.405-preview8.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview8.mpp).

This update makes the temporary queue unique by video ID and enables its existing native shuffle and repeat controls.

- Both add actions reject an already queued video with **Already in queue**, preserving its position and playback. Removing or clearing it allows adding it again.
- Repeat uses the native off → repeat all → repeat one → off cycle. Repeat one applies to automatic advance; manual Next/Previous still navigate normally.
- Shuffle uses YouTube's native random successor/predecessor mapping, keeping the current video and visible list order. The selected row follows playback. Switching shuffle off returns to visible list order. Add/remove/reorder rebuild the mapping; title updates and video changes preserve it.
- Ending the queue resets both modes. Account playlists retain their native control path. No account playlist is created or edited.

Device testing of earlier previews confirmed the native queue UI, automatic advance, player Next/Previous, drag reordering, and the compact Mix-style swipe-to-reveal removal action. Uniqueness and the new shuffle/repeat controls require a device retest.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 8)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `ba31fb735ba93cec18f4611410c77eb4b9d913a4` on `feature/youtube-native-queue`.
Fifteen queue tests and real-APK DEX/resource verification passed. Validation checks the native random navigator, existing native button listeners and state rendering, guarded mode forwarders with intact account-playlist delegates, and valid random mappings before observer notifications. Build verification does not establish playback or control behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/ba31fb735ba93cec18f4611410c77eb4b9d913a4/docs/youtube-native-queue.md

SHA256: `3a85d7151533ff99e52047b5996f3df7ba04b12412ea8103e5b0af75dfc3d304`

The older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
