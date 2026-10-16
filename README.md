# Native Queue — preview 21

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview21.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview21.mpp).

This preview completes the branch convention cleanup: clearer video-ID/controller/navigator names, one local-entry namespace and video-ID validator, named buffer/retry limits, and removal of unused discovery references. Queue titles now call Restore original titles' existing marker-removal helper instead of copying its private marker format. The helper's implementation is unchanged. Existing queue UI, navigation, editing, ratings, saving, modes, flyout grouping and fullscreen entry remain included.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; select Native temporary queue (experimental) manually if necessary.

Phone checks: verify titles with Restore original titles enabled and disabled, feed and gear menus, next/previous, reorder/removal, repeat/shuffle, ratings, Save playlist and the fullscreen side-panel entry. Check that closing the player resets the queue and opening a normal account playlist restores native behavior. Actual UI and account operations still require device testing; no Android device or emulator is attached in this environment.

Built from source commit `39237da8f7a5fb09a39e6005cbbaee8a13778863` on `feature/youtube-native-queue`. Preview branding is applied only to artifact metadata; source project metadata and notices remain unchanged.

Validation: all 68 queue tests passed (71 passing tests overall; 5 unrelated Jam APK tests skipped), and 90,681 strings passed the resource checker. Stock APK application, Android DEX/cross-DEX verification and resource compilation passed in standalone, combined and unselected modes. The combined mode applies seven related patches and verifies preservation of existing repeat/shuffle hooks, the quick-actions margin hook and shifted fingerprint indexes. The unselected mode verifies original native controls and the persistent queue path. APK assertions verify shared title-marker removal survives extension shrinking. The final Android bundle contains classes.dex.

All three APK validation modes now live in the committed test setup and share the existing assertion suite. With a stock APK and the configured SDK, run:

```sh
./gradlew :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `ab500cedf9307c3063bd7d98231bbe480d0c8cb7cf317146b857e85df7b5553f`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
