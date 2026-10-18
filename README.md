# Native playlist queue — incremental phone-test builds

Patch an untouched **YouTube 21.20.405 APK** with the selected local MPP source. Native playlist queue stays selected by default. Each bundle includes earlier features; each feature has its own source commit. Phone checks remain pending until performed on a device.

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
