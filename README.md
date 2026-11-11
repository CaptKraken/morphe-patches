# shorts queue actions - test build

[download the mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/shorts-queue-upstream-dev-20261111-test.mpp)

rebuilt from upstream/dev `01b2be43a` on november 11, with the Shorts queue-menu feature.
patches version: **2.2.0**. requires **Patcher 2.3.0 or newer**.

adds Native queue actions to Shorts card three-dot menus. the setting is **off by default**.
this build includes the fix for Shorts being mistaken for playlists. no temporary diagnostics.

## test

1. repatch with this mpp and your usual patches, including **Add to queue**.
2. select **Native queue**, then enable **Show queue actions for Shorts** under **Morphe → Feed**.
3. from a Short card's three-dot menu, use **Play next in queue** with no playback, then with a standalone video playing.
4. with an active Native queue, try **Play next in queue** and **Play last in queue**. playback should stay on the current video.
5. turn the setting off and confirm the extra Shorts menu items disappear. check an ordinary video and playlist menu too.

use Short cards in Home, Search or a channel page. this feature does not add actions to the full Shorts player menu.
the earlier feature build worked in phone testing; this rebased build is ready for another test.

## checks and source

494 tests passed; 5 optional APK tests skipped. string validation and the MPP build passed.
actual APK patching with `--validate` and SDK verification passed on **21.30.216** and **21.44.528** with related patches, including Loop count and Playlist improvements.
21.30.216 also passed with Add to queue unselected. 21.45.82 was not checked.

[complete source archive](shorts-queue-upstream-dev-20261111-source.zip), from feature commit `dd859bcca98ec168f449a077acb4a16118e76b01`.
extract it and use the repository build setup with `./gradlew :patches:buildAndroid`.
base: [upstream/dev commit](https://github.com/MorpheApp/morphe-patches/commit/01b2be43a658d62de7812b9211aa909e55c58c8f).

mpp sha256: `04ff402319c32eb36a51bcff1c8535eb02ef5265fdd38ad1f6aa6861195a0dd6`

## previous builds

- [regional view counts](native-queue-regional-view-counts-a56bc696f-dev11-test.mpp) / [source](native-queue-regional-view-counts-source.zip)
- [Mix menu fix](native-queue-mix-card-url-fix-fc9fe932f-dev11.mpp) / [source](native-queue-mix-card-url-fix-source.zip)
- [mix3 diagnostics](native-queue-mix-diagnostics-mix3-fc9fe932f-dev11.mpp) / [source](diagnostic-source-mix3.zip)
- [mix2 diagnostics](native-queue-mix-diagnostics-mix2-fc9fe932f-dev11.mpp) / [source](diagnostic-source.zip)

see [LICENSE](LICENSE) and [NOTICE](NOTICE).
