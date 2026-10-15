# Native YouTube queue preview 7

Download [youtube-native-queue-21.20.405-preview7.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview7.mpp).

Preview 7 selects YouTube's compact, icon-only playlist swipe action with its native crossed-out-eye icon (VISIBILITY_OFF). The visible text supplied in preview 6 selected the wide-button layout; omitting it selects the host's icon-only padding. The localized accessibility label is retained. YouTube creates, styles, binds, and recycles the view. Swipe toward the start edge (left in left-to-right layouts), then tap the crossed-out-eye button to remove that queue entry. The Watch Later full-swipe removal behavior is not enabled.

The native client-only HideEnclosingCommand emits the tagged-row event consumed by the local queue removal handler. No account playlist-edit endpoint is included. Appearance and removal still need a device retest. Device testing of preview 4 confirmed the native queue UI, automatic advance, player Next/Previous, and drag reordering. Repeat and shuffle remain unimplemented.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 7)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled in the app's Morphe queue settings.

Built from source commit `19872e1d9aff0735348cd0ee2d49434a4ff3288a` on `feature/youtube-native-queue`.
Twelve queue tests and real-APK DEX/resource verification passed. The icon-only payload regression checks that no visible text is present, that the native eye-off icon is selected, and that the localized accessibility label and client-only removal command remain. Build verification does not establish appearance or gesture behavior on a device.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/19872e1d9aff0735348cd0ee2d49434a4ff3288a/docs/youtube-native-queue.md

SHA256: `943d12347d4b956a8013c6df299059dbe5685be1654b546b0671032a51c6c322`

The older preview files are superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
