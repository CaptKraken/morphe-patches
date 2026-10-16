# Native Queue — preview 29

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview29.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview29.mpp).

This preview fixes a navigator ownership defect. The previous implementation trusted the last watch-response subscriber, although the native player can already have replaced that navigator. Player Next/Previous now resolve the live navigator from YouTube's actual player implementation and synchronize its response before committing navigation. Availability checks follow that same instance. Shuffle/Repeat requests follow the current player, preserve pending local intent while its response loads, and do not apply modes to its retired predecessor.

Callbacks from a retired navigator cannot claim the active queue. A cached response received during a replacement navigator's constructor is deferred until the player installs it, and accepted only if it is still that navigator's current response. Real native commit failures remain reported. Normal playback outside an active temporary queue retains its native controls.

The supplied logs establish an attachment failure and a later successful playback request followed by the generic queue-update toast. They do not expose the precise native instance identities for that device transition. The ownership defect is demonstrated separately by a controlled before/after test: preview 28 commits Previous against a retired navigator, while this implementation commits against the player's replacement. This is a correction to a concrete code path, not confirmation that every reported device error is resolved.

Validation:

- All 77 YouTube queue tests and formatting checks for 90,681 resource strings passed.
- The actual extension handlers pass controlled tests for Next/Previous handoff, retired responses, constructor callbacks, newer-response ordering, Shuffle/Repeat off on the last item, response-free loading transitions, recovery, and real native commit error reporting. Native access, scheduling and playback use test doubles in this handler test.
- The emitted bridge getters execute against actual native player class fields from patched DEX for both playback implementations. Native objects are allocated without executing Android player constructors or playback.
- The supplied APK's actual navigation and shuffle/repeat algorithms execute in a separate research harness, including 200 shuffle on/off cycles at first/last positions. The storage interface is controlled; this does not execute Android playback.
- Standalone, seven-related-patch combined, and Native Queue-unselected APK validation passed with Android SDK DEX/cross-DEX verification and resource compilation. The Android bundle was built. No Android device or emulator is attached.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; ensure Native temporary queue (experimental) is selected.

Useful device checks are repeated player Next/Previous in portrait and fullscreen; Shuffle/Repeat on and off while the last video is still playing; navigation after those toggles; and queue behavior through PiP and player close/reopen. No further log capture is required for the current investigation.

Built from source commit [`62b511ebff298b22e234e43f46f645f523bd97b3`](https://github.com/CaptKraken/morphe-patches/commit/62b511ebff298b22e234e43f46f645f523bd97b3) on `feature/youtube-native-queue`. Preview branding changes only artifact metadata; source metadata and notices remain unchanged.

Reproduce with the configured JDK and Android SDK:

```sh
./gradlew :patches:test :patches:checkStringResources :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue :patches:buildAndroid -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `aef84e9530cb1ac5a7b9c31793bff859d411f01c8305bc28f7bfbdc9f0a9edbf`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
