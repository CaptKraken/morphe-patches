# Native Queue — preview 23

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview23.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview23.mpp).

This preview removes the additional navigation diagnostics introduced in preview 22, including the descriptor-tracing hook. It retains the player-control registration fix: Next/Previous handlers remain weakly registered when YouTube reuses the overlay across player sessions, while closing the player still resets the queue. Existing baseline error reporting is unchanged. The source tree matches the previously validated registration-fix commit before the diagnostic additions.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; select Native temporary queue (experimental) manually if necessary.

Phone test: turn debug logging off and repeat the same first-queue playback sequence used to encounter the failure. Verify player Next/Previous in portrait and fullscreen with Shuffle and Repeat disabled, checking that Next follows the queue without appending a recommendation. Also close the player, create another queue without restarting the app, and repeat. This build tests whether the registration fix remains effective without the added diagnostics; the exact cause of the reported first-queue failure remains unconfirmed.

Built from source commit `c6f8b8e54333f1b0450d44351cf6837f7f399a66` on `feature/youtube-native-queue`. Preview branding is applied only to artifact metadata; source project metadata and notices remain unchanged.

Validation for this build: all 68 queue tests passed, the Android patch bundle was built, and standalone stock-APK application, Android DEX/cross-DEX verification and resource compilation passed. No Android device or emulator is attached; phone behavior still needs user testing.

Reproduce the stock-APK check with the configured SDK:

```sh
./gradlew :patches:test :patches:validateNativeQueue -PqueueApk=/absolute/path/youtube.apk :patches:buildAndroid
```

SHA256: `52514a8b2e92ca51371c94e6e3fa89dc00cab10275109c5026e5c1f05928f78b`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
