# Native Queue — preview 13

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview13.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview13.mpp).

This build follows a review of the full native-queue branch:

- Reset initial cached ratings when YouTube's native current account identity changes, and refresh queue rows after the account provider releases its lock. This avoids initializing refreshed buttons from the preceding account's cached rating. Account identities/tokens are not logged by the queue.
- Label this unofficial preview **Native Queue**. Preview manifest metadata is applied only to the distributed artifact; the contribution branch retains upstream project metadata and README.
- Update the documentation to the current behavior and record the review, checks and remaining device scenarios.

All previously confirmed features remain: native queue UI, automatic and manual navigation, unique entries, reordering/removal, repeat/shuffle, titles/details, native ratings/undo and the native Save playlist menu/dialog. Save playlist was confirmed on the phone with preview 12. The account-cache reset needs a device retest, including account switching, sign-out/incognito and votes already in flight.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone for YouTube. Select **Native temporary queue (experimental)** alongside other desired patches from this source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `f036d7b7e0237d4957579ed03e7dcaf3181e4e87` on `feature/youtube-native-queue`.

Validation: 45 queue tests passed; the full patch test task had 48 passing tests and 5 unrelated Jam APK tests skipped. The project resource checker validated 90,681 strings. Actual patch/dependency application, native account-hook checks, Android DEX/cross-DEX verification and resource compilation passed against stock 21.20.405. The Android bundle contains `classes.dex` and retained upstream license notices. No real account vote or playlist creation request was sent during cloud checks. No emulator/Android device was attached for the review, and the earlier optional direct-JVM Android-protobuf probe is not counted as passing.

[Usage and device checklist](https://github.com/CaptKraken/morphe-patches/blob/f036d7b7e0237d4957579ed03e7dcaf3181e4e87/docs/youtube-native-queue.md) · [Branch review](https://github.com/CaptKraken/morphe-patches/blob/f036d7b7e0237d4957579ed03e7dcaf3181e4e87/docs/youtube-native-queue-review.md)

SHA256: `e9618bbada5df7f41327503a903fb5376e4a976d8ea95b6dd9006477befcf47b`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
