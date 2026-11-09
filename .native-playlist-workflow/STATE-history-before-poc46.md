# Active — New playlist complete queue fix

User phone confirms PoC45 toast AND Undo work. Now authorized next fix: New playlist currently saves only first video. Found native beoz button field2 vs our QueueSaveHeader.button3; header binding skips real button. Real schema DEX and ona binder confirm. jtu/nln/nlm pass full SelectedVideoIds list into ailn repeated request field3. Add native wire-format regression independent of generated header builder; run it failing before correcting field. No new native hooks needed.

# Current checkpoint — PoC45 existing-playlist confirmation test published

Source52dd00d1f64f6fb8fe4f567b6995a5fb0beb8432 on feature/youtube-native-playlist-queue. Basee5464395441e916b643af4e2175c4e253e6df487. Artifact423b087d3cdfba81a9d7605407f02f97d6d961db on builds/youtube-native-playlist-queue. Both pushes confirmed, both trees clean.
https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc45-save-confirmation-undo.mpp
SHA25605c7f0a78caed1118d8afffd4ebe7c7373a9965ecd6d7749c6197325db4c0743;11904227bytes.

## Scope and next action
User explicitly clarified confirmation missing for EXISTING playlists only. PoC45 appends native Saved to [playlist] + safe Undo on successful queue-owned existing Add response. Exact newly-added server entry IDs only; missing/foreign data omits Undo rather than guessing. Native Snackbar pipeline/settings, original native callbacks/auth/query remain intact. No global state/custom UI/network. Ordinary player/row/account Save unmarked and unchanged. Native New creation unchanged.
User NOW reports NEW playlist creates only FIRST queue video. Explicit latest instruction: test toast first, then fix New separately. Do not bundle New changes into confirmation. Wait for phone confirmation result, then investigate New payload/interfaces from stock; previous JVM New binding is NOT proof of actual final backend request.
PoC44 user-confirmed existing Save works; earlier broad success must not be interpreted as New success. PoC42FAILED1of3/PoC43FAILED400beforepicker historical. Stock single-video picker query must stay stock; full queue binds only later native destination commands. Play last already deliveredPoC41.

## Validation actually performed
23pass/5unrelatedJamAPKskips/0fail (28total). Combined20related real21.20.405 passed2m27s including Hide layout components; SDK12DEX/hierarchy58230/resources and strings90674clean. Finalexactsource tests/unselected/MPP passed2m12s after helper safety refinement.18ordinary nativeSave/auth/serializer/error/query/Snackbar methods matchstock. Typed callback bridges selected-only, absent unselected. FinalMPP CRC/currentMPE/all36Kotlinpayloads/defaulttrue verified AFTER Gradle complete. Mandatory exactstaged review+gate passed,10files368insertions4deletions. Initial CharSequence pinnedAPI compileerror fixed, failure logretained; no failed bundle published.
Evidence /workspace/scratch/native-playlist-save-feedback-{combined,final,compile-failure,stock-paths}.log; integrity.json,review.json,staged.diff,cumulative.diff. Details save-feedback-poc45.md.

## Device checks pending
No authenticated device/emulator here. Existing destination correct title/Undo; preserve preexisting occurrences, only returned new entries removed. Failed/cancelled save no success, native Snackbar hide setting, single queue/ordinary player/row/account unchanged, late response/rotation/queue clear/reorder and playback. User tests confirmation before New fix. No further log requests.

## Workflow
Read /workspace/AGENTS.md and WORKFLOW.md. Noagents/delegation/modelrouting. Onefixpergatedcommit/MPP. Source+buildbranch uploads already authorized; no newpermission.21.20.405only/defaulttrue. Currentgate naturallystaleaftercommit; regenerate for nextstagedtree. Previous checkpoint STATE-history-before-poc45.md and STATE-history-before-poc44.md.
