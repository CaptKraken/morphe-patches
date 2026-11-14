# Native queue thumbnail buttons: corrected browse getter

[Download the corrected test MPP](native-queue-thumbnail-playlist-context-getter-fix-20261114-test.mpp) · [Complete matching source](native-queue-thumbnail-playlist-context-getter-fix-20261114-source.zip)

Corrects a verifier-type defect in the new native browse getter after the previous build was reported to crash immediately on launch. The getter now returns the native reader's String separately and explicitly returns null for an empty endpoint. This prevents the endpoint and String register types from merging into Object. Only two Smali instructions differ from the failed build; page detection, card recognition, button placement/actions, settings and requests are unchanged.

Repatch **YouTube 21.30.216** and check that it opens first. Then check both buttons are hidden on ordinary playlist-page rows in portrait and landscape. Home, Search, Subscriptions and normal player recommendations should retain the buttons; cached navigation and hold-to-open menus should still work.

65 patch tests passed, with 5 optional APK-dependent tests skipped; MPP build passed. Unchanged Java had 446 passing extension unit tests in the previous run; these were not rerun for this Smali-only correction. Five new actual APK applications passed strict fingerprint validation, SDK DEX/hierarchy checks and inclusion/coexistence checks: 21.30.216 with Watch later alone, Queue alone, both with overlapping patches, and neither; 21.44.528 combined. A new emitted-DEX return-type analysis rejects the previous build and passes both String/null return paths of all four selected-feature APKs. Validation APKs were deliberately unsigned; signature verification is not claimed.

**Not visually verified:** no attached Android runtime. The previous immediate-launch crash is a tester report; the register-type defect is directly established by bytecode analysis. A phone retest is still needed to confirm startup and UI behavior.

Patches **2.3.0-dev.3**, requires **Patcher 2.3.0 or newer**. Source HEAD `2768fd397dfe02e0e087970f718eb0341281e5c4` plus the three uncommitted changes recorded in BUILD-SNAPSHOT.json. The source archive includes LICENSE/NOTICE and every tracked source file. Build with the repository setup and `./gradlew :patches:buildAndroid`. This delivery does not commit or push the feature branch.

MPP SHA-256: `6f635779cc1c152743f2b1b7124a08c281fb75966633a41a8f4ae4b8ef81d053`

---

# Native queue thumbnail buttons: playlist-page context test

**Failed phone test: this older build crashes on launch. Use the corrected browse-getter build above.**

[Download the test MPP](native-queue-thumbnail-playlist-context-20261114-test.mpp) · [Complete matching source](native-queue-thumbnail-playlist-context-20261114-source.zip)

Hides the entire Watch later/Native queue thumbnail button container on ordinary playlist pages using the card's ancestor fragment and its native browse endpoint. Reuses existing browse discovery with one typed getter; no new fingerprint or global page flag. Card detection, video-ID lookup, long-press menus and the individual actions are unchanged.

Repatch **YouTube 21.30.216** and check that both buttons disappear on playlist rows in portrait and landscape, including Watch later and public playlists. Check Home, Search, Subscriptions and normal watch-page recommendations still have working buttons. Also check cached back navigation and hold-to-open menus.

65 patch tests and 446 extension tests passed; 5 optional APK-dependent tests skipped. MPP build passed. Five actual APK patch applications passed strict fingerprint validation and SDK DEX/hierarchy checks: 21.30.216 with Watch later alone, Queue alone, both with overlapping patches, and both unselected; 21.44.528 with the combined selection. Native getter contracts and existing Playlist improvements callbacks were checked. Validation APKs were deliberately unsigned; signature verification is not claimed.

**Not visually verified:** no attached Android runtime. This is a phone-test candidate; the earlier global page-state attempt failed phone testing and is not included.

Patches **2.3.0-dev.3**, requires **Patcher 2.3.0 or newer**. Source HEAD `2768fd397dfe02e0e087970f718eb0341281e5c4` plus the three uncommitted changes recorded in the source archive's BUILD-SNAPSHOT.json. The archive includes every tracked source file and LICENSE/NOTICE. Build with the repository setup and `./gradlew :patches:buildAndroid`. This delivery does not commit or push the feature branch.

MPP SHA-256: `2c6bd23696907bc03164e63d5e403542b7ac7a3243ee833d76d572949b2ffb73`

---

# native queue thumbnail buttons - swipe anchor

[download the test mpp](native-queue-thumbnail-button-swipe-anchor-21.30.216-test.mpp)

both buttons now share the native thumbnail's overlay, using thumbnail-local coordinates. the thumbnail and its moving ancestors carry them during swipe/reveal. touch targets follow the current image position, and pending long presses cancel if the image moves. row/column layout is retained. native image unmount/rebind removes our drawables before pooling/reuse. no extra container, animation loop or native swipe hook.

repatch **21.30.216** and repeat the Watch Later swipe in portrait and landscape. swipe open and back, then tap/hold both buttons at their new positions. check the exposed native action area, normal taps, scrolling/recycling, rotation, single-button mode and larger-card column too.

433 extension tests and 66 patch tests passed (5 optional APK tests skipped); MPP built. exact final216 combined and Watch Later-only/queue-unselected APKs passed fingerprint validation, SDK checks and native URL argument-type regression checks. Kotlin/native hooks/settings/resources unchanged; 528 not rerun for this Java-only step. uses existing public ViewOverlay/getLocationOnScreen APIs at minSdk26.

**not visually verified**: no connected Android runtime. user reports the previous compact-row build looks good; supplied swipe screenshots show its stale root-overlay positions, not proof of this new anchor.

patches **2.3.0-dev.2**, requires **Patcher 2.3.0 or newer**. base upstream/dev `0306f45b1`. two Java files differ from the previous build. [complete matching source](native-queue-thumbnail-button-swipe-anchor-20261113-source.zip) includes LICENSE/NOTICE and BUILD-SNAPSHOT.json. build with repository setup and `./gradlew :patches:buildAndroid`.

mpp sha256: `afbf04ab6ca6b3bfb517978b2a1e73671ea757480d2d8847fbab927df351be1a`

---

# native queue thumbnail buttons - compact row

[download the test mpp](native-queue-thumbnail-button-compact-row-21.30.216-test.mpp)

when two stacked buttons would crowd a compact thumbnail, Watch Later and queue now appear side by side at the top. larger thumbnails retain the vertical stack. uses the actual thumbnail size, with space below for native badges; no page/screen detection. matching touch targets and RTL ordering included. one Java file differs from the startup-corrected build; the confirmed startup register correction is retained.

repatch **21.30.216** and try the same portrait playlist pages with both buttons enabled. check each button's tap/hold action and that the duration stays visible. then check larger Home/Search/Subscriptions cards, each button alone, scrolling and rotation.

433 extension tests and 66 patch tests passed (5 optional APK tests skipped); MPP built. exact final216 combined APK passed fingerprint validation, SDK checks and the native argument-type regression check. native hooks/settings/resources unchanged; 528 was not rerun for this layout-only change.

**not visually verified**: no connected Android runtime. earlier corrected build opens according to user testing; these screenshots identify its compact-thumbnail overlap, not proof of this new row layout.

patches **2.3.0-dev.2**, requires **Patcher 2.3.0 or newer**. base upstream/dev `0306f45b1`. [complete matching source](native-queue-thumbnail-button-compact-row-20261113-source.zip) includes LICENSE/NOTICE and BUILD-SNAPSHOT.json. build with repository setup and `./gradlew :patches:buildAndroid`.

mpp sha256: `d00486a67e9df7a70b6f46f1850661898880e955708737a3227bd8c3699de652`

---

# native queue thumbnail binding - startup correction

[download the corrected test mpp](native-queue-thumbnail-binding-startup-fix-21.30.216-test.mpp)

**use this instead of the previous shared-binding build**, which was reported to crash at startup. found a verifier-type defect in its native helper: the image-source object and URL shared a register, so Android could see Object where String was required. the source now uses a separate register, keeping the callback URL String/null on every branch. only three production Smali lines changed; no detection, requests, settings or UI changes.

added a focused argument-type check to the existing APK verifier. it rejects the old build's actual emitted DEX and passes this build on **21.30.216**, **21.44.528**, and 216 with Watch Later selected but Add to queue unselected. fingerprint validation, SDK checks and 66 patch tests passed (5 optional APK tests skipped). unchanged Java and strings were not retested for this Kotlin-only fix.

repatch and **check that YouTube opens first**. then enable Native queue / Show queue button on thumbnails and test landscape Search, portrait, Home and Subscriptions. try Watch Later too if included.

**not visually verified**: no connected Android runtime here. the code defect is confirmed; phone launch must confirm whether it resolves the reported crash.

patches **2.3.0-dev.2**, requires **Patcher 2.3.0 or newer**. base upstream/dev `0306f45b1`. [complete matching source](native-queue-thumbnail-binding-startup-fix-20261113-source.zip) includes LICENSE/NOTICE and BUILD-SNAPSHOT.json. build with repository setup and `./gradlew :patches:buildAndroid`.

mpp sha256: `4f54e0667fef42d9dc552fb971871ac025bd0bd0ef465cd1a4e856ced175bb72`

---

# native queue thumbnail button - shared native image binding

[download the mpp](native-queue-thumbnail-binding-21.30.216-test.mpp)

both Watch Later and Native queue buttons now find the mounted thumbnail through YouTube's own image source. position comes from the actual ImageView, including horizontal cards. removes the failed Search width workaround and guessed thumbnail-top placement. no extra requests, diagnostics or Niconico changes.

repatch **21.30.216** with **Add to queue**, select Native queue, enable **Show queue button on thumbnails**, then restart. test **Search in landscape first**, followed by portrait Search, Home and Subscriptions. try tap/hold, scroll away and back, rotate, and check the card playing a feed preview hides its buttons. include **Feed video actions** to test Watch Later alongside queue; also test each button alone.

**not visually verified**. no connected Android device here. these checks establish native discovery/injection, not that the reported Search UI failure is fixed.

433 extension tests and 66 patch tests passed; 5 optional APK tests skipped. 198835 strings verified and MPP built. exact MPP passed `--validate` and Android SDK checks on **21.30.216** and **21.44.528** with related patches, plus 216 Watch Later without Add to queue, and count-only selection without either thumbnail action. native bind/unbind registers and fallthrough inspected in both APKs. count-only selection does not install the new image hooks. 21.45.82 unrun.

patches **2.3.0-dev.2**; requires **Patcher 2.3.0 or newer**. source base upstream/dev `0306f45b1`. [complete matching source](native-queue-thumbnail-binding-20261113-source.zip) contains all tracked source plus the new fingerprint file, LICENSE, NOTICE and BUILD-SNAPSHOT.json. build with the repository setup and `./gradlew :patches:buildAndroid`.

mpp sha256: `d8cbf1836f334376eadad3f89de7915d34259f1cf2760aeb19b5f2818070f5e7`

---

# native queue thumbnail button - Search landscape test

[download the mpp](native-queue-thumbnail-button-search-landscape-21.30.216-test.mpp)

candidate correction for missing thumbnail buttons in landscape Search. keeps the original side-thumbnail matcher for thumbnails up to half the row width; when Feed video actions are enabled, wider thumbnails also qualify if a separate visible text host is beside the image. this removes a strict width assumption without increasing the percentage limit. other size/aspect/inset/row/menu checks remain.

## test

repatch **21.30.216** with **Add to queue**, select Native queue, enable **Show queue button on thumbnails**, restart, and repeat the same Search in landscape. try tap/hold, then Home and Subscriptions again. optionally include **Feed video actions** and test Watch Later too. individual playlist-row buttons remain enabled by the earlier horizontal-layout change.

**not visually verified**: this targets a concrete detection constraint suggested by the screenshot; the exact runtime rejection and success still need phone confirmation. earlier phone results: Home/Subscriptions horizontal work; Search portrait works; Search landscape is missing buttons.

433 extension tests and 66 patch tests passed; 5 optional APK tests skipped. English string checks and MPP build passed. exact MPP passed Desktop CLI `--validate` and Android SDK verification on **21.30.216** and **21.44.528** with related patches, plus **21.30.216** with Add to queue unselected. touch injection is unchanged. latest **21.45.82** not checked.

one Java helper file differs from the previous horizontal MPP; queue actions/settings/preview logic are unchanged. with thumbnail actions disabled, other feed features retain their original matching. when combined, they can resolve the same newly recognized valid row.

patches **2.3.0-dev.2**, requires **Patcher 2.3.0 or newer**. base upstream/dev `0306f45b1`. no diagnostics or Niconico changes.

[complete matching source archive](native-queue-thumbnail-button-search-landscape-20261113-source.zip). BUILD-SNAPSHOT.json binds the eight uncommitted feature files. build with `./gradlew :patches:buildAndroid` using the repository setup.

mpp sha256: `b67d92fcef0c031b019e01616502e592cf99b21b771dcdeed149e8856ea075fb`

see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

# native queue thumbnail button - horizontal cards test

[download the mpp](native-queue-thumbnail-button-horizontal-21.30.216-test.mpp)

adds the thumbnail-left/details-right layout (mirrored in RTL) to both queue and Watch Later buttons, using the existing side-thumbnail detector and thumbnail bounds. tap/hold, queue insertion, settings and preview hiding are unchanged from the first phone-tested build.

## test

1. repatch **21.30.216** with **Add to queue**. select Native queue and enable **Show queue button on thumbnails**, then restart.
2. rotate to landscape and try a card with its thumbnail beside the video info. tap queues last; hold queues next. try the Swap setting too.
3. optionally include **Feed video actions** and enable its Watch Later thumbnail button. try both buttons, then each alone.
4. rotate back to portrait, scroll away/back and try a feed preview. check the buttons stay on the correct thumbnail and only its playing preview hides them.

ordinary horizontal Litho video cards are supported by this change; native compact/history views, Shorts and playlist/Mix collection-card buttons remain outside scope.
**not visually verified**: horizontal placement, rotation and gestures await phone testing. the user confirmed tap/hold and adding while a playlist is active on the previous full-width-only build.

433 extension tests and 66 patch tests passed; 5 optional APK tests skipped. English strings and MPP build passed. Desktop CLI `--validate` and Android SDK verification passed on **21.30.216** and **21.44.528** with related patches, and on **21.30.216** with Add to queue unselected. The shared touch injection is unchanged. Latest experimental **21.45.82** was not checked.

patches version **2.3.0-dev.2**; requires **Patcher 2.3.0 or newer**. base upstream/dev `0306f45b1`. no diagnostics or Niconico changes. one Java file differs from the prior phone-tested source.

[complete matching source archive](native-queue-thumbnail-button-horizontal-20261113-source.zip). source snapshot is in BUILD-SNAPSHOT.json. build with `./gradlew :patches:buildAndroid` using the repository setup.

mpp sha256: `399a54e72f518b1b8570c3b0f091813fc2bf11506a58756736384c392269cf25`

see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

# native queue thumbnail button - test build

[download the mpp](native-queue-thumbnail-button-21.30.216-test.mpp)

built from upstream/dev `0306f45b1` with the thumbnail queue-button feature.
patches version: **2.3.0-dev.2**. requires **Patcher 2.3.0 or newer**.
no temporary diagnostics or Niconico feature changes.

## test

1. repatch YouTube **21.30.216** with **Add to queue** included.
2. select **Native queue** under **Morphe -> Feed**, enable **Show queue button on thumbnails**, then restart YouTube.
3. tap the thumbnail queue button to play last, or hold it to play next. with no playback, either creates the first queue.
4. enable **Swap queue thumbnail button actions** to reverse tap/hold.
5. optionally include **Feed video actions** and enable **Show Watch later button on thumbnails**: queue appears below Watch Later, or in its position when Watch Later is off.
6. check scrolling, preview playback and returning from previews. the button hides only on the card currently playing a feed preview. check adding with a standalone video, ordinary playlist and active queue.

this uses the existing full-width feed video-card layout. compact lists, horizontal tablet rows, Shorts and playlist/Mix cards are outside this first test build.
**not visually verified**: phone layout, gestures and playback still need testing.

## checks and source

433 extension tests and 66 patch tests passed; 5 optional APK tests skipped.
English string validation and MPP build passed. Desktop CLI `--validate` and Android SDK verification passed on **21.30.216** (queue alone, related patches together, and queue unselected) and **21.44.528** with related patches. Each has one shared thumbnail touch callback. Latest experimental **21.45.82** was not checked.

[complete matching source archive](native-queue-thumbnail-button-20261113-source.zip).
source includes the eight uncommitted feature files and BUILD-SNAPSHOT.json; it excludes local workflow files and generated build outputs.
extract it and use the repository build setup with `./gradlew :patches:buildAndroid`.

mpp sha256: `28b153d3ef77cf465c578f3e917fcc98b3e833101daf318866381e1b26da14c4`

see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

# niconico comments in PiP - revised overlay test

[download the revised mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/niconico-comments-pip-overlay-fix-21.30.216-test.mpp)

this replaces the first PiP test below. it restores the original comments attachment
in the regular player. only PiP moves the same comments view to an Android view overlay
on the player host, with explicit sizing and restoration when returning.

1. repatch with **Niconico comments** selected, enable its main switch and restart youtube.
2. confirm its player button is on, then test comments in the regular player first. landscape and portrait are supported.
3. enable **Show in picture-in-picture** under **Morphe → Player → Niconico comments** and enter PiP.
4. try resizing, pause/resume, returning to the player and entering PiP again.

the cause of the first blank result is not confirmed. this corrects a native layout
path where the previous custom container may have no usable bounds. phone testing
is still needed; no change to comment fetching and no diagnostics added.

based on upstream/dev `93d0d701c`, patches **2.3.0-dev.1**, requires **Patcher 2.3.0+**.
52 Niconico and 64 patch tests passed; 5 optional APK tests skipped. strings and MPP
build passed. actual APK application with `--validate` and SDK verification passed
on **21.30.216** and **21.44.528** with related player/overlay patches selected.

[matching complete source](niconico-comments-pip-overlay-fix-20261112-source.zip)
includes the uncommitted feature snapshot, LICENSE and NOTICE.
experimental fork build; see [#4718](https://github.com/MorpheApp/morphe-patches/issues/4718).

mpp sha256: `8d748bc382ef8a203ae5ff67595d879b6fa3e18f4966df1d6a01dc6ee5bf116e`

---

# niconico comments in picture-in-picture - test build

[download the mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/niconico-comments-pip-21.30.216-test.mpp)

experimental fork build for [#4718](https://github.com/MorpheApp/morphe-patches/issues/4718),
based on upstream/dev `93d0d701c`. patches **2.3.0-dev.1**; requires **Patcher 2.3.0 or newer**.

1. repatch with **Niconico comments** selected.
2. enable Niconico comments and restart youtube if newly enabled.
3. under **Morphe → Player → Niconico comments**, enable **Show in picture-in-picture** (default off).
4. open a video where scrolling comments already appear, then enter PiP.
5. try resizing, pause/resume and returning to the player. repeat with **Show in portrait** off, then with the PiP toggle off.

reuses the existing comments renderer and youtube's native custom-views container.
no diagnostics added. actual phone rendering and readability still need testing.

52 Niconico tests and 64 patch tests passed; 5 optional APK tests skipped.
203281 strings checked and MPP build passed. separate actual APK patching with
`--validate` and SDK verification passed on **21.30.216** and **21.44.528**, together
with SponsorBlock, captions, overlay opacity, PiP button and background playback.

[complete matching source](niconico-comments-pip-20261112-source.zip) includes the
uncommitted feature snapshot on top of the stated upstream base, plus LICENSE and NOTICE.
build with the repository setup and `./gradlew :patches:buildAndroid`.

mpp sha256: `2bc2e38465ccbbccfa521077463f1f941441216ba161d4163072d1fefa3b3303`

---

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
