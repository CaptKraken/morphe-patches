# Native playlist queue — incremental phone-test builds

Patch an untouched **YouTube 21.20.405 APK** with the selected local MPP source. Native playlist queue stays selected by default. Each bundle includes earlier features; each feature has its own source commit. Phone checks remain pending until performed on a device.

## PoC48 — create the first queue in the miniplayer

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc48-miniplayer-queue-start.mpp) · [Source 4c2e7b533](https://github.com/CaptKraken/morphe-patches/commit/4c2e7b533d57e042e3543300d5932733bb788cc3)

Play next is available with no player or a home feed preview. It opens the selected video using YouTube's watch intent with the native start-minimized flag, then attaches a one-item queue only after the matching native player/service is ready. Play last stays hidden until a queue exists. Browsing stays open, with the existing added toast and no queue-panel expansion. Existing regular-player queue additions/account-playlist/Shorts guards retained. Exact-video fullscreen exception preserves this miniplayer start while subsequent ordinary videos follow user settings. No custom UI/network/auth or unrelated Save changes; PoC47 saving remains the user-confirmed baseline.

Validation:24tests passed/5 unrelated Jam APK skips; selected combined20-related (1m59s) and unselected (1m41s) real21.20.405 SDK12DEX/hierarchy/resources passed. Actual APK ordering regression catches rejected early-injection code; final shared callback preserves native descriptor construction and existing video filters.25Save/New methods stock-exact and original watch dispatch tail matches unselected. Final lifetime-only helper change tests/build31s; currentMPE/36queueKotlin+sharedAPI/CRC/defaulttrue and mandatory gate passed. **Phone miniplayer/browsing flow pending.**

Test no player and homepreview: Play next visible, Last hidden; selected video starts a one-item queue in miniplayer and retains browsing. Then both actions appear/order correctly. Check fullscreen modes, cancellation/rapid opens/clear/Shorts/account playlists, ordinary video taps and PoC47 Save/Undo/New.

SHA-256: `54afed2f1e5206105c9a32ace00456246aead68fdfa7445776b09966fefc180f`

## PoC47 — queue picker regression and native New playlist dialog

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc47-native-new-dialog.mpp) · [Source e20618655](https://github.com/CaptKraken/morphe-patches/commit/e206186550dd9928704e62a9501819e38f0bb029)

PoC46 exposed a header command that our binder could not handle and closed the entire picker. Queue-only New playlist now dispatches YouTube's verified list-capable CreatePlaylistDialogEndpoint with every queued ID. Clears all three native click slots before setting one navigation action, avoiding duplicate creation. Original header text/style, picker and existing destination Save/confirmation/Undo retained. YouTube handles sign-in, name/privacy, native playlist/create and callbacks. Uses the native default create dialog; appearance may differ from the server-rendered variant. Ordinary player/row/account Save untouched. No new hooks, customUI/network/auth or playback changes.

Validation:23pass/5unrelatedJamAPKskips; native action-slot regression cases; combined20-related real21.20.405 SDK12DEX/hierarchy/resources and MPP build passed2m20s.25originalSave/New/auth/query/serializer/Snackbar methods stock-exact. Final MPP/currentMPE/all36Kotlinpayloads/CRC/defaulttrue and mandatory commit gate passed. No new unselected run for unchanged hooks. **Phone/backend result pending.**

Test: queue Save picker opens; existing Save toast/Undo still works; THREE distinct IDs -> New native dialog -> created playlist all3/order. Include singlequeue/playerNew, cancel/rotation and continuous playback. Exact server command shape from PoC46 failure was not captured; no wrapper-type diagnosis claimed.

SHA-256: `9424abaf758e9f2ec8a95ca056af49de44e01bd91f03e696e485dccba130f1af`

## PoC46 — complete queue when creating a New playlist

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc46-new-playlist-whole-queue.mpp) · [Source 2e8e14957](https://github.com/CaptKraken/morphe-patches/commit/2e8e14957cd59cc021cf221bb6b577ba96fdc8e0)

Corrects the native New playlist header button field from 3 to 2. The wrong field caused queue binding to skip the actual button and leave its seed-only command unchanged. The existing binding now supplies all queued IDs to YouTube's original name/privacy dialog and playlist/create list. No new native hooks, custom UI/network or ordinary Save changes. PoC45 existing-playlist confirmation and Undo were user-confirmed working and are retained.

Validation: native wire-format regression failed before the correction and passed afterwards; full suite23pass/5 unrelated Jam APK skips. Combined20-related real21.20.405 SDK12DEX/hierarchy/resources and MPP build passed2m29s.25 original native Save/New/auth/query/serializer/Snackbar methods match stock. MPP/currentMPE/all36Kotlinpayloads/CRC/defaulttrue and mandatory commit gate passed. No fresh unselected run for unchanged hooks. **New playlist phone result pending.**

Test THREE distinct queued videos -> New playlist contains all3 in order. Also single queue/New, ordinary player/New, existing Save confirmation/Undo, cancellation/rotation and continuous playback.

SHA-256: `ccf2bf7ab6456d754a07b8bf0c545a5dad3767c10190176596edb830d1296238`

## PoC45 — existing-playlist save confirmation and Undo

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc45-save-confirmation-undo.mpp) · [Source 52dd00d1f](https://github.com/CaptKraken/morphe-patches/commit/52dd00d1f64f6fb8fe4f567b6995a5fb0beb8432)

Queue header saves to existing playlists now append YouTube's native Saved to [playlist] confirmation after a successful native response. Undo uses only server-returned newly added entry IDs, preserving older occurrences. Unsafe/missing entry data omits Undo. Ordinary player/per-row/account Save and new-playlist creation are unchanged.

Validation:23 tests passed/5 unrelated Jam APK skips; combined20-related and final unselected real21.20.405 SDK12DEX/hierarchy/resources checks passed; strings90674 clean.18 original native methods match stock. Final MPP/currentMPE/all36Kotlinpayloads/CRC and mandatory commit gate passed. **Phone confirmation/Undo pending.**

Test existing playlist success message and Undo, including a destination with an older occurrence. Check ordinary Save, failed/cancelled save and uninterrupted playback. **Known separate issue: New playlist currently saves only the first queued video; user requested testing this confirmation before fixing New.** PoC44 existing-playlist saving was user-confirmed working; New is now confirmed incomplete.

SHA-256: `05c7f0a78caed1118d8afffd4ebe7c7373a9965ecd6d7749c6197325db4c0743`

## PoC44 — original picker request, batch destination actions

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc44-stock-save-picker.mpp) · [Source 1b7f38b91](https://github.com/CaptKraken/morphe-patches/commit/1b7f38b9198ef02bdb65fe25f6252f3646a5fc33)

Removes the batch picker request override that returned HTTP400 before the picker opened. YouTube's original single-video picker request and initializer now match stock. The complete queue snapshot is retained for the later native Add and New playlist commands. For multiple queued videos, playlist destinations use YouTube's native tap-to-add-and-close rows, avoiding a checkbox/removal state that describes only the seed video. Single-video queues and ordinary player/per-row/account Save retain original behavior. No custom network/auth/UI or playback changes.

Validation: 23tests passed/5 unrelated Jam APK skips; actual21.20.405 combined19related (2m12s) and unselected+finaltests/MPP (1m51s) SDK12DEX/hierarchy/resources passed. Eleven native Save/auth/serializer/error/initializer methods match stock exactly. MPP/currentYouTubeMPE/all34Kotlinpayloads/CRC/defaulttrue and mandatory commit gate passed. **Authenticated device Save remains unverified.**

Test: picker opens without400; THREE distinct queued videos saved to existing and New playlists each contain all3/order. Include a fresh destination and one already containing the first video; single queue/ordinary player Save; cancellation/rotation/continuous playback. Multi-video rows add and close rather than expose membership checkboxes.

SHA-256: `18a0583a042518251922045ae2f095617b6bd92d206a3bc84ff90152a1d005e8`

## PoC43 — FAILED: HTTP400 before picker opens

**User confirmed HTTP400 appears before choosing a destination. The batch picker query override is removed in PoC44.**

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc43-save-destination-commands.mpp) · [Source 51ad54a79](https://github.com/CaptKraken/morphe-patches/commit/51ad54a7918184f4a7b7bbcaab46efd0bcaf631a)

Replaces the transient endpoint-object selection marker with serialized, extension-owned local command data, then retains the snapshot in native fragment arguments. In addition to the native batch query, the queue picker now explicitly binds the complete list to native destination Add and New playlist commands. An unsupported/partial destination action follows YouTube's original error/close path instead of silently saving one item. Native UI (legacy list/checkbox picker), authentication, edit/create dispatch and playback remain native. Ordinary player/per-row/account Save has no selection marker and passes through unchanged. Includes earlier Play last and player Save restoration.

Validation: 23 tests passed, 5 unrelated Jam APK skips; actual 21.20.405 combined (19 related patches) and unselected SDK 12-DEX/hierarchy/resources checks passed. Ten ordinary Save/auth/serializer/error methods match stock. Final MPP/default=true/current YouTube MPE/all34 Kotlin payloads/CRC and mandatory commit gate passed. **Authenticated device saving remains unverified. PoC42 failed the phone test; this is the next test candidate.**

Please check THREE distinct queued videos -> existing and New playlists each contain all three in order. Also check ordinary player Save, partial membership/uncheck, cancel/rotation, and uninterrupted playback. Watch Later may have separate backend restrictions.

SHA-256: `9665746d84089f7378868e8a423946fcb15146dea1832dcdf9b9bba8b6231def`

## PoC42 — FAILED phone test: only one of three saved

**User confirmed this bundle saves only one video from a three-video queue. Do not treat it as working whole-queue Save.**

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc42-native-batch-save.mpp) · [Source 0c5a46e74](https://github.com/CaptKraken/morphe-patches/commit/0c5a46e745abb8181a690e504ad6e92a90e71d01)

Queue header Save now opens YouTube's **native batch picker (older list/checkbox layout)** and sends all queued videos as separate IDs in its native playlist/get_add_to_playlist request. Its original destination membership, Save/New playlist commands, authentication and callbacks remain native. This replaces PoC41's first-video-only diagnostic. Ordinary player/per-row/account Save has no queue marker and keeps its original request. No CSV IDs, generic row replacements, final edit/create interception or custom account client. Includes Play last and playerSave restoration.

The snapshot is attached only after validating the exact active queue header, then copied to that native fragment's saved arguments. Native request construction uses that snapshot; later queue clear/reorder and rotation cannot change the selected IDs. Playback is not modified.

Validation:23tests passed/5unrelatedJamAPKskips; actual21.20.405 combined19related and unselected SDK12DEX/hierarchy/resources passed. Queue marker/argument/request-list bridge and unselectedabsence verified. Eleven native Save/auth/requestserializer/callback methods match stock. Final MPP/defaulttrue/exactcurrentYouTubeMPE/all34Kotlinpayloads/CRC and mandatory commit gate passed. **No authenticated phone Save test performed here.**

Please test3different queued videos: existing playlist and New must receive every ID in order. Also test partial membership, normalplayer/per-rowSave, cancel/rotation/clear/reorder while picker open and uninterrupted playback. Watch Later can have independent YouTube backend limitations. Modern batch-picker layout remains deferred.

SHA-256: `b6b65331702a9d65040a9ad8e2a3a520c37343f81592cc1a820e5d9a6a27e811`

## PoC41 — Play last in native queue

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc41-play-last.mpp) · [Source f08ec6d1c](https://github.com/CaptKraken/morphe-patches/commit/f08ec6d1cd1273100ecdffedbc62b7810226dc06)

Adds Play last below Play next in eligible video menus, using YouTube's queue-last icon and existing injected menu layout. Appends at end while retaining current selection/order/repeat; shares existing queue publication, metadata, toast and no-panel-expansion behavior. Preserves queue/account/Shorts/current-video/menu guards. Includes PoC40 removal of shared Save modifications. **Queue header Save still saves only the first video; whole-queue saving remains unresolved.**

Validation:23tests passed/5unrelatedJamAPKskips;strings/build/defaulttrue passed33s;CRC/exactcurrentYouTubeMPE/all32Kotlinpayloads/gate passed. No freshAPKrun for unchanged nativehooks; PoC40combined/unselectedAPK checks remain applicable. No phone/emulator; verify append order and uninterrupted playback plus playerSave restoration.

SHA-256: `46227ba62bad974fdd3a3dd83ca95e45780c1bf2296437db4cf81644bdbca1f4`

## PoC40 — restore native player Save paths

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc40-restore-player-save.mpp) · [Source 549824a7a](https://github.com/CaptKraken/morphe-patches/commit/549824a7a7a90264ec87a9b8f15ae89749dc1715)

Removes the experimental batch Save resolver/presentation hooks, global sheet-row factory replacements and obsolete runtime expansion. Ordinary player/account Save uses original native paths. Queue header Save remains the phone-confirmed **first-video-only diagnostic**, not whole-queue Save. Queue playback is unchanged.

Validation:22tests passed/5unrelatedJamAPKskips; actual21.20.405 combined19related and unselected SDK12DEX/hierarchy/resources passed. Six emitted native Save methods match stock registers/instructions/references/branches/handlers. MPP/defaulttrue/currentMPE/all32Kotlinpayloads/CRC/deletedhookabsence and exact-tree gate passed. Phone regression check still pending: playerSave existing/New/WatchLater, ordinary/account/per-row and queuefirstvideoSave.

PoC38 failed multi-video saves for both existing/New; one-video saves work. User also reported playerSave regression. Do not use failed batch builds as working fallbacks.

SHA-256: `8a0734d1b36ccbcc8e2336a10e62e0116856e86ac77508e7690ea9368374565f`

## PoC39 — first-video Save diagnostic

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc39-first-video-save.mpp) · [Source 1fbc1a7ab](https://github.com/CaptKraken/morphe-patches/commit/1fbc1a7ab1d927f766cf05f18380fbb5c850245a)

As requested, queue header Save targets **only the first video in the queue**, even when another is playing. It passes a real video ID to the modern native picker and omits local batch ownership; native commands, membership toggles, New playlist and presentation fall through without batch rewriting. This is a single-video diagnostic, not whole-queue Save. Earlier batch helpers remain installed but are not activated by header Save.

Validation:22tests passed,5unrelatedJamAPKskips; build/defaulttrue passed44s; final CRC/currentYouTubeMPE/all36Kotlinpayloads verified; exact-tree gate passed. No fresh APK application: native discovery/injections unchanged from PoC38. No device/authenticated Save test.

Phone test: queue3different videos, optionally play second/third, use headerSave to existing/WatchLater and New. Confirm **only the first video** is saved and no error appears. Playback should continue unchanged. PoC38 failed phone testing with the same generic error and is superseded.

SHA-256: `c5ca6f64db952261284e191d6b1a8838cb27f1b7fdb017c82073da2eb5894461`

## PoC38 — preserve queue selection through native Save rows

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc38-save-context.mpp) · [Source 005db79a6](https://github.com/CaptKraken/morphe-patches/commit/005db79a6dde02a8171cad5510f96ec0bf99bbdf)

Fixes two concrete gaps in PoC37: newer reactive playlist rows omitted their sheet owner, and some command routes bypassed the intercepted router. Native row factories now retain the immutable queue selection; the three actual Save resolvers expand owned commands before consuming their input. Original picker, adapters, authentication and ordinary Save paths remain native. No playback changes. Batch destination unchecking remains unsupported.

Validation:22tests passed,5unrelatedJamAPKskips; real21.20.405 combined19related and unselected patch applications passed SDK12DEX/hierarchy/resources and scoped native Save checks. Final MPP/defaulttrue/currentMPE/all36Kotlinpayloads/CRC verified; mandatory commit gate passed. **Phone test pending:** existing and New playlist must receive every queued video in order; New must not be empty. No authenticated Save was tested here.

PoC37 failed phone testing: initial success followed by generic error, destination unchanged. It is superseded, not a working fallback.

SHA-256: `1f3a46f46c19b445fec88542524d6312cac1cd2c91d0c48203bdf2c66f406ae6`

## PoC37 — save the full queue through the modern picker

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc37-scoped-batch-save.mpp) · [Source 93956d2cf](https://github.com/CaptKraken/morphe-patches/commit/93956d2cffda4619a3271e658238e28de55e4f48)

The modern picker now carries an immutable queue selection. Native save/edit and creation commands expand to that full selection before the original resolvers execute. New playlist clears the single-video entity source that otherwise overrides explicit IDs. Normal player, per-row and account-playlist Save remain native. Saving does not modify playback or queue state. Batch destination unchecking is not implemented.

Validation:22tests passed;5unrelatedAPK-dependent tests skipped. Real21.20.405 combined19related and unselected APK checks passed SDK12DEX/hierarchy/resources and new native context/registry/callback/cleanup checks. Final MPP/defaulttrue/currentpayload/CRC verified; exact staged-tree gate passed. No attached phone or authenticated Save test: **please verify both existing and New playlist receive all3queued videos**.

PoC36 failed phone testing (existing Save error; empty new playlist) and is superseded.

SHA-256: `c064f90b8dc06f2ef30b40e96be33136f1e3ae615946cf2161f1e17f7b7761ac`

## PoC 32 — use current playlist context for menu visibility

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc32-menu-visibility.mpp) · [Source e1a6ef238](https://github.com/CaptKraken/morphe-patches/commit/e1a6ef238d461d6847f91f0f83fc44d1c1b9798d)

The Play next in native queue visibility guard previously treated the latest fetched player-response playlist ID as current playback context. A response for another video could therefore suppress the option. It now checks the accepted native controller only when its video ID matches the playing video. Real account playlists remain excluded, and the click still revalidates the active controller. Morphe's separate queue can remain disabled. Shared flyout timers/extraction and other queue actions are unchanged. Includes PoC31's insert-after-current behavior.

Validation:21existing tests passed,5unrelatedAPK-dependent tests skipped; actual21.20.405 standalone patch application with separate Add to queue excluded passed SDK12DEX/crosshierarchy/resources/native-bridge checks. Build/defaulttrue/currentMPE/all31Kotlinpayloads/CRC verified. No phone/emulator; the intermittent user symptom was not reproduced, and this corrects one concrete unsafe source path rather than claiming its only possible cause.

Phone checks: while standalone regular video plays, open another video's menu without restarting the player; repeat after browsing account playlists/Shorts and minimized playback. Verify the option works and actual account-playlist/current-video/Shorts menus still exclude it. No additional logs requested. Shared flyout timing remains a separate unconfirmed possibility if missing items persist.

SHA-256: `00cabb0c98277c94d01e7a0549e7867d3bf0bdd2f1a4d97ed994296021e8126b`

## PoC 31 — insert opened videos immediately after current

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc31-insert-opened-video-next.mpp) · [Source b4cb9588d](https://github.com/CaptKraken/morphe-patches/commit/b4cb9588d78d59a174f61a232f1035fce8a86e24)

Corrects PoC30 to the user's confirmed YouTube Web behavior: queue[A,B] withA playing, openC ->[A,C,B] withC selected. Subsequent queued videos keep their order. Reuses existing Play next insertion and selection; same toast/native publication/Shorts/account and navigation guards.

Validation:21tests passed,5unrelatedAPK-dependent tests skipped. Updated regression covers after-current insertion, another open from the middle, selected position, remaining order, immutable snapshots, repeat mode and invalid ID rejection. Build/defaulttrue/MPP CRC/currentYouTubeMPE/all31queueKotlinpayloads verified. No fullAPK rerun for this pure state-order correction; native discovery/injection unchanged from PoC30. No phone/emulator.

Phone check: whileA plays in[A,B], openC, confirm[A,C,B],C playing; Next should playB. Open another video whileC plays and check it appears beforeB. Last-entry insertion and existing queue controls/Shorts/account/Clear still need device checks.

SHA-256: `ff47e08eb3efffe3ccd843a8690641b9f9a5bfcfa92771550c4d3c41bfc4680b`

## PoC 30 — append newly opened regular videos

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc30-append-opened-video.mpp) · [Source 35fc0132f](https://github.com/CaptKraken/morphe-patches/commit/35fc0132fad9c288f98125bced5a91d08afa1b2b)

While a queue is active, opening a new regular video appends it at the end and selects it, retaining earlier entries. Uses the existing success toast, native queue rows/backend and normal video-open action; no second video load or forced panel expansion. Active playback and the accepted regular controller must agree before appending. Native queue row/Next/Previous selection keeps its existing path; repeated same-video callbacks do not append again. Shorts stays suspended, then a confirmed new regular video can append on return; account playlists take over normally.

Validation: 21 tests passed, 5 unrelated APK-dependent tests skipped; actual21.20.405 combined application with19related patches, SDK12DEX/crosshierarchy/resources verification, build/defaulttrue/currentMPE/all31queueKotlinpayloads/CRC checks passed. Added one append/order/selection/repeat/immutability/invalid-ID test and updated existing Shorts return policy test. No device/emulator; Android playback still requires phone testing.

Phone checks: queue[A,B], openC from Home/Search/suggested ->[A,B,C] withC playing; one toast, no extra reload or forced expansion. Try rapid/repeated opens; native row and player/notification Next/Previous must not append duplicates. Check Shorts return and opening another regular video after Shorts; account playlist takeover; repeat/shuffle, background controls, Clear/recreate. Prior PoC28 smooth Clear and PoC29 header appearance still await user feedback.

SHA-256: `b69a984db8bc99f4400224792012c956dd65a687f046b5ee1ee3a0336ad8f865`

## PoC 29 — supported native header icons

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc29-header-icons.mpp) · [Source 1d6f9989d](https://github.com/CaptKraken/morphe-patches/commit/1d6f9989d)

Keeps the native bookmark icon for Save playlist and replaces Clear queue's unmapped DELETE_SWEEP enum with native DELETE, which has a drawable in21.20.405. Uses the same native menu layout and actions. Includes PoC28's guarded clear preserving playback. No text-only menu changes retained.

Build/default selection/current payload integrity and emitted MPE icon references verified; no new APK application or device run for this enum-only change. Native resolver mapping/drawables inspected. Phone checks: both menu icons and spacing; Save overlap is NOT confirmed fixed, and remains pending device testing. Also test continuous playback on Clear from PoC28.

SHA-256: `290df49f7ac5c1d14e492429ad19e7d818e8b0adbb67c7c2a57f80bcba5a8684`

## PoC 28 — Clear queue without unloading playback

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc28-continuous-clear.mpp) · [Source a97bbfccc](https://github.com/CaptKraken/morphe-patches/commit/a97bbfccc)

Fixes PoC27's clear path: native queue disposal emits an empty selected item, whose listener unloads playback. The clear operation now uses YouTube's existing queue-change guard during disposal and releases it on success and failure. Queue entries, repeat/shuffle caches and presentation are cleared; the active player is left running. Only our queue's Clear action uses this path.

Validation: 20 unit tests passed, 5 unrelated APK-dependent tests skipped; actual 21.20.405 combined patch application, SDK verification of 12 DEX files, cross-DEX hierarchy, generated guard/exception cleanup checks, resource build and final MPP/default selection/payload integrity passed. No attached phone/emulator; uninterrupted playback still needs phone testing.

Phone checks: clear while playing and while paused, preserve current position; cleared entries must not drive notification/player Next or autoplay; create a new queue after clearing. Retest repeat/shuffle and ordinary playlists. The reported Save playlist icon/spacing artifact is still under investigation and is not claimed fixed by this build.

SHA-256: `0c661ddfacace7a4a028a08fb58e31999badac5c6d4d6cd5791ef7e0297efd9a`

## PoC 27 — Clear queue in the queue header menu

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc27-clear-queue.mpp) · [Source 9eecea28e](https://github.com/CaptKraken/morphe-patches/tree/9eecea28eb438b90d6e56983abf6eb2862dabffd)

Adds Clear queue after Save playlist in our queue's native header three-dot menu. Only owned queue panels can activate it; ordinary/account playlist menus are unchanged. Clears the local playback sequence by handing navigation back to YouTube's ordinary backend, then restores the panel. No player load/seek/stop is requested. Current video should keep playing at its position. Stale header actions are consumed without clearing a newer queue or invoking native Hide. New base resource uses the project's locale fallback mechanism.

Phone checks: Clear appears only in our header; clear while playing keeps current video/time and removes queue UI; old queued entries no longer control notification/player Next/autoplay; create a fresh queue; single item; repeat/shuffle enabled. Check Save playlist, video-row Remove/swipe/Share and ordinary playlists. Playback/device verification pending.

20 tests passed;5unrelatedJamAPKskips; string-resource check passed. Final stock21.20.405 combined19related and queue-unselected SDK12DEX/hierarchy/resources/marker/menu/handoff checks passed. Initial new marker verifier selected interface+implementation, corrected and rerun. Actual patched resources contain Clear queue. FinalMPP/defaulttrue/currentpayload/CRC and exact staged-tree gate passed. No agents/device/emulator used.

SHA-256: `cd9bf3f60f398b34eb63a5d735f85989888e5004ad43c6b0243885842d18f533`

## PoC 26 — native playlist Shuffle

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc26-native-shuffle.mpp) · [Source c78daf1df](https://github.com/CaptKraken/morphe-patches/tree/c78daf1df38035b64517a6e875fe36d7156eb666)

Enables YouTube's existing Shuffle control for the owned local queue, retaining its click handler, native random traversal and mode observers. Enabling keeps the current video playing; displayed rows retain their original order while playback follows shuffled links. Entry changes invalidate the old native index links and rebuild them using YouTube's original algorithm, preserving the enabled mode and repeat. Metadata-only refreshes preserve shuffle order. Header availability refreshes on native backend events, including reattachment. No new UI/settings or custom shuffle algorithm.

Phone checks: use4items; enable without restarting playback; Next/Previous/autoplay agree across player and notification; disable at shuffle traversal end; repeat-all/one and1item; add/remove/reorder while enabled; metadata refresh; background/Shorts return. Ordinary playlists and prior features should retain their behavior. Device playback pending.

20 existing tests passed;5unrelatedJamAPKskips. Final current-source stock21.20.405 combined19related and queue-unselected application, SDK12DEX/hierarchy/resources and native shuffle cache/mode/snapshot checks passed. Initial added verifier matched an interface as well as its implementation; corrected and rerun. FinalMPP/defaulttrue/currentpayload/CRC and exact staged-tree gate passed. No agents/device/emulator used.

SHA-256: `d91512dd456a3346a2062c56f5acf55d652e99df96366b1c046526c9fd0f7db9`

## PoC 25 — modern native Share sheet

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc25-modern-share-sheet.mpp) · [Source 07bdd17ac](https://github.com/CaptKraken/morphe-patches/tree/07bdd17ac4d26f4c9fb0d8cb307b8f91f0f8fc8d)

PoC24 opened the old Share grid. This build opens YouTube's named action sheet, then requests panel type2 from its native Share service. The server returns the modern Share sheet contents for the tapped queue video. Removes the previous direct legacy Share override; the existing Open system share sheet patch handles its own setting normally. No custom Share UI.

Phone checks: modern bottom sheet; correct link when sharing a nonplaying queued video; current row; cancel/reopen; System Share setting off/on. Check ordinary playlist Share, Save/Download/Remove/swipe and background navigation. Device appearance/behavior pending.

20 tests passed; five unrelated Jam APK skips. Stock21.20.405 combined19related and queue-unselected APK application, SDK12DEX/hierarchy/resource checks passed. Actual anonymous row-entity service responses distinguish legacy type1 from modern type2 and confirm matching sheetId. Emitted command/native onShow ordering inspected. FinalMPP/defaulttrue/currentpayload/CRC and exact staged-tree gate passed. No agents/device/emulator used.

SHA-256: `729797b5e6f391ad05b3b469eea637343ca280d4f9f5fab49e080db63839cd56`

## PoC 24 — native player Share action and row menu context

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc24-row-menu-share-fix.mpp) · [Source 995b0bfc6](https://github.com/CaptKraken/morphe-patches/tree/995b0bfc6fb8f688025f533883fb7139604d54c7)

Suppresses Play next in native queue in the queue's own row menus, preserving its outside entry points. Uses YouTube's localized Share video string instead of the missing share resource key. Share now uses the same navigation endpoint as the captured player Share button and native playlist row, opening the native unified share screen for the tapped video. The previous service-only action is removed. The optional System Share override is retained only when its patch is included and its setting enabled.

Phone checks: queue row has no redundant Play next; Share video label is localized; Share opens the player share screen with the correct nonplaying video, with System Share off/on. Check outside-menu Play next, Remove/swipe, ordinary playlists and background queue navigation. Phone/server outcome remains pending.

20 tests passed, five unrelated Jam APK-dependent skips. Actual stock21.20.405 combinedqueue+19related and queue-unselected SDK DEX/hierarchy/resources plus emitted context/dismissal checks passed. Captured native row Share endpoint/panel/video-ID wire check passed. Final MPP/default=true/payload/CRC and exact staged-tree gate passed. No agents or Android emulator used.

SHA-256: `70912f45ad130db398836326c33c903b12ce3ba2d6db996d61bb1079de771d9f`

## PoC 23 — correct native row menu field

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc23-row-menu-field-fix.mpp) · [Source 7e39c8f59](https://github.com/CaptKraken/morphe-patches/tree/7e39c8f59d9884959000a5db5bb381d1adc2e9a7)

PoC22 failed on the phone: no per-video three-dot buttons appeared. The local protobuf wrote menu at field18, which stock21.20.405 treats as text. The native menu is field19. This bundle changes only that field number and preserves the existing native menu/actions.

Actual captured native row verification now decodes its populated menu, verifies the regenerated writer emits19 and confirms broken18 has no menu. Existing tests20passed/5unrelatedJamAPK-skips, MPP build/default=true/integrity and commit gate passed. No native hook changes or new APK application run. Phone check first: do the per-video three-dot buttons now appear? Then test Save/Download/Share/Remove; these actions are not device-verified yet.

SHA-256: `7497f7c2932ac25c5b66b47db3d4dfca21f1793c2090fea2c3d1729ae802ef03`

## PoC 22 — native video row menu

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc22-row-menu.mpp) · [Source 3c48ce2a9](https://github.com/CaptKraken/morphe-patches/tree/3c48ce2a94fd08b04200cb75aa196189122aafa7)

Adds YouTube's native three-dot menu to local queue rows: Save to Playlists, Download, Share, and Remove for nonplaying rows. Save targets the tapped video; Download uses the native eligibility action and respects the configured downloader override; Share retains the native/System Share setting and uses the tapped row's ID. Remove reuses guarded local queue removal, preserves native dismissal and never removes the playing row. Uses YouTube's localized Remove text; Premium queue wording is unverified.

Phone checks: open playing/nonplaying row menus; Save/Download/Share the nonplaying video and confirm its ID. Test native/external Download and native/System Share settings, including after opening another playlist/post menu. Remove should be absent on the playing row, reject stale selection, close the sheet, and preserve playback/order. Retest swipe, reorder, header Save, ordinary playlist menus and background notification Next/Previous.

20 tests passed; five unrelated Jam APK-dependent tests skipped. Stock 21.20.405 APK application and SDK DEX/hierarchy/resources passed with queue plus 19 related patches and queue unselected; queue-only application also passed (full standalone log was not saved). Review corrected interface context and label issues before publication. Focused emitted-DEX checks accept corrected outputs and reject an earlier wrong-interface guard. Final MPP/default=true/integrity and exact staged-tree gate passed. Native dialogs, server actions and device behavior still need phone testing.

SHA-256: `82cd06ccead051752a8ae9a218ce2409001dcc2e17e92ea79384cebd00d202ff`

## PoC 21 — notification playback-state completion

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc21-notification-state-completion.mpp) · [Source 6239139bb](https://github.com/CaptKraken/morphe-patches/tree/6239139bbfcf8ceaeb216df2a325aba02802c082)

PoC 20 still lost notification navigation after background skips. This candidate recomputes the active queue's native navigation after YouTube finishes its notification playback-state handler, including its per-video reset. It removes the earlier selection-time refresh and changes neither Background playback policy nor the final Media notification controls filter. Device resolution is unconfirmed.

Phone check: create three queued videos, press Home, then notification Next twice and Previous twice without reopening YouTube. Navigation should track first/middle/last positions and remain available. Repeat paused, locked and after Shorts; compare ordinary/account playlists.

20 tests passed; five unrelated Jam APK-dependent tests skipped. Stock 21.20.405 APK application and SDK DEX/hierarchy/resource verification passed with the queue plus 17 related patches and with queue unselected. Actual DEX analysis checks both native state handlers' five normal exits and preserved receiver types; the same check rejects PoC 20. Final MPP/default-selection/integrity and source review gate passed. No phone/emulator playback test performed.

SHA-256: `00d074938cbb96a646509d7423f252a3e1ad7ca910f707205ec35f0d920fd26a`

## Step 16 — retain background playback ownership after Shorts

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step16-background-handoff.mpp) · [Source 1f35e10b8](https://github.com/CaptKraken/morphe-patches/tree/1f35e10b8ca29d41ef4fad87a90da163e3a63156)

Retains the local queue when the live playback service confirms the same queued video while its watch UI is absent. A return from Shorts can resume queue ownership without a visible watch controller; notification skips reconcile that ownership before rejecting the press. A recreated detached watch controller gets a fresh native model baseline. The current implementation still uses native playlist UI with local queue navigation; attaching the native playlist playback engine is separate research. This is an interim lifecycle fix, not device-confirmed resolution of every background failure.

Phone checks:

1. Create a fresh three-item queue, use notification Next and Previous while YouTube is visible, then press Home and use them without reopening. Repeat paused and playing, and on the lock screen.
2. Open several Shorts, return with Back, press Home, then use notification Next/Previous before reopening YouTube. Verify actual playback follows the queue and that its order, selected row/counter and saved position survive repeated trips.
3. Check first/middle/last-item boundaries, recreated watch screen, actual player dismissal and an unrelated/account playlist. Native Shorts/casting and Loop, autoplay, sleep timer, background screen-lock pause and media visibility settings must retain their behavior.

25 tests passed, including two new focused service-resume/ownership regressions; five unrelated Jam APK-dependent tests skipped. Real stock 21.20.405 APK application, 12-DEX SDK/hierarchy and resource checks passed standalone, with 17 related selections including Remove background playback restrictions, and queue-unselected. Native notification callbacks/bridges and stock fallthrough inspected; 376 host/shared methods and 14 interface sets match the prior validated build. A temporary-disk resource-packaging failure was resolved by retrying unchanged source on workspace storage. Final Android MPP/default=true loader and exact staged-tree review gate passed. No attached phone; playback timing remains for device testing.

SHA-256: `966d0b14634a7529f212d1da24cf8fc2956b0737b3efa7ce462d770bbd686a71`

## Step 15 — retain the queue across Shorts

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step15-shorts-retention.mpp) · [Source b2b998e5d](https://github.com/CaptKraken/morphe-patches/tree/b2b998e5d70fc9346b9213158638c088481feab5)

Suspends the regular local queue while Shorts owns playback, preserving its order, selected occurrence and repeat mode. Returning waits for the regular controller and active video ID to agree, then refreshes the native queue rows and controls. Shorts playback and notification controls retain their native handling. A confirmed different regular video/account playlist ends the old temporary queue. No new UI or shared Shorts/Loop patch changes.

Phone checks:

1. Create a three-item queue and go to the middle item. Open Shorts from a shelf or tab, swipe through several Shorts, then return with Back. Verify the same queue order, position and repeat mode, without forcing the panel open. Repeat the trip with the queue collapsed and expanded.
2. After returning, verify row selection, player/notification Previous and Next, automatic advance, and repeat-one. While Shorts is open, its controls must not navigate the retained regular queue. Also try opening Shorts immediately after pressing Next.
3. Open a different regular video or account playlist after Shorts, and dismiss the regular player. Confirm the old queue no longer controls playback. Check the first/last item, duplicate IDs, native dragging/removal, background/PiP, Loop, sleep timer and autoplay/visibility settings. Phone validation remains pending.

23 tests passed, including four new tests of the actual Shorts suspension policy; five unrelated Jam APK-dependent tests skipped. Current stock YouTube 21.20.405 APK application and SDK verification of 12 DEX files/resources/hierarchy passed standalone, with 16 related selections, and queue-unselected. Emitted ownership/suspension and notification guards inspected. 376 native/shared methods and 14 interface sets matched the validated previous build; native discovery and injection code are unchanged. Final Android MPP/default=true check and exact staged-tree review gate passed. Device transition timing and playback still require phone testing.

SHA-256: `b413b9b70f214989270db154aa761fefbb013885f2e7177598e5a45f17b2b6c9`

## Step 14 — first queue row swipe background

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step14-swipe-background.mpp) · [Source c518eef44](https://github.com/CaptKraken/morphe-patches/tree/c518eef44cee678e0b35f119290644bd1e38b4e3)

Refreshes the native swipe widget's cached foreground background after YouTube changes a local queue row's selection background. This addresses a stale playing-row drawable when the first attached item becomes removable. Keeps YouTube's original swipe layout, animation, remove button and dimensions; account playlist rows retain their native path. Visual confirmation on the phone remains pending.

Phone checks:

1. Create a two- or three-item queue, advance to the second video, then swipe the first row slowly and fully. The remove action should be revealed beside the moving card with no card text visible through it.
2. Close/reopen the panel, navigate back and forward, and repeat partial/full swipes on the first and other rows. Tap remove; verify order/counter, current-item protection, native dragging and rating actions.
3. Compare a normal account playlist, check dark/light theme, and confirm notification Previous/Next and native Loop still work. Phone validation remains pending.

19 existing tests passed; 5 unrelated Jam APK-dependent tests skipped. Stock YouTube 21.20.405 APK application and SDK verification of 12 DEX files/resources/hierarchy passed standalone, with 16 related selections, and queue-unselected. Inspected emitted field/register/branch and ownership guards. Compared 158 native methods to stock; every original instruction/register/relative branch remains, with only one added background observer when selected. No new JVM UI imitation or source-snapshot tests. Final Android MPP/default=true check and exact staged-tree review gate passed. Device rendering remains unverified.

SHA-256: `be5beb5a153e5b94bf9ede855cca1fcd5f9687a8f627979ee47fb4f6f9affa62`

## Step 13 — native notification queue navigation

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step13-native-notification-navigation.mpp) · [Source de98fb69c](https://github.com/CaptKraken/morphe-patches/tree/de98fb69c644cd78f0f8d64ebf7124f6bf5e1328)

Uses YouTube's native notification-control adapters to publish the queue's Previous/Next availability before notification actions and playback state are built. At the final item with repeat off/one, Previous is available and Next is unavailable. Adds notification Previous routing through the same native playback-service loader as Next, including when the watch screen is hidden. Replaces the earlier late platform-state filter; existing native layouts, icons, compact layout and notification-hiding settings remain in use.

Phone checks:

1. Create a three-item queue. Check notification controls on first, middle and last items; compare to a normal YouTube playlist. Last should offer Previous with no Next when repeat is off/one.
2. On the last item, press notification Previous and verify the preceding queued video actually plays and the counter updates. Repeat after Home, while paused, and on the lock screen; use Next to return to the final item.
3. At the final item toggle Repeat all on/off, append another video, reorder/remove, or tap an earlier row. Notification availability should update using the new order without reopening YouTube.
4. Check native Loop/repeat-one after Previous, rapid presses/duplicate IDs, queue close/new session, ordinary/account playlists, casting, and hide-notification-controls/disabled-seek settings. Device validation remains pending.

19 existing tests passed;5 unrelated Jam APK-dependent tests skipped. Extended the existing repeat test to verify Previous from the final item for all repeat modes. Current stock21.20.405 APK application and SDK12DEX/hierarchy/resource verification passed standalone, with16 related selections, and queue-unselected. Emitted adapters/constructor/skip hooks and receiver/branch/native-notifier handling inspected;17 native notification/model/loader/repeat methods compared unchanged. Obsolete platform-filter hooks/interfaces are absent. Final Android MPP/default=true check and exact staged-tree review gate passed. Native notification rendering and background playback still require phone verification.

SHA-256: `319510042f4a69d634f05a43b69369273766eb1294f152346759a18869d4bcc3`

## Step 12 — queue position and notification boundary

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step12-queue-indicators.mpp) · [Source f22aff16a](https://github.com/CaptKraken/morphe-patches/tree/f22aff16ab262fbe953b11b07a33415764a3831f)

Corrects the native renderer pagination offset so a two-item queue shows 2/2 after advancing. Filters the owned queue's notification Next action at the final item and refreshes cached native playback state after queue edits, repeat changes and background selection. The notification visibility change uses Android 13+ PlaybackState actions; Android 12 and earlier use a separate notification action layout, which is not changed in this step. Counter and notification fixes have separate source commits.

Phone checks:

1. With two queued videos, advance from the on-screen player and check 2/2. Repeat using notification Next, both while YouTube is visible and after Home; reopen and check the selected row and count.
2. At the last item with Repeat off or Repeat one, Next should disappear from the Android 13+ notification. Enable Repeat all and check Next returns and wraps to the first entry; switch it off again while paused and playing.
3. At the boundary, append another video, reorder, remove a different item, or tap an earlier row. Check count and Next availability update without reopening the app.
4. Check native Loop/repeat-one playback, ordinary/account playlists, casting and Morphe hide-notification-controls settings retain their behavior. Device testing is still needed; Android 12-and-earlier button visibility is outside this step.

19 existing tests passed;5 unrelated Jam APK-dependent tests skipped. Stock21.20.405 APK application passed standalone, combined with16 related selections, and queue-unselected, with SDK12DEX/hierarchy/resource checks. Emitted session ownership, preserved receiver, shared-filter chaining and native-original load/repeat paths inspected. Android MPP/default-selection checks and separate exact staged-tree pre-commit gates passed. Phone validation remains pending; Android12-and-earlier notification action layout is unchanged.

SHA-256: `ea74e375388c6f44f25b8c49b9e53f863c098edde7ac7fa17660dde87c9f7f63`

## Step 11 — notification Next follows the queue

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step11-notification-next.mpp) · [Source a2a83547b](https://github.com/CaptKraken/morphe-patches/tree/a2a83547ba12581dd31aaff6a600564499240eec)

Routes notification Next to the queued target through YouTube's normal playback-service load API, bypassing the foreground history/screen route. Registers the existing background active-video hook to confirm queue selection while hidden, and refreshes retained UI when it catches up. Casting/unrelated/account playback retain original callbacks. This step handles notification Next; it retains the earlier native Repeat-one correction and existing queue features.

Phone checks:

1. With YouTube visible and a three-item queue, press Next in the notification. It should play the next queue item, not a recommendation/history item.
2. Leave YouTube using Home. Press notification Next twice without reopening it; queued videos should actually play. Return to YouTube and check selection, with no extra delayed skip. Repeat from lock screen and paused playback.
3. Reorder then test notification Next; test last boundary and repeat-all wrap, quick presses and duplicate IDs. Confirm native Repeat one remains working after manual skips.
4. Check ordinary/account/casting notification controls and hidden-media-controls/spoof/fullscreen settings. Existing Activity/process-death persistence and background automatic advance are not expanded by this step.

19 existing tests passed;5 existing Jam APK-dependent tests skipped. Current stock21.20.405 APK application passed standalone, combined with16 related selections (including a shared helper), and queue-unselected; SDK DEX/hierarchy/resource checks passed. Emitted notification/service/background-ID hooks and original native loader/repeat paths inspected. Android MPP/default-selection and exact staged-tree review gate passed. Actual background playback/selection and native mode transitions require phone testing.

SHA-256: `964aa662ce5f5fccafc838e76867dceabd2f1365b392059ed619cf2f4131e3ce`

## Step 10.2 — preserve YouTube's native repeat-one playback

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step10-repeat-fix2.mpp) · [Source 757208921](https://github.com/CaptKraken/morphe-patches/tree/7572089217d27d04c4998b806718782323695bab)

Stops consuming YouTube's automatic navigation events while Repeat one is active, restoring its original same-video restart/play path. Removes the queue's competing deferred replay attempt. Manual Next/Previous and off/all queue advancement remain local. No changes to Morphe's separate Loop patch or other patches' settings. This corrects a verified native-path interception; actual phone replay still needs retesting.

Phone checks:

1. Enable Repeat one using the queue header or native player Loop and let the video finish twice; test first/middle/last queue entries.
2. Confirm selected queue item stays the same during replay, and manual Next/Previous still skip. Disable repeat and confirm queue advancement resumes under your settings.
3. Check native end sleep timer, normal/account playlists, close/reopen, and existing Loop/fullscreen/autoplay preferences. Repeat all still wraps under its existing policy.

19 tests passed;5 existing Jam APK-dependent tests skipped. Current combined stock21.20.405 APK with13 related patches passed SDK DEX/hierarchy/resource verification. Emitted DEX inspected to confirm Repeat one returns to the original native handler before local interception; original repeat setters/internal reset/RPC unchanged. Android MPP/default-selection and exact staged-tree review gate passed. No native injection changes; unchanged standalone/unselected paths were not rerun. Phone verification remains pending.

SHA-256: `a5b993127013b4fad7ee36c7c8fed14a77f4e5c6e45c1b927bf02ea246ffc02a`

## Step 10.1 — repeat-one completion fix

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step10-repeat-fix1.mpp) · [Source c4d9f2bc5](https://github.com/CaptKraken/morphe-patches/tree/c4d9f2bc540a01a8093dba42a1d8beaa6e600f52)

Repeat one can restart the current item without depending on a native playlist AUTOPLAY request. Queue advancement still respects Disable playlist autoplay; replay retains the captured end sleep-timer veto and duplicate-completion protection. No changes to Morphe's separate Loop patch. Cumulative build includes all earlier queue features. This fixes a confirmed source-level replay dependency; the reported phone issue still requires retesting.

Phone checks:

1. Enable Repeat one from the queue header or native player Loop switch, then allow two consecutive real completions; test first/middle/last queue entries.
2. Repeat with Disable playlist autoplay on and off. Explicit replay should work in both cases; end-of-video sleep timer should still stop playback.
3. Disable repeat: queue advancement retains configured policy. Repeat all wraps subject to that policy; manual Next/Previous still skip.
4. Check close/reopen/account playlists and existing Morphe Loop/fullscreen preferences retain their behavior.

19 tests passed; 5 existing Jam APK-dependent tests skipped. Current combined stock-APK application with 13 related patches passed SDK DEX/hierarchy/resource checks. Native hooks and original reset paths inspected; no fingerprints or injected code changed. Android MPP/default-selection and exact staged-tree review gate passed. Standalone/unselected native paths remain unchanged from the previous verified build; those modes were not rerun for this runtime-only fix. Phone playback remains pending.

SHA-256: `b1c3b5c9949c05479d79eab9adf65b943404266918010ce61e6135abe687b27f`

## Step 10 — queue repeat and native player Loop

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step10-repeat.mpp) · [Source 942a2d6ce](https://github.com/CaptKraken/morphe-patches/tree/942a2d6cee2c44b0d35c8088c6e73f869dceb8ed)

Adds Off / Repeat all / Repeat one through YouTube's existing repeat header and native player Loop switch. Both controls share the queue's mode; Repeat all wraps the queue, Repeat one replays the current item, and manual skips follow the queue. Existing Morphe Loop behavior is unchanged in this step. Cumulative build includes all earlier queue features. Shuffle remains deferred.

Phone checks:

1. Enable native player Loop before creating a queue, then add a video: repeat-one should be inherited. Toggle Loop and header Repeat one in both directions; the controls should agree.
2. Cycle header Off -> All -> One -> Off. Off stops after the last item; All wraps last to first; One replays current on completion. Test first/middle/last, including an already-ended video.
3. With One, manual Next/Previous still skip. With All, manual boundaries wrap. Repeat mode should survive playing another row and adding/removing/reordering items.
4. Check portrait/fullscreen and panel open/closed; close/reopen player or start a new queue quickly, then switch to an ordinary video/account playlist. No repeat-state leak or stale restart should occur.
5. Check sleep timer, Disable playlist autoplay, the separate Morphe Loop/range feature, and fullscreen/hidden-controls settings still behave as configured.

18 tests passed; 5 existing Jam APK-dependent tests skipped. Applied to the stock YouTube 21.20.405 APK standalone, combined with 13 related patches, and with queue unselected; SDK DEX/hierarchy/resource checks passed. Native hooks/control flow inspected; original repeat setters/internal reset/RPC paths verified unchanged. Android MPP/default-selection check and exact staged-tree review gate passed. Actual phone playback and visual synchronization remain pending.

SHA-256: `75e975930d019403f41f968e3316caa8eed5ff5bdba97999b87454bf6568173b`

## Step 9: queue playback navigation

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step9-playback-navigation.mpp) · [Source 71a0def7b](https://github.com/CaptKraken/morphe-patches/tree/71a0def7bd08b257dd5a2287452dd93b4662ee53)

Adds automatic advancement through the displayed temporary queue and player Next/Previous navigation, including native cached eligibility and history-delegate paths. The queue stops at its last entry. Existing Loop video, end-of-video sleep timer, Disable playlist autoplay, fullscreen and button-visibility policies retain their effect. Includes prior native cards, ratings, row playback, swipe removal, reordering and Save playlist.

Phone checks:

1. Create a queue with three different videos. Seek near the end and let playback finish: the next displayed item should play once. Let the last video finish: retain the queue and stop.
2. Use player Next/Previous in portrait/fullscreen with the queue expanded/collapsed. Verify the selected row follows playback and first/last boundaries do not open recommendations.
3. Reorder/remove/add queue entries while the current video plays. Verify button availability refreshes and navigation follows the new order. Try an end while holding a drag.
4. Enable Loop video, then disable it; test the end-of-video sleep timer and Disable playlist autoplay. Auto suppression should stop advancement while manual navigation still works. Check hide-button/fullscreen settings.
5. Close/reopen the player, replay the last item, and open ordinary videos/account playlists. Verify original behavior and no stale queue takeover.

Focused JVM tests, real 21.20.405 APK application with Android SDK DEX/resource verification in standalone/combined/unselected modes, native hook/control-flow inspection, local pre-commit review gate, and Android MPP/default=true verification passed. Phone playback checks remain pending; no device reproduction is claimed.

SHA-256: `1e9a45c067afef4ef4ba49e617e13f56d532ca11e6cd450b5db02ba540df89fa`

## Step 8 — native Save playlist

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step8-save-playlist.mpp) · [Source c8856c224](https://github.com/CaptKraken/morphe-patches/tree/c8856c22411332b33dd49b39665360221250fe2b)

The queue header's existing three-dot button opens YouTube's native bottom sheet with only Save playlist. The action supplies the queue's current video order to YouTube's native create-playlist dialog, which handles sign-in, playlist name, privacy and creation. No custom UI, new injected callbacks or request helpers. Saving creates a new account playlist from the temporary queue rather than bookmarking a server Mix ID.

Phone checks:

1. Open Queue, tap the three-dot button beside Repeat/Shuffle, and confirm the native bottom flyout has only Save playlist and no Share action.
2. Tap Save playlist and cancel the native name/privacy dialog. The playing video, queue and order should remain unchanged.
3. Reorder/remove items, then save using a recognizable name and chosen privacy. Check the new playlist in Library contains the expected videos in order, including the playing item. Confirm the temporary queue and playback remain usable.
4. Confirm ordinary account playlist menus retain their normal actions. If convenient, check that signed-out use reaches YouTube's own sign-in prompt.

Review gate passed. JVM regression suite: 14 passed, 5 existing Jam APK-dependent tests skipped. Actual YouTube 21.20.405 patch application and SDK DEX/resource checks passed with 11 related patches and with queue unselected; no new native hook sites. Native menu schema, one-item create command and supplied video-ID ordering checked against stock code and captured native data. Android MPP built and default selection verified. Native dialog interaction and account playlist persistence still require the phone checks above.

SHA-256: `00657d10901ef8fd1d0d0be9eabbece41cb2745a651949cc5dd964b30bc438e0`

## Step 7 — native drag reordering

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step7-reordering.mpp) · [Source 1a679ff3c](https://github.com/CaptKraken/morphe-patches/tree/1a679ff3c05d77b8a5b4ca06c752be0eeda12dc5)

Cumulative build with YouTube's existing drag handles and gesture machinery enabled for local queue rows. Completed moves update local queue order while retaining the playing entry. Metadata refresh waits until a drag ends. Normal account-playlist reordering keeps its native backend and callbacks.

Phone checks:

1. Add several videos, open Queue, and drag the left handle up and down, including first-to-last and last-to-first. Collapse/reopen the panel and confirm the order remains.
2. Move the playing row and move other rows across it. The same video should keep playing and remain highlighted at its new position. Play next in native queue should insert after that position.
3. After reordering, tap a different row, use like/dislike, and swipe a nonplaying row left then tap remove. Check that each action targets the correct video.
4. Try a held or cancelled drag while metadata finishes loading, then rotate or close/reopen the player. Check for stale order, flicker, errors, or a stuck drag.
5. Open an ordinary account playlist and confirm its drag actions still work normally.

Review gate passed. Focused JVM suite: 14 passed; 5 existing Jam APK-dependent tests skipped. Real YouTube 21.20.405 patch application and SDK DEX/resource verification passed with 11 related patches and with Native playlist queue unselected. Emitted native callbacks/bridges and ownership guards inspected; queue hooks absent when unselected. Captured native Mix row verified canReorder field 36, and local rows omit the server edit identifiers. Android MPP built and default selection verified. Gesture and playback checks above remain pending on a phone.

SHA-256: `061b6ea631ee7805ad6c0b807ac8c46c77ee1d476247cd36b47b9e41a88f835b`

## Step 6 fix 1: retain full collaborating-channel bylines

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step6-fix1-full-byline.mpp) · [Source 5ad1ab038](https://github.com/CaptKraken/morphe-patches/tree/5ad1ab038b1d394adde5a40a3ebd41655413ba95)

Includes all step6 functionality. Uploader-only cards no longer skip full native byline lookup. Verified longBylineText takes precedence over later uploader-only card/player/lookup metadata, retaining all collaborating names and native connectors. Captured titles and native single-line playlist UI are preserved.

Phone checks:

1. Add waZyE4Xl6bo from Watch Later or Home, wait for background metadata and confirm Starbound Extra and Starbound HFY in the native byline (native ellipsis may truncate it).
2. Scroll/reopen the queue and play another item; collaborating-channel data should not revert to the uploader-only name.
3. Retest native row playback, rating buttons and swipe removal from step6.

13 tests passed;5 existing Jam APK-dependent tests skipped. Current stock21.20.405 +11related patches SDK DEX/resource validation passed. Actual saved native response cache-precedence smoke retained both collaborators after uploader-only updates. Final AndroidMPP/default=true and review gate passed. Phone validation pending.

SHA-256: `7d76b035035bd11df8e926d7d73a9b5fb4c43a4d01d659ce734c9bd442d9516c`

## Step 6: native swipe removal

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step6-swipe-removal.mpp) · [Source eff1206c7](https://github.com/CaptKraken/morphe-patches/tree/eff1206c7de88ec7fa56b6dc607e3d0483b1d6c6)

Cumulative full collaborating-channel/card information, native like/dislike actions, row playback and native Mix-style swipe removal. Swipe right to left reveals the crossed-out-eye button; tapping removes a nonplaying row. Playing items, stale buttons and navigation in flight are protected. Uses native swipe UI and local state, with no account-playlist edit payload.

Phone checks:

1. Swipe a nonplaying row from right to left: it should reveal the crossed-out-eye button without removing immediately. Tap the button to remove it.
2. Confirm the playing row cannot be removed. Select a later row, then remove a row before and after it; playback and the selected video should remain unchanged.
3. Check repeated taps and metadata refreshes while a removal button is revealed, portrait/fullscreen behavior and ordinary account-playlist swipes.
4. Retest complete collaborating-channel text/views/age/duration, like/dislike undo and main watch-page synchronization, and tapping earlier/later queue rows.

12 tests passed,5 existing Jam APK-dependent tests skipped. Stock21.20.405 SDK DEX/resource verification passed with11related patches and with queue unselected. Emitted DEX confirms selected-only hooks/ownership markers; native Mix wire schema smoke passed.90670strings checked without issues; final AndroidMPP/default=true/integrity checks passed. Native UI/account/playback behavior still needs phone verification.

SHA-256: `96eb6a1d9e93ce3544a9ab40acf751fe403312dedd43632590078677b3935d05`

## Step 5: play selected queue rows

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step5-row-playback.mpp) · [Source cfabd1c96](https://github.com/CaptKraken/morphe-patches/tree/cfabd1c968b66ffaee45955c961294b2313b3a22)

Cumulative full native card information and rating actions, plus native row navigation that retains local order and updates selection. Adds no Next/Previous or automatic progression. Account/feed actions retain original routing. Swipe removal is not included in this milestone.

Phone checks:

1. Tap later, earlier and last queue rows in portrait/fullscreen. Check playback, selected row and retained queue.
2. After selecting a later row, add another video using Play next and check its position.
3. Test rapid/repeated taps, external video/account-playlist opens and closing/reopening the player.

11 tests passed,5 existing Jam APK-dependent tests skipped. Stock21.20.405 patch application and SDK DEX/resource verification passed with11related patches and with queue unselected. Native click control flow and watch-endpoint wire fields inspected; finalMPP/default=true passed. Phone playback still requires testing.

SHA-256: `686e1457977c7757e1b6dbb4ee8cdcd5b0f38b11987fc216a482acba438b801f`

## Step 4: native like and dislike actions

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step4-ratings.mpp) · [Source 82ac8cb2e](https://github.com/CaptKraken/morphe-patches/tree/82ac8cb2ef7cf85114ee85fcb40327cda63b82ef)

Cumulative card information plus native like/dislike buttons on the playing queue row. Reuses native watch commands and shared rating entities; no custom action UI or separate account API. Row playback and swipe removal are not included in this milestone.

Phone checks:

1. Like then undo; dislike then undo; switch between like and dislike. Check synchronization with the main watch-page rating buttons.
2. Test while Return YouTube Dislike is selected and while watch-page action buttons are hidden.
3. Add another video, close/reopen the player and open an account playlist; original rating paths should remain unchanged.

10 tests passed,5 existing Jam APK-dependent tests skipped. Stock21.20.405 SDK DEX/resource validation passed with11related patches with queue selected and unselected. Actual native command capture smoke and final Android MPP/default=true checks passed. Phone account actions remain unverified.

SHA-256: `bff87cba14d1c6af1b53443088f0975c7a54bdbfc42a5221879470201db37ad6`

## Step 3: complete native card information

[Download MPP](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step3-card-info.mpp) · [Source 7b2147f05](https://github.com/CaptKraken/morphe-patches/tree/7b2147f05ac0a615e12496e5a215d0d41d352249)

Native rows now include full collaborating-channel text, views/age and duration badges. Complete captured cards avoid a lookup; incomplete rows use a bounded background Android search projection with exact video-ID matching. Title policies remain effective and late completion updates only an owned active queue. Native UI is reused, with no auto-expansion. Optional missing/private/unindexed metadata retains its fallback. Ratings, row playback and removal are not included in this particular milestone.

Phone checks:

1. Add waZyE4Xl6bo from Watch Later while a normal video plays; wait briefly, then confirm Starbound Extra and Starbound HFY, views/age and29:04 on the queued row.
2. Check Home/recommendations and long channel names against the native playlist style.
3. Close the player before a lookup completes and open an account playlist; the queue must not reopen or replace it. Confirm additions still show a toast without expanding the panel.

9 tests passed;5 existing Jam APK-dependent tests skipped. Stock21.20.405 patch application with9related patches and SDK DEX/resource checks passed with the queue selected and unselected. All20 captured native cards retained channel/info/duration. Exact reported-video lookup projection returned both collaborators and full metadata. Final Android MPP build/default=true check passed. Phone behavior remains unverified.

SHA-256: `0116302d041841b7f10133134286fee272c74769d2663345ef48a583f31c5260`

Earlier step 1/2 bundles remain in this branch. LICENSE and NOTICE are preserved; only distribution manifest metadata differs from build output.
