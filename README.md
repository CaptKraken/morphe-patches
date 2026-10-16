# Native Queue — preview 25

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview25.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview25.mpp).

This preview finishes the discovery and shared-utility cleanup. Remaining custom method and constructor scanners are replaced with named, scoped fingerprints for native roles. Reference extraction, access flags and constructor register calculations reuse the shared Morphe utilities. Protobuf owner checks have separate fingerprint instances, and extension metadata uses named fields instead of tuple positions. The existing field and data-flow checks still establish the native contract.

This is a refactor with no added features or intended playback changes. The generated standalone output was compared against preview 24: eleven DEX files are byte-for-byte identical; the other file has the same class members and method instructions across 22,704 descriptions, with only `PatchInfo.PATCH_TIME` differing. Android SDK DEX/cross-DEX verification and resource compilation passed independently.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; ensure Native temporary queue (experimental) is selected.

Phone checks:

- Start a queue with at least three videos, Shuffle and Repeat off. Check player Next/Previous in portrait and fullscreen, with the queue expanded and collapsed. Confirm navigation follows the queue and respects its boundaries.
- Close the player and start another queue without restarting YouTube. Closing should reset the queue; minimize/PiP should preserve it.
- Check repeat/shuffle, drag reordering, swipe removal of nonplaying items, title/channel/statistics/duration display, rating toggles and Save playlist.
- Open a normal account playlist and check that its controls, modes and menus still behave normally.

Validation: all **77 queue tests passed**, resource formatting passed for **90,681 strings**, and standalone, seven-related-patch combined, and Native Queue-unselected APK validation passed. The combined checks preserve earlier mode/margin hooks and exercise shifted constructor/fullscreen anchors. The Android `.mpp` bundle was built. No Android device or emulator is attached, so phone testing remains necessary; this cleanup does not establish the cause of the previously reported intermittent navigation issue.

Built from source commit [`a7e96d0765c2c5a2b0782dea11ddd58cb3d8de7c`](https://github.com/CaptKraken/morphe-patches/commit/a7e96d0765c2c5a2b0782dea11ddd58cb3d8de7c) on `feature/youtube-native-queue`. Preview branding changes only artifact metadata; source metadata and notices remain unchanged.

Reproduce with the configured JDK and Android SDK:

```sh
./gradlew :patches:test :patches:checkStringResources :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue :patches:buildAndroid -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `81bed80d757b18446ce13c233941fe970fa0c6419185ab4e133f745c99bbbd30`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
