# Native playlist queue — incremental phone-test builds

Patch an untouched **YouTube 21.20.405 APK** with the selected local MPP source. Native playlist queue stays selected by default. Each bundle includes earlier features; each feature has its own source commit. Phone checks remain pending until performed on a device.

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
