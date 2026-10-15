# Kraken Queue Patches — preview 13

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [youtube-native-queue-21.20.405-preview13.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview13.mpp).

This build follows a review of the full native-queue branch:

- Reset initial cached ratings when YouTube's native current account identity changes, and refresh queue rows after the account provider releases its lock. This avoids initializing refreshed buttons from the preceding account's cached rating. Account identities/tokens are not logged by the queue.
- Identify the derivative bundle with its own name, author and fork source links, as required by the project's branding/origin rules.
- Update the documentation to the current behavior and record the review, checks and remaining device scenarios.

All previously confirmed features remain: native queue UI, automatic and manual navigation, unique entries, reordering/removal, repeat/shuffle, titles/details, native ratings/undo and the native Save playlist menu/dialog. Save playlist was confirmed on the phone with preview 12. The account-cache reset needs a device retest, including account switching, sign-out/incognito and votes already in flight.

Disable the preceding preview source, import this file in Morphe Manager, and select **Kraken Queue Patches (preview 13)** alone for YouTube. Select **Native temporary queue (experimental)** alongside other desired patches from this source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `ac68688a5a5407f83efc6c6e794a0f6f1f07bd67` on `feature/youtube-native-queue`.

Validation: 45 queue tests passed; the full patch test task had 48 passing tests and 5 unrelated Jam APK tests skipped. The project resource checker validated 90,681 strings. Actual patch/dependency application, native account-hook checks, Android DEX/cross-DEX verification and resource compilation passed against stock 21.20.405. The Android bundle contains `classes.dex` and retained upstream license notices. No real account vote or playlist creation request was sent during cloud checks. No emulator/Android device was attached for the review, and the earlier optional direct-JVM Android-protobuf probe is not counted as passing.

[Usage and device checklist](https://github.com/CaptKraken/morphe-patches/blob/ac68688a5a5407f83efc6c6e794a0f6f1f07bd67/docs/youtube-native-queue.md) · [Branch review](https://github.com/CaptKraken/morphe-patches/blob/ac68688a5a5407f83efc6c6e794a0f6f1f07bd67/docs/youtube-native-queue-review.md)

SHA256: `afe69ebf979b4229bd93ecaf106d89ff0beeb26edee8ade036716c28d44876b3`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
