PHONE UPDATE: PoC36 modern UI works but CSV batch input fails existing Save and creates empty New playlist. Candidate withdrawn; clean PoC35 restored. Do not restore archived candidate. Next: inspect native repeated-string NavigationEndpoint command schemas and their handlers.

# Whole-queue native Save to… — implementation plan

Checkpoint: 2026-10-20. User subsequently authorized an implementation attempt
with remaining7% budget. It did not establish the authenticated modern batch
contract. No agents, source edits, builds or bundle; implementation still pending.
Repo /workspace/morphe-patches, feature/youtube-native-playlist-queue,
HEAD 559a3354f9f46df4217efb99810735c61fa96ee4 (PoC35); clean.
Read STATE.md and save-picker-investigation.md first; do not repeat completed probes.

## Agreed behavior and scope

Queue header More > Save playlist opens YouTube's modern player Save to… sheet.
Choosing an existing playlist saves the entire queue; New playlist starts native
creation with the entire queue. Preserve queue order and duplicate semantics
unless native behavior proves otherwise. Saving/dismissing must not change current
video, position, queue order, selection, or playback. Ordinary/account playlists
and the normal player Save action remain native. Clear queue remains unchanged.
One feature/commit. Migrating each row's legacy Save action is a separate step,
not bundled unless the user asks. No custom picker, account listing, or auth client.

## Established evidence

- User identifies current Premium queue picker as the modern player Save sheet,
  with https://www.youtube.com/watch?v=D8MWEdD-3Bg. Visual choice is settled.
- NativePlaylistQueuePatch.playlistMenu currently sends CreatePlaylistDialogEndpoint
  (85650004), repeated IDs field1: new playlist only. Replace this header action.
- rowMenu sends AddToPlaylistEndpoint (69487224), single ID field1; phone-confirmed
  legacy picker. Do not use this route to claim modern UI parity.
- Native header ppo/oyn dispatches the accepted response menu unchanged. The modern
  Save action is supplied by server data, not hardcoded in that header.
- RatingActionsHook.installRatingCapture already observes parsed watch-next before
  action-bar filters. NativePlaylistQueuePatch.onWatchNextResponse is the existing
  read-only hook. QueueRatingActions.fromElement already handles both action-bar
  model layouts. Reuse this source rather than adding a click listener/view hook.
- next_response.proto already identifies PrimaryButtonViewModel Save as
  addToPlaylistButtonViewModel tag484598023. Its AddToPlaylistButtonViewModel only
  declares entity key field2; the toggle/action fields are not currently exposed.
- Saved android-next-full.json shows Save.toggleButtonViewModel -> toggleButtonViewModel
  -> default/toggledButtonViewModel.buttonViewModel.onTap; onTap is a serial command.
  Signed-out default contains logGestureCommand then signInEndpoint. This sample
  proves the envelope, NOT an authenticated modern sheet or batch payload.
- Commands schema already knows innertubeCommand169495254, serialCommand170382688,
  showSheetCommand443434441. Its showSheet schema is partial. Modern Share uses a
  different verified showActionSheet/onShow/service sequence; do not guess Save
  uses the same service fields or panel-type enum.
- QueueRatingActions.endpoint extracts the first innertube command recursively.
  Do NOT reuse it blindly for Save: it can discard surrounding sheet/serial work.
- Legacy ailp request supports an ID list, but lpp/pbv controller stores a single
  video ID. This is not proof the modern sheet's existing/new destination actions
  accept a batch. Public minimal request probe returned400, no diagnostic cause.

## Ordered implementation work after reset

1. Finish the native BATCH contract first (no production edits yet).
   UPDATE: modern single-item command identified in stock captures:
   showSheetCommand -> panelLoadingStrategy.requestTemplate with
   panelId PAadd_to_playlist; params nested111.1 single video ID.
   Native requestTemplate bfnf fields2 panelId/3 params; aili/ailh get_panel;
   icr.b(case1)/aozm.J native sheet loading. See investigation breakthrough.
   Single-ID public request returns401 sign-in-required; repeated111.1 returns400.
   Modern invocation discovery is done; do not repeat it as an unresolved blocker.
   Still need actual batch schema or a verified batch panel route, plus both
   destination paths. Do not infer repeated1 is valid just from backend ID lists.
   Inspect stock21.20.405's Save view-model proto and the command/handler chain
   reached by the normal player's Save action. Establish actual field numbers,
   sheet-opening command, request payload, destination mutation commands and
   New playlist callback. Follow references from discovered types; no hardcoded
   obfuscated production descriptors, guessed tags, byte substitutions, or new
   speculative endpoint probes. Existing generated proto/capture/research tools
   are in scratch/native-playlist-inspection. Public signed-out response is an
   auth gate, not a valid authenticated action template. If server-only payload
   details remain unavailable, record the exact missing contract before editing;
   do not ship a known legacy/current-video-only substitute.

2. Choose the smallest verified native invocation.
   Prefer expressing the discovered batch-capable modern command in the queue
   header MenuServiceItem/NavigationItem and letting existing dispatch execute it.
   If that command must be reused from the accepted player Save model, extend only
   the needed proto fields and capture it through the existing response hook.
   Preserve complete required command envelopes and native unknown fields. Bind
   captured action to its response video/account/controller context; prefetches
   must not overwrite the current action, and stale async work must not reattach
   a cleared/replaced queue. Do not introduce capture/cache if a verified native
   batch command can be constructed directly. Capture a queue ID snapshot at the
   actual Save invocation boundary; native sheet must use one consistent snapshot
   for existing playlist and new playlist, even if queue changes while open.
   Verify command carrying context and sheet-dismiss ordering in PlaylistMenuHook
   rather than synthetically clicking the player button (which targets one video).

3. Implement only the header Save replacement and necessary native contract.
   Expected files: NativePlaylistQueuePatch.java; playlist_queue.proto and/or the
   minimal next_response.proto Save model fields. A small action helper is justified
   only if typed command parsing/state is actually needed. Kotlin/Fingerprints.kt
   changes only if existing capture/dispatch cannot reach the verified native path.
   Keep native sign-in, account playlist permissions, membership states, errors,
   confirmation, localization, theming and new-playlist UI. No hidden network
   rewriting, saved-state spoofing, custom picker or invented fallback.

4. Review, validate, then deliver one feature commit/phone bundle.
   Focused tests: full ordered ID list vs selected ID; one and several videos;
   correct command serialization and unknown-field/envelope preservation if changed;
   stale action/queue replacement and clear while picker pending if capture needed.
   Avoid broad test scaffolding or regex/source snapshots. Trace overlap with Hide
   video action buttons, compact/restore action bar, SaveToWatchLater, native menu
   dispatch, clear, account switching and queue-disabled/unselected paths. Both
   action-bar layouts must work, even if the visible Save button is hidden.
   Run :patches:test. Native/proto changes require real21.20.405 patch application
   and appropriate DEX/resources verification using existing validatePlaylistQueue,
   combined actual overlaps and unselected when shared hooks/contracts change.
   Run checkStringResources only if strings change; prefer existing native labels.
   Final buildAndroid/default-selection check after tests; verify archive/MPE/classes.
   Complete cumulative/staged review and mandatory exact-tree precommit gate.
   Then one conventional feature commit and next authorized PoC36 bundle.

## Phone acceptance (pending; no device available here)

- At least three distinct IDs, with selected video in the middle: header Save
  opens current modern sheet, existing destination receives every ID in order.
- New playlist receives all IDs; Watch Later, existing membership and repeated Save
  follow native behavior without silent partial saves or artificial duplicates.
- Cancel/back, signed-out/sign-in, account change and native request failure leave
  playback/queue intact; no pause, reload or altered timestamp/selection.
- Portrait/fullscreen header entry; hidden/compact action-bar settings; change or
  clear queue while sheet is open; reopen after clear/recreate; ordinary/account
  playlist and single-video Save unaffected.

Research confidence: entry data source and current code gap verified; modern
batch command/request and destination mutation contract NOT yet verified. This
plan is actionable but that first contract check remains an implementation gate,
not a claim that the whole feature is already invocable.
