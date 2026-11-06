# native queue mix menu fix — test build

[download the mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/native-queue-mix-card-url-fix-fc9fe932f-dev11.mpp)

patches version: **2.1.0-dev.11**. based on [fc9fe932f](https://github.com/CaptKraken/morphe-patches/commit/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4), including the feed-preview startup fix.

fixes target detection for mix cards whose tap command wraps a dialog/watch choice. the queue can read the selected mix's own native card url when the direct watch command is unavailable. no new proto or native hooks; no temporary diagnostics in this build. phone confirmation is pending.

## test

1. repatch with this mpp and your usual patches, including **Add to queue**. select **Native queue**. debug logging is not required.
2. with no playback, open the failing mix's three-dot menu -> **Play next in queue**. confirm it starts a queue containing the mix's videos.
3. add an ordinary video, remove that added video, then add the same mix again through its three-dot menu.
4. with a native queue active, try both **Play next in queue** and **Play last in queue** on the mix. confirm the incoming mix is inserted next or appended as selected.
5. check that an ordinary video's menu still adds only that video. a small regular playlist should still work too.

use the same mix and screen as the failing report first. this fix targets menu actions; the separate normal-card-tap dialog behavior is unchanged. no new diagnostic capture is requested.

## checks and source

58 patch tests and 384 youtube tests passed; 5 optional apk tests skipped. the new regression fails against the old parser and passes with the fix. actual apk patching with strict fingerprint and sdk validation passed on **21.30.216** and **21.44.528**, with the related feed-card, playback and title patches selected. these checks do not replace phone testing.

source: the [base commit](https://github.com/CaptKraken/morphe-patches/tree/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4) plus [fix source archive](native-queue-mix-card-url-fix-source.zip). extract `fix.patch`, apply it to that commit, then run `./gradlew :patches:buildAndroid` with the repository's build prerequisites. the patch changes the existing card parser and adds focused tests.

sha256: `400d92dfa742ed1b7ccb4133a55b42bb82317c8926f77122611225847cf603ac`

## previous diagnostics

existing downloads remain available: [mix3 mpp](native-queue-mix-diagnostics-mix3-fc9fe932f-dev11.mpp) / [source](diagnostic-source-mix3.zip), and [mix2 mpp](native-queue-mix-diagnostics-mix2-fc9fe932f-dev11.mpp) / [source](diagnostic-source.zip). use the fix build above for the new test.

see [LICENSE](LICENSE) and [NOTICE](NOTICE).
