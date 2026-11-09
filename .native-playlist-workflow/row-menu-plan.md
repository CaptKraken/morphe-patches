# Native playlist queue row overflow: implementation handoff

2026-10-19; gpt-6.1-sol/low planning, baseline6239139bb. This replaces the conditional
plan: row-menu-followup.md resolves its bounded research questions with existing
server fixtures and stock21.20.405 DEX. No further broad investigation is required.
No production edit, build, test, commit or device verification occurred in planning.

## Behavior and ownership

Show Save to Playlists, Download video, Share video in each stock native row menu;
show localized native Remove only when neither selected nor currently playing.
No Premium Play next. Native Remove wording is verified; Premium wording is not.
Preserve stock overflow control, sheet, validation, logging, dismissal and original
listeners. Save/Download/Share target exact tapped video without changing local
selection/order/playback. Queue selection default=true remains.

Native owns account Save, inferred Download/eligibility and Share. Queue extension
owns local removal and its publication. Reuse exact QueueRow ownership, controller
published-model identity, exact row identity, selected/playing-ID checks, pending
selection and drag guards at activation. Consume only local hide commands; all
other commands, ordinary playlists and feature-disabled paths fall through.

## Exact files and sequence

Paths relative to /workspace/morphe-patches. Keep scratch/notes out of tracked diff.

1. `extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/playlist_queue.proto`:
   add Video.menu18, Menu service renderer66441155 (text1/icon2/service endpoint3).
   AddToPlaylistEndpoint69487224(video ID1) goes in existing navigation item.
   Add OfflineVideoEndpoint73080600(video ID1, action2=11, onAdd command8).
   onAdd is NavigationEndpoint.GetDownloadActionCommand382320942(video ID1,
   params5='CAIQAA%3D%3D'). Use no Mix playlist ID, manufactured offlineability,
   cross-device field13, or unrelated parameters. Add ShareEntityServiceEndpoint
   387863284(serialized entity1, panel enum2=1 UNIFIED), and tiny serialized video
   entity(video ID1). Update header-only Menu comment. Use known wire fields only.
2. `extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch.java`:
   build per-row Menu from current entry ID; reuse localized existing labels and
   native icons. Save uses AddToPlaylist; header retains whole-queue creation.
   Download uses inferred11+native onAdd fetch above, preserving native policy and
   existing Downloads entry hook. Share encodes explicit entity proto bytes into
   URL-escaped base64 (follow exact observed fixture escaping); native current
   service handler engages existing System Share override. No endpoint cache,
   metadata/search changes, JSON-to-proto translation, direct downloader/share
   implementation or new global menu ID is needed.
   Reuse hide endpoint construction for swipe/menu and rename onSwipeCommand to a
   shared removal name if helpful, updating both callers. Keep activation guards.
   Add queue menu-open helper: only for owned QueueRow in native endpoint.tag,
   delegate existing FlyoutUtils.extractFlyoutIdFromMap; retain no native map/model.
3. `patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/PlaylistRowHook.kt`:
   add existing FlyoutMenuVideoIdInterface to discovered native video type with
   patch_getVideoId reading its discovered video ID string field (proto10).
   Follow existing FlyoutPatch typed bridge conventions; no obfuscated literals.
4. `.../nativeplaylistqueue/Fingerprints.kt`, `PlaylistRemovalHook.kt` and
   `NativePlaylistQueuePatch.kt` (small new PlaylistMenuHook.kt if that keeps existing
   hook responsibilities clear): install two narrowly scoped native hooks:
   - Resolve row overflow method through discovered binder type, menu field and
     native aqxt.b relationship. Immediately after native map move-result call
     queue helper; derive register from instruction. Existing shared juo.b feed
     extraction is not called by row overflow, so interface alone is insufficient.
   - Resolve native sheet from stable MenuSheetDialogFragment factory relationship,
     scope unique service item handler by native menu type, endpoint resolution,
     map copy and dispatcher relationships. Before original ahnn.c(endpoint,map),
     call same guarded remover as swipe. Consumed commands branch to original sheet
     dismissal, not return; unrelated commands restore native endpoint type and
     dispatch unchanged. Original validation/logging and native close remain.
     Use matchSingle, discovered references/registers, actual instruction labels,
     established branch helpers; no hardcoded research indices/classes/registers.
     Existing ButtonRenderer swipe hook does not cover this service-menu path.
     No popup hook: verified21.20.405 row path uses bottom sheet.
5. `extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/utils/FlyoutUtils.java`:
   primary approved minimum shared change: in existing typed-video extractor branch,
   a valid nonempty exact video ID establishes exclusive video context and clears
   stale playlist/post/comment IDs. Inspect appropriate existing per-menu flags and
   change only what this context requires. Preserve null/empty and nonvideo parsing
   behavior. Do not add queue dependency/state to shared helpers. Existing typed
   history/You playlist consumers must remain correct. OpenSystemShareSheetPatch
   and Downloads implementations/settings stay unchanged.
6. Focus tests in existing `patches/src/test/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/PlaylistQueueTest.kt`
   and `PlaylistQueueApkBuild.kt` as appropriate. Add only distinct behavioral
   boundaries that can be tested without recreating Android/native runtime.

## Evidence and interactions

Follow-up fixtures prove50 Share entities decode exactly to proto field1(video ID);
legacy90650344 fixture route and current387863284 handler both pass same entity to
share/get_share_panel. Be precise: current service route is verified handler
equivalence, not a server-returned387863284 sample or proof of phone/server success.
Download fixture and stock inference/onAdd control flow prove native fetch route
avoids inventing local eligibility. Native handler owns Pause/Resume/Retry decisions.

Downloads: existing iax.e entry override/settings must receive tapped ID. No forced
selection/dependency. Open system share sheet: existing arpm.b interception uses
FlyoutUtils context; stale playlist/post priority requires approved shared fix.
Shared Flyout: retain injected queue/copy/etc buttons, popup/sheet initialization,
settings/hide behavior and native menu ordering; never restore a hidden action.
Do not claim all Litho player filters apply to this native menu. Exact-video bridge
also affects history/You typed callers; verify those and old popup/video contexts.
Account Save/Watch Later and other flyout consumers retain context and policy.
Review transitive dependencies if any shared helper changes; other patches must
not acquire a queue dependency or require queue inclusion. Existing queue swipe,
reorder, rating and header Save remain effective. No playback/notification/control
hook change is planned, so preserve Loop/autoplay/player-button paths and avoid
unrelated changes. Keep disabled/unselected paths ordinary.

## Required validation

Implementation worker records actual commands/outcomes; no inherited result counts
as validation of changed source. Run :patches:test and :patches:buildAndroid; run
:patches:checkStringResources if strings change (prefer native strings).

Native parser verification on stock21.20.405 must parse generated menu and each
endpoint, assert actual per-row IDs, decode Share payload against saved native
samples, and inspect Download onAdd against actual sample command. Distinguish
legacy fixture from current Share handler equivalence. Do not use source regex
snapshots as wire proof. Focus behavior tests on removal predicate/activation
boundaries and stale typed context, as practical with current infrastructure;
no getters/implementation mirror tests or broad new framework.

Apply real APK and run SDK DEX/hierarchy/resource checks for queue alone and queue
with Downloads + Open system share sheet selected plus existing relevant related
patch selection. Exercise queue-unselected with those shared consumers. Inspect
actual patched DEX at new hooks for preserved receivers, types/register lifetime,
branch-to-dismissal, original unrelated dispatch and native map creation timing.
Check unique fingerprints and dependencies, default=true, no broad version claims.

Phone-only checks remain pending: Save account/native sheet selects tapped ID;
Download setting off/on, native eligibility and inferred actions; Share setting
off/on correct tapped ID and native server panel acceptance; all rows' menu UI;
Remove absent for selected/playing and stale-open activation rejected during
selection/drag/handoff; playback/order remain intact; swipe/reorder/rating/header
Save unchanged; adjacent playlist/post-to-queue menu context; old popup/history/You
menus; ordinary account playlists and shared hidden/injected items. No attached
device, no new user log request, no fabricated playback/server success.

Next stage: bounded implementation/build by gpt-6-luna/medium using this plan and
row-menu-followup.md, then gpt-6.1-sol/medium review. Primary retains mandatory
staged+cumulative gate, commit and publication authority; no worker commits/pushes.
