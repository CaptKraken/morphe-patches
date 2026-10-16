# Native Queue — preview 18

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview18.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview18.mpp).

Native temporary queue (experimental) now follows upstream's default-selection convention: removing its explicit default=false override inherits default=true from the patch API. Manager may retain an existing saved selection; reset the selection to defaults or select the patch manually in that case. This changes initial patch selection, not the behavior of an already patched app.

All extension resources are byte-identical to preview 17. Its runtime fixes remain included. The reported split menu grouping also remains: a reused native Play next row can appear below the divider while added Play last/Clear rows appear above it. Grouping has not been changed in this preview.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `c5abed3e70089c2fd70c7d15b9e206d90bfcb0f9` on `feature/youtube-native-queue`. Only the default-selection override was removed from source. Preview branding is applied to artifact metadata; the contribution source retains upstream project metadata, README and notices.

Validation: the compiled patch object's default property was verified as true. All 60 queue tests passed, with 63 passing tests overall and 5 unrelated Jam APK tests skipped. The Android bundle build passed and contains classes.dex. Compared with preview 17, only the manifest and compiled patch definition/class DEX changed; all embedded extension resources are identical. This metadata-only update did not rerun full APK application checks. Preview 17 passed standalone, combined-patch and Native Queue-unselected APK/SDK verification. No Android device or emulator is attached. Only 21.20.405 is advertised.

SHA256: `e660aa05282d81f3d57e1d9c27911d50fb9d1586f3bc51a5cc7b58e041cffb0c`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
