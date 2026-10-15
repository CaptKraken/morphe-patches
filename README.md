# Native Queue — preview 14

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview14.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview14.mpp).

This preview refactors the native queue patch to follow the project's fingerprinting conventions:

- Resolve host classes and members through stable fingerprints, native type relationships and protobuf Lite schema metadata. Production discovery no longer pins obfuscated class, method or field names.
- Separate the patch declaration, fingerprints, reference discovery, protobuf inspection and feature hook installation. Reuse shared fingerprints and mutable-method/reverse-instruction helpers.
- Resolve the host contract before installing native queue hooks, and reject missing or ambiguous matches.
- Add schema-reader tests for renamed fields/types, message lists, oneofs, enum metadata, large protocol field numbers and initializer control flow.

All previously confirmed features remain: native queue UI, automatic and manual navigation, unique entries, reordering/removal, repeat/shuffle, titles/details, native ratings/undo and the native Save playlist menu/dialog. Preview 14 needs a phone regression test. Account-cache invalidation from preview 13 also needs device coverage for account switching, sign-out/incognito and votes already in flight.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone for YouTube. Select **Native temporary queue (experimental)** alongside other desired patches from this source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `31737ed06d3dee3204975209b45b5f42d99ae587` on `feature/youtube-native-queue`. Preview manifest metadata is applied only to this artifact; the contribution source retains upstream project metadata and README.

Validation: all 49 queue tests passed; the full patch test task had 52 passing tests and 5 unrelated Jam APK tests skipped. The project resource checker validated 90,681 strings. Actual patch/dependency application, the existing independent native-binding assertions, Android DEX/cross-DEX verification and resource compilation passed against stock 21.20.405. The Android bundle contains `classes.dex` and retained upstream license notices. No real account vote or playlist creation request was sent during cloud checks. No emulator or Android device was attached. Only 21.20.405 is advertised; fingerprint discovery alone does not establish other-version compatibility.

[Usage and device checklist](https://github.com/CaptKraken/morphe-patches/blob/31737ed06d3dee3204975209b45b5f42d99ae587/docs/youtube-native-queue.md) · [Branch review and contribution guidance](https://github.com/CaptKraken/morphe-patches/blob/31737ed06d3dee3204975209b45b5f42d99ae587/docs/youtube-native-queue-review.md)

SHA256: `636e90285792db3b9a256fe2fac4ba7db61ea5c511f4234c13aac471ef07ff27`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
