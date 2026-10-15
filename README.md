# Native YouTube queue preview 6

Download [youtube-native-queue-21.20.405-preview6.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview6.mpp).

Preview 6 replaces preview 5's custom red button with YouTube's own swipe-button renderer. The queue supplies standard button-renderer data; YouTube creates, styles, binds, and recycles the view through its existing playlist row presenter. Swipe toward the start edge (left in left-to-right layouts), then tap Remove. The native client-only HideEnclosingCommand emits the tagged-row event consumed by the local queue removal handler. No account playlist-edit endpoint is included. Appearance and removal still need a device retest.

Device testing of preview 4 confirmed the native queue UI, automatic advance, player Next/Previous, and drag reordering. Repeat and shuffle remain unimplemented.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 6)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `85fc9428476d156f2ea9f5890abf2a6f71768094` on `feature/youtube-native-queue`.
Twelve queue tests and real-APK DEX/resource verification passed. Checks cover the native swipe presenter lifecycle, renderer and client-command tags, localized button/accessibility data, and exact duplicate-entry removal after reordering. Build verification does not establish appearance or gesture behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/85fc9428476d156f2ea9f5890abf2a6f71768094/docs/youtube-native-queue.md

SHA256: `262de17cc7d4b64d1944edde2dcef5e68be8d432bfb59705b94925183547515b`

The older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
