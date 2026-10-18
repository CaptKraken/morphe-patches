# Native playlist queue — incremental phone-test builds

Patch an untouched **YouTube 21.20.405 APK** with the selected local MPP source. Native playlist queue stays selected by default. Each bundle includes earlier features; each feature has its own source commit. Phone checks remain pending until performed on a device.

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
