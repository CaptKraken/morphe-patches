# Row menu bounded follow-up
2026-10-19; gpt-6.1-sol/medium investigation complete, production unchanged.
Tasks: saved native endpoint fixtures, Download/Share payload proof; native service-menu
removal dispatch; typed bfzy row ID and exact shared extractor path.
Existing metadata fixtures located under scratch/native-playlist-inspection/metadata,
including ANDROID-mix.json, native-mix-full.pb, native-playlist-menu.pb, next/search
JSON and Element cards. No network requests yet. Read conditional row-menu-plan.md.
No new build, device result or implementation is claimed.

## Checkpoint: fixture gaps resolved (2026-10-19)

Existing ANDROID-mix.json contains50 native playlist rows. All50 Share entities
URL-decode/base64-decode exactly to protobuf string field1(videoId); no guesses.
JSON uses legacy shareEntityEndpoint90650344 (bhiv): serialized entity1, panelType6.
Native current service equivalent387863284 (bjgz) also forwards serialized entity1
unchanged to same share/get_share_panel; prefer service variant to preserve existing
Open system share sheet hook (arpm.b). Need distinguish observed legacy fixture from
current equivalent handler evidence; no claim server returned387863284 in that file.

native-swipe-row.pb (existing stock response) proves native Download73080600:
video_id1='4N1iwQxiHrs', action2=11, playlist_id5='RDdQw4w9WgXcQ',
on_add_command8 contains GetDownloadActionCommand382320942, video_id1 same,
params5='CAIQAA%3D%3D', is_cross_device_download13=false. ANDROID-mix.json,
missing-card-search.json and videoWithContextModel.json repeat same command route
for distinct exact video IDs; search/cards omit playlist context. iax.e checks
presence of field8 (bit32) first in ACTION_ADD: if present dispatches that command
instead of requiring local offlineability. Native inference11 resolves action and
calls iax.e (iav case2) for normal ADD. Downloads override still hooks iax.e before
its native logic. This solves initial ID-only offlineability uncertainty through
native onAdd fetch, without manufacturing eligibility or forcing an override.

Native bottom-sheet service action path: aqxt.b->d builds awgd(ahnn,map,logger),
assigns to aqxv.ai. aqxv.aS(aqxp) resolves service/navigation endpoint through
aglq.ba/aZ, copies awgd.c into new HashMap, validates present endpoint, handles
optional toast/toggle/logging, invokes ahnn.c(endpoint,map), then calls native
bl() dismissal. Saved row-menu-bottomsheet-dispatch-dex.txt code-unit244 is invoke,
247 is native dismissal. Hook immediately before invocation, reuse endpoint v2 and
map v3, and branch consumed commands to247 (not return) to preserve native close.
Existing ButtonRenderer onClick hook does not cover this menu path. Production
must derive references/registers/indexes from fingerprints, never hardcode these.

## Final bounded findings / implementation-ready direction

The remaining endpoint gaps are resolved sufficiently for implementation against
21.20.405, with device/server behavior still pending. No new network fetch needed.
The fixtures were already present; no abandoned branch was read.

Download command parser: row-menu-followup-java/bbgu.java and mdp.java prove
382320942 has video oneof string1 and params string5. mdp uses existing account,
format, safety-check, attestation/native request plumbing; server response determines
actual native action. Thus emit the fixture's minimal ID+params onAdd command inside
OfflineVideoEndpoint(action11, on_add8). **Do not copy playlistId from Mix**; search
fixtures prove the same path without it. **Do not add field13** simply because older
fixture has explicit false: current bbgu's known schema ends at12; false13 is unknown
and unnecessary. No local eligibility, Premium claim, or direct downloader call.
Existing Downloads handler may infer Pause/Resume/Retry and show native controls;
keep original behavior. Original native handler+configured overrides own policy.

Share native binary fixture also proves legacy90650344 body field1
'Cgs0TjFpd1F4aUhycw%3D%3D' and panel_type6=1 for video4N1iwQxiHrs. Its payload
matches the50 JSON assertions. Use a tiny explicit serialized entity message
(video_id1), generated protobuf bytes, then URL-escaped base64 as observed. Do not
add unrelated opaque params. For current387863284 use serialized_share_entity1
with the same proved payload and panel enum2=1 (UNIFIED); arpm.b/aigg forward it
as serializedSharedEntity to the same service used by legacy arqi. This route
engages existing Open system share sheet hook; the legacy90650344 direct fragment
route would bypass that hook. This is supported-handler equivalence rather than
claiming the fixture server returned387863284. Actual native panel opening/server
acceptance is a phone check, not established by parser/byte assembly alone.

## Exact row ID extraction / shared changes required

FindMenuContext tool finds exactly `juo.b(azga,Map)` for the four shared
FeedFlyoutBufferObjectFingerprint strings. Existing row overflow oxk.h does not
call it: it builds autt.m(endpoint.tag=bfzy,interaction_logger) and invokes aqxt.b
directly (row-menu-open-dex.txt). Therefore a typed bfzy bridge alone is insufficient.

Minimum feature-local hook: resolve oxk.h from row binder's type, verified native
menu field and aqxt.b relationship; immediately after native Map move-result,
call a queue extension helper which checks QueueRow owned and delegates to existing
FlyoutUtils.extractFlyoutIdFromMap(map). Register is derived from that move-result;
no parameter cloning/new retained map is needed. Stock code later reuses p1, so
hook near native map creation, not a late parameter read. No listener replacement.
Add FlyoutMenuVideoIdInterface / patch_getVideoId using bfzy's discovered public
video ID string field (p / proto10), following shared FlyoutPatch convention.

A concrete shared gap remains to fix with that small implementation: current
extractFlyoutIdFromObject's typed-interface branch sets flyoutVideoId only, while
OpenSystemShareSheetPatch prioritizes stale flyoutPostId/flyoutPlaylistId. Existing
reset happens only after menu dismissal via asynchronous delay (100ms with System
Share enabled); adjacent/nested opens can retain previous context. Recommend make
the existing typed-video extraction path establish exclusive video context by
clearing conflicting playlist/post/comment IDs (and any appropriate per-menu flags
according to actual consumers) when a valid nonempty ID is supplied. This touches
FlyoutUtils plus its existing history/You playlist typed bridge consumers; inspect
those paths in review. Do not add queue-specific shared dependency/state or alter
OpenSystemShareSheetPatch itself. Primary notified of this concrete shared scope
before implementation. Verify native/disabling fallthrough and old popup context.

## Removal dispatcher implementation boundary

Use aqxv.aS native bottom-sheet dispatch, not deprecated aqxw.onItemClick. aqxw
is generic popup counterpart and is not the direct aqxt.b row path; no speculative
popup removal hook is needed for verified21.20.405. Existing shared initializer
fingerprint can resolve aqxw but this does not prove it controls aqxv.
Scope discovery through aqxt's stable MenuSheetDialogFragment factory relationship
(return type aqxv), then match its single item-action handler through known menu
entry type and endpoint dispatcher/map-copy relationships. Required capability
must match uniquely. Native DEX244 (research-only index) consumes v2 endpoint,v3
copied map and then native bl() closes. Before that invocation delegate to renamed
existing local remover; when consumed jump to original dismissal instruction.
This preserves logging, validation and dismissal while preventing native hide/edit.
For unrelated endpoints restore native endpoint type and invoke unchanged.
If an injection helper requires an existing target label, use actual instruction
reference / branch helper; never assume cached absolute position or v0 is free.

## Read-only evidence and limits

Created row-menu-fixture-evidence.txt (actual parsed native endpoint trees and50
Share entity equality assertions), row-menu-bottomsheet-dispatch-dex.txt,
row-menu-open-dex.txt, row-menu-infer-callback-dex.txt,
row-menu-shared-context-match.txt, row-menu-service-type-users.txt,
row-menu-download-action-strings.txt, row-menu-followup-java/{bbgu,mdp,bhiv,juo,
iav,aqxw,awgd}.java and decompile logs in scratch/native-playlist-inspection.
Every InspectNative process completed successfully with known decompile warnings.
Stock DEX backs dispatch/branch/register assertions. Fixture helpers are scratch,
not production tests. No actual source test/build/APK application/device check in
this investigation. git status remained clean. Native Premium removal wording
remains unverified and is not a blocker to reuse localized Remove.

Primary explicitly approved the shared extractor scope after inspecting consumers:
typed exact video context clears stale playlist/post/comment IDs in existing
FlyoutUtils interface branch; queue-local native menu-open hook calls that shared
extractor. Preserve nonvideo extraction behavior. Validation must include existing
history/You playlist typed callers, old popup/video contexts, adjacent playlist or
post-to-queue menu opens, Downloads/Open system share sheet both setting states,
and queue-unselected. Do not add a queue-global menu ID or bypass settings.
