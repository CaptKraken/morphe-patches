# Native Queue — preview 19

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview19.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview19.mpp).

This preview restores Play next, Play last, and Clear temporary queue as contiguous added rows above the menu divider. Clear appears only while a temporary queue is active. When native queue actions are overridden, each original row is hidden only after its corresponding local replacement was successfully added to that same menu and video context. Partial/failed additions retain the native fallback actions. The original rows remain in their parent; their layout and visibility are restored when they detach so recycled views are not left hidden.

The original Add to queue override handling is unchanged. If added queue rows are disabled, existing overridden native queue actions remain visible and functional. If overriding is disabled, YouTube's original rows remain visible with their original behavior alongside the separate added local actions. When Native Queue is unselected, the new grouping callback returns without changing any views. Account-playlist menus and unrelated native actions are not hidden.

The default-selection change from preview 18 remains: Native temporary queue (experimental) inherits the patch API's default=true. Manager may retain saved selections; reset the selection to defaults or select the patch manually if needed. Other preview 17 runtime fixes remain included.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube 21.20.405 APK. Enable **Override feed flyout queue button** and the setting that adds queue rows to test the unified local group.

Built from source commit `403c67bf93057bed40777f0687524a49ec65e7da` on `feature/youtube-native-queue`. Preview branding is applied only to artifact metadata; contribution-source metadata, README and notices remain unchanged. Additional combination/isolation harnesses and development notes remain outside the source checkout.

Validation: all 66 queue tests passed, with 69 passing tests overall and 5 unrelated Jam APK tests skipped. New menu tests cover both settings, successful/partial/failed additions, panel identity/reuse, video and playlist guards, and unrelated actions. The resource checker validated 90,681 strings. Stock 21.20.405 APK application, DEX/cross-DEX verification and resource compilation passed standalone, alongside six related patches, and with Native Queue unselected. APK assertions verify the original Add to queue binding remains before local grouping, selected-patch/success guards survive shrinking, and hidden rows register cleanup that restores layout/visibility and releases its listener. Earlier repeat/shuffle test hooks survived the combined installation. The final Android bundle contains classes.dex. No Android device or emulator is attached, so actual menu appearance and repeated open/close/recycling still need phone testing. Only 21.20.405 is advertised.

SHA256: `8f0e61374b4807079872b2b262d3d4658e33e2002c99a19bf3912577e7cdde70`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
