# Native Queue — preview 20

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview20.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview20.mpp).

The player settings menu now omits Play next and Play last when its video is already in the temporary queue. Clear temporary queue remains available. Feed menu additions, their grouping and the existing override setting retain their behavior.

An active temporary queue now has a fullscreen playlist entry using YouTube's button layout and normal list/play-arrow icon. It opens the existing native playlist panel through its native entry click handler. The entry reserves space beside the server-provided quick actions, respects Hide quick actions and Hide playlist button, and leaves native orientation/visibility animations in control. Clearing or closing the queue, or opening an account playlist, hides the entry and restores the reserved margin. The registry holds weak references to avoid retaining old player views.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. The patch retains default selection from preview 18. Manager may retain saved selections; select Native temporary queue (experimental) manually if needed.

Phone checks: with a queue active, the gear menu should show Clear without redundant Next/Last additions. Enter fullscreen, show controls, and tap the list/play-arrow icon beside More videos; the queue should open in the native side panel. Check that clearing/closing the queue removes this entry, account playlists retain their original entry, and the fullscreen hide settings still apply. Actual appearance, tapping and rotation need device testing; no Android device or emulator is attached in this environment.

Built from source commit `c2c96d7725de986bc8616004cd9ba83651131958` on `feature/youtube-native-queue`. Preview branding is applied only to artifact metadata; source metadata, README and notices remain unchanged. Research and distribution notes stay outside the contribution source checkout.

Validation: 67 queue tests passed, with 70 passing tests overall and 5 unrelated Jam APK tests skipped. The string checker validated 90,681 strings. Stock APK application, Android DEX/cross-DEX verification and resource compilation passed standalone, with seven related optional patches, and with Native Queue unselected. APK checks cover the fullscreen bind location, native panel opener, hide-setting references, queue teardown refresh and player-menu eligibility after extension shrinking. The combination retains the existing quick-actions margin hook and earlier repeat/shuffle test hooks. The unselected check installs no Native Queue fullscreen hook. The final bundle contains Android classes.dex. Only 21.20.405 is advertised.

SHA256: `2382832ef5b39f8079ce6a11aeb5dedae031395ce6bb81f770cc8f6af985bbcc`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
