# Native Queue — preview 26

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview26.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview26.mpp).

This preview fixes an attachment bug associated with the reported “Local queue navigator is not attached” error. YouTube can forward empty watch responses from another navigator during playback transitions. The extension previously replaced its active navigator reference before checking for an empty response, disconnecting that reference from the attached queue. Empty responses now return before changing the reference. Repeat/shuffle hooks and native navigation implementations are unchanged.

A JVM reproduction using the actual compiled extension failed before the fix and passes afterward. It covers empty responses with repeat and shuffle enabled and disabled, preserves queue selection/order/mode state, and verifies that nonempty responses can still replace the active navigator. This establishes the code defect; on-device testing is still needed to confirm whether it fully accounts for the reported sequence.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; ensure Native temporary queue (experimental) is selected.

Phone checks:

- Start a queue with several videos. Enable Shuffle and cycle Repeat, then disable both. Use player Next/Previous repeatedly across transitions in portrait and fullscreen, with the queue expanded and collapsed. Confirm the playing-row highlight follows playback and neither error toast appears.
- Repeat with Shuffle enabled and with Repeat all/Repeat one. Check navigation boundaries with both disabled.
- Close the player and start another queue without restarting YouTube. Closing should reset the queue; minimize/PiP should preserve it.
- Open a normal account playlist and check that its controls, modes and menus still behave normally.

Validation: all **77 queue tests passed**, the focused attachment reproduction passed, resource formatting passed for **90,681 strings**, and standalone, seven-related-patch combined, and Native Queue-unselected APK validation passed with Android SDK DEX/cross-DEX verification and resource compilation. The Android `.mpp` bundle was built. No Android device or emulator is attached.

Built from source commit [`feaf23fa6f0a8b76a464cd39fbe8c47e9bbf3c78`](https://github.com/CaptKraken/morphe-patches/commit/feaf23fa6f0a8b76a464cd39fbe8c47e9bbf3c78) on `feature/youtube-native-queue`. Preview branding changes only artifact metadata; source metadata and notices remain unchanged.

Reproduce with the configured JDK and Android SDK:

```sh
./gradlew :patches:test :patches:checkStringResources :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue :patches:buildAndroid -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `87206ea2e3b7211f99da7ade448276f6ed5dd51e92bc9da11e015d636a29784a`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
