# Native Queue — preview 27

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview27.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview27.mpp).

This preview addresses two remaining response-ownership failures missed by preview 26. A nonempty callback during a local panel refresh, or a response without a valid video ID or account playlist ID, could replace the current navigator and watch response before returning without attaching the queue. Subsequent local Shuffle-off requests were rejected because the navigator references no longer matched. Response validation and the local-refresh guard now run before ownership updates. Opening an account playlist still clears/releases the local queue.

A direct lifecycle regression starts from consistent ownership and fails against preview-26 source. It passes against this source across six null/refresh/invalid-response cases with the first and last items selected; Shuffle-off and Repeat-off remain accepted, and entries/selection are preserved. Valid response replacement and account-playlist handoff also pass. The probe executes the actual extension handlers and queue model with native-access and Android UI test doubles. This establishes code defects, not which callback triggers the reported intermittent failure on a phone.

Generated standalone APK comparison against preview 26 changes only NativeQueuePatch.onResponse and PatchInfo.PATCH_TIME. The other ten DEX files are byte-identical; native navigation, repeat/shuffle hooks and UI implementations are unchanged.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; ensure Native temporary queue (experimental) is selected.

Phone checks:

- Start a queue with at least three videos and select the last item. While it is still playing, toggle Shuffle on/off repeatedly and cycle Repeat back to off. Confirm the icons change each time.
- With both modes off at the last item, Previous should return to the preceding queue item; Next should be unavailable at the actual queue boundary. Neither error toast should appear.
- Repeat after reaching the last item through player Next and through automatic playback. Compare portrait/fullscreen and expanded/collapsed queue views.
- Close the player and start another queue without restarting YouTube. Opening a normal account playlist should release the local queue and retain normal native controls.

Validation: all **77 queue tests passed**, the before/after lifecycle regression passed, resource formatting passed for **90,681 strings**, and standalone, seven-related-patch combined, and Native Queue-unselected APK validation passed with Android SDK DEX/cross-DEX verification and resource compilation. The Android `.mpp` bundle was built. No Android device or emulator is attached; the user's last-item report concerns a video still playing, so completion cleanup remains a separate hypothesis rather than an established cause.

Built from source commit [`eda633f6b8c7da821c97cd0b9eab3d647ff351a9`](https://github.com/CaptKraken/morphe-patches/commit/eda633f6b8c7da821c97cd0b9eab3d647ff351a9) on `feature/youtube-native-queue`. Preview branding changes only artifact metadata; source metadata and notices remain unchanged.

Reproduce with the configured JDK and Android SDK:

```sh
./gradlew :patches:test :patches:checkStringResources :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue :patches:buildAndroid -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `ba9b2e658647821c893db7ac822e0288d4fff2d1076216603853bd57e626de74`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
