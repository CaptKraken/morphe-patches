# Native Queue — preview 22

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview22.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview22.mpp).

This preview fixes a navigation lifecycle bug: closing the player cleared our weak Next/Previous handler registrations, even though YouTube can reuse those controls without registering them again. A later queue could therefore fall through to a recommendation and append it as a newly opened video. Registrations now survive queue resets, remain weak, and only intercept navigation while a temporary queue is active. The queue and playback references still reset on player close. This fixes a confirmed code path that can cause the issue, but does not establish the cause of the reported first-queue failure. Focused debug traces now record handler recognition, native availability, the chosen descriptor and queue membership immediately before loading, committed-video appends, and player-session resets. Diagnostic reads are lazy and guarded so they cannot interrupt playback.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; select Native temporary queue (experimental) manually if necessary.

Phone investigation: enable debug logging, clear the log, create the first queue after app launch, and reproduce a failed Next tap with Shuffle and Repeat disabled. Check both portrait and fullscreen and export the log immediately after the failure. The trace distinguishes a missed interception from a native navigator returning a recommendation.

Phone regression check: create a queue with three videos and verify player Next/Previous, then close the player completely. Without restarting the app, open a video and create another queue. Verify Next/Previous again in portrait and fullscreen, with Shuffle and Repeat disabled. Repeat several times, checking that Next selects the queued video without appending a recommendation and Previous stays available when there is an earlier queue item. Closing the player must still reset the queue; ordinary account playlists must retain their native controls.

Built from source commit `ccd8c7d7b4f0e1f05d3242874f273c583ec207a1` on `feature/youtube-native-queue`. Preview branding is applied only to artifact metadata; source project metadata and notices remain unchanged.

Validation: all 68 queue unit tests passed (71 passing tests overall; 5 unrelated Jam APK tests skipped), and 90,681 strings passed the resource checker. The registration-lifetime APK regression assertion failed on the preceding code and passed after the fix. Additional APK assertions verify the descriptor trace occurs after native commit and before the single player load. Stock APK application, Android DEX/cross-DEX verification and resource compilation passed in standalone, combined and unselected modes. The combined mode verifies coexistence with seven related patches; the unselected mode verifies native controls and the persistent queue path. The final Android bundle contains classes.dex. No Android device or emulator is attached; cloud checks do not establish actual on-device behavior.

Reproduce the APK checks with a stock APK and the configured SDK:

```sh
./gradlew :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `4d42ef70ae9d4cc759da429b322fd11751d5db57c2d5bb677b351491d5f3dc2f`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
