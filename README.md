# Native Queue — preview 24

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview24.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview24.mpp).

This preview contains the branch-wide review fixes. Native controls, listener roles, initialization and visibility hooks use named fingerprints and matched instructions. Video/playlist IDs follow the labeled model fields through constructor and builder assignments; drag coordinates follow constructor parameters. Manual player hooks use the existing register utilities and preserve earlier hooks. Ownership uses weak object identity so equal account-playlist models cannot inherit local ownership. Protobuf discovery follows actual call-result stores and invalidates overwritten constants. Queue insertion rejects malformed video IDs.

The existing control-registration lifetime fix remains. Additional navigation diagnostics remain removed. Support is still limited to YouTube **21.20.405**.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; ensure Native temporary queue (experimental) is selected.

Phone checks:

- Start a first queue with at least three videos, Shuffle and Repeat off. Use player Next/Previous repeatedly in portrait and fullscreen, with the queue expanded and collapsed. Confirm Next follows the queue and does not append a recommendation; check both boundaries.
- Close the player, start another queue without restarting YouTube, and repeat. Minimize/PiP should preserve the queue; closing should reset it.
- Check repeat/shuffle, reordering, swipe removal of nonplaying items, native rating toggles, metadata/durations and Save playlist.
- Open a normal account playlist and check its controls, modes and menus. Confirm flyout override and hide-button settings still apply.

Built from source commit [`0b1ccebacdad19347c4d746a15c87da393397392`](https://github.com/CaptKraken/morphe-patches/commit/0b1ccebacdad19347c4d746a15c87da393397392) on `feature/youtube-native-queue`. Preview branding is applied only to artifact metadata; source project metadata and notices remain unchanged.

Validation: all **77 queue tests passed**, including receiver preservation, reordered constructor stores, unrelated reads, stale constant invalidation and equal-but-unowned models. Standalone and seven-related-patch combined APK application, Android DEX/cross-DEX verification and resource compilation passed; the final combined run also shifts constructor/fullscreen anchors and retains earlier hooks. Native Queue-unselected APK validation passed. String-resource formatting passed (90,681 strings), and the Android patch bundle was built. There is no attached Android device or emulator; these results do not establish runtime behavior or reproduce the intermittent first-queue navigation failure.

Reproduce with the configured JDK and Android SDK:

```sh
./gradlew :patches:test :patches:checkStringResources :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue :patches:buildAndroid -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `13ebc9902fd48c6d7fcbc1cd241cb88825f7daf7aaa38f8b2fe5306700f03fe4`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
