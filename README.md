# native queue regional view counts - test build

[download the mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/native-queue-regional-view-counts-a56bc696f-dev11-test.mpp)

patches version: **2.1.0-dev.11**. requires a manager with **Patcher 2.3.0-dev.2 or newer**, such as [Morphe Manager 2.5.0-dev.3](https://github.com/MorpheApp/morphe-manager/releases/tag/v2.5.0-dev.3). Manager 2.4.0 bundles Patcher 2.2.0 and cannot load this build.

includes the tested Mix menu fix and the feed-preview startup fix. this new change preserves the full app locale in queue metadata and Mix requests. YouTube formats the counts: a live comparison returned `1B` for `en` and `108 crore` for `en-IN`. no custom number formatter or new settings were added. no temporary diagnostics in this build.

## test

1. repatch with this mpp and your usual patches, including **Add to queue**. select **Native queue**. debug logging is not needed.
2. start a fresh queue and add a video whose feed card shows lakh/crore. compare the count units in the queue with the feed.
3. try adding a Mix too. where its rows include view counts, they should use the same regional format.
4. confirm regular playlist additions and ordinary single-video additions still work.

keep the app locale that produced the reported mismatch. phone confirmation is pending; no new diagnostic capture is requested. imported ordinary playlists still use YouTube's own native page metadata.

## checks and source

59 patch tests and 386 youtube tests passed; 5 optional apk tests skipped. tests cover full regional request fields independently of the system locale, unchanged player/edit request contexts, and rejecting an in-flight result after the app language changes.

actual apk patching with strict fingerprint and sdk validation passed on **21.30.216** and **21.44.528**, with related feed-card, playback and title patches selected. these checks do not replace phone testing.

source: the [public base commit](https://github.com/CaptKraken/morphe-patches/tree/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4) plus [complete source archive](native-queue-regional-view-counts-source.zip). the archive includes both the Mix target fix and this regional locale change, so it can be applied to the public base even before the feature branch is pushed. extract `complete-source.patch`, apply it, then run `./gradlew :patches:buildAndroid` with the repository's build prerequisites.

sha256: `cbd58f079d86aa5248cb1ca60d3751a9c9d98934c1110b50bec43307de60598f`

## previous builds

- [Mix menu fix](native-queue-mix-card-url-fix-fc9fe932f-dev11.mpp) / [source](native-queue-mix-card-url-fix-source.zip)
- [mix3 diagnostics](native-queue-mix-diagnostics-mix3-fc9fe932f-dev11.mpp) / [source](diagnostic-source-mix3.zip)
- [mix2 diagnostics](native-queue-mix-diagnostics-mix2-fc9fe932f-dev11.mpp) / [source](diagnostic-source.zip)

see [LICENSE](LICENSE) and [NOTICE](NOTICE).
