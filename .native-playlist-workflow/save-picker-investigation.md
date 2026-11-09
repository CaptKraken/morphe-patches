# Native Save to… picker investigation — 2026-10-20

User supplied older official tutorial screenshots plus CURRENT phone screenshot:
Save to… sheet, thumbnail rows/visibility labels/bookmark toggles, New playlist at
bottom. Wants reuse player Save UI for entire queue if directly invocable. Play last
explicitly deferred. No implementation/build this research; source clean PoC35
559a3354f9f46df4217efb99810735c61fa96ee4.

Verified current patch behavior:
- NativePlaylistQueuePatch.playlistMenu emits CreatePlaylistDialogEndpoint85650004
  with repeated videoIds1: NEW playlist only, skips chooser. Not the requested UI.
- Existing rowMenu Save to Playlists emits AddToPlaylistEndpoint69487224 (axvo),
  single videoId1. Native lpp.ij reads single ID -> ailp list -> pbv list renderer.
- ailp native route playlist/get_add_to_playlist, List a supports repeated IDs;
  request bcnw fields context1/repeatedStringvideoIds2/bool4. Invariant diagnostic:
  GetAddToPlaylistServiceRequest must have videoIds XOR playlistId. Code's
  playlistId branch optimized away in this native request implementation.
- lpp inflates playlist_add_to_fragment, uses native list/pbv. This is a legacy
  concrete controller; do not assume it renders the exact current screenshot.
- Current native SaveToPlaylistListEntityModel bgyu and update resolver diagnostics
  exist, but these describe saved-playlist state, not proof of modern sheet entry.
- Captured public signed-out Android action-bar Save button resolves to sign-in
  rather than an authenticated picker command. It does not establish the modern
  authenticated entrypoint or batch payload. No account credentials/logs requested.
- Public endpoint probes with two IDs returned HTTP400 Request contains an invalid
  argument. An initial exploratory unsupported addToPlaylistPanelType/sheetId guess
  was discarded; minimal native request also400. Do NOT infer exact auth reason or
  claim successful current-sheet API invocation from those requests.
- Public YouTube.js protocol reference independently supports AddToPlaylistService
  endpoint videoIds[] or [videoId], optional playlistId/params/excludeWatchLater:
  https://github.com/LuanRT/YouTube.js/blob/main/src/parser/classes/endpoints/AddToPlaylistServiceEndpoint.ts
  AddToPlaylistEndpoint aliases it in that WEB-oriented library; does not mean
  Android axvo's one-field proto accepts repeatedIDs or invokes current modern UI.

Outstanding practical clarification requested via async question: Does Save to
Playlists in our existing queue ROW menu open the SAME current Save to… sheet?
Options yes / older / not tried. No reply yet at checkpoint. This distinguishes
whether existing invocable row route is already the correct current UI. Do not
ship first-ID-only header action or silently invoke known legacy UI. Backend list
support is necessary but not proof whole-queue picker/new-playlist actions receive
all IDs; native pbv stores one ID for controller context and callbacks also need
tracing if using that old route. Prefer server-supplied modern command/whole IDs and
existing native dispatcher, no custom account picker/auth/network rewrite.

Scratch: native-playlist-inspection/row-menu-java/{lpp,pbv,ailp,bcnw,axvo,hsk,bgyu}.java;
new anfh/deep-link video_ids is unrelated playback URI parsing; excluded false lead.
axvm/axvn/axvs/axvt/axvu neighbor proto exploration did NOT identify modern batch
picker entry; no production descriptors guessed. save-ui-strings/diagnostics/routes
logs and public endpoint reference files remain scratch. No tests/build needed for
read-only investigation. No device/emulator, no legacy-panel parity claim.

## User clarification and native Premium queue scope

User answered: our ROW Save opens the older picker shown in @YouTubeViewers Short,
NOT the current normal-player Save to… screenshot. Treat this as phone confirmation
that axvo/lpp is legacy; do not reuse it claiming modern UI parity. Pending question
is resolved. User then explicitly asks to inspect YouTube's OWN Premium queue save,
not our queue or just the ordinary player Save action.

Stock Premium/shared playlist-header path traced: ppo binds accepted bfzu.x menu;
oyn case13 passes that menu untouched to aqxt.b with original bfzu endpoint tag and
native logger. Menu item commands are supplied in accepted watch-next panel model.
Native bfzu.x writes found only ppo.b/pqj.gt update playlist LIKE-state menu entry,
not create a hardcoded Save command. Thus APK-only header code does not establish
which picker the current authenticated Premium queue Save item invokes. Existing
native-mix-full/public captures are MIX/standalone, NOT authenticated Premium queue;
never present their Save entry as Premium-queue evidence. No attached Premium
session/device or Premium queue menu response exists in current workspace. Current
official help confirms existing/new destination selection, not modern vs legacy
visual variant. Cannot honestly confirm exact current Premium picker from available
samples. No user log/credential request or production edit made.

Native files ppo/oyn/pqj inspected, premium-header-menu-writes.txt saved. oyt is
GENERIC save toggle presenter, not Premium queue header; excluded false lead.
ibi handles saved-playlist entity updates, not proof of picker opening action.

## Current user-selected target (2026-10-20)

User supplied https://www.youtube.com/watch?v=D8MWEdD-3Bg and reports Premium
queue uses the modern normal-player Save button sheet. Explicit latest direction:
“we should just follow the player save button.” Follow that native entrypoint.
The visual target is settled; earlier inability to independently establish its
visual variant is superseded by user evidence. Still verify native invocation
and whole-queue IDs for both existing destinations and New playlist. No source
change, build, or claim of authenticated-command verification made this turn.

## Focused planning pass (2026-10-20)

Existing RatingActionsHook/onWatchNextResponse provides pre-filter access to the
normal player Save view model; no separate view click hook is needed merely to
read it. next_response.proto already has Save tag484598023 but only entity key2.
Public JSON has Save toggle default/toggled onTap serial command envelopes.
QueueRatingActions.endpoint flattens to first innertube command: unsuitable for
blind reuse because Save may require full sheet/serial/on-show sequence. Generic
oyt is older view presenter, dispatches server endpoints with endpoint.tag; not
evidence of the modern Elements Save invocation. Commands includes partial
showSheetCommand443434441, distinct from modern Share's showActionSheet contract.
No authenticated modern batch contract discovered in this pass. Implementation
plan saved save-picker-plan.md; no production edits or new builds.

## Authorized implementation attempt, remaining7%

No safe production change identified. Additional stock asset scan:418assets,
no literal get_add_to_playlist/saveToPlaylist/addToPlaylist/videoIds/
add_to_playlist/playlistEditEndpoint matches. EML assets exist but the scan
does not prove absence of an encoded modern command. Save view-model
484598023 literal scan also found no native generated-type instruction anchor;
not proof of absence (Elements may be data-driven). FindLiteral scratch helper
fixed to include NarrowLiteralInstruction as well as wide. Initial javac without
activate failed (not found); rerun after activate compiled/scanned successfully.
Public grep.app query addToPlaylistServiceEndpoint:16results mostly YouTube.js
and web types, no Android modern sheet batch schema. saveToPlaylistCommand:
only unrelated music-bot class. playlistAddToOptionRenderer:14results web/legacy
parsers and existing shared request helper, no missing current-sheet contract.
Do not repeat these searches/probes as new investigation. No production edit,
source test, build, commit or phone bundle. Need authenticated normal-player
Save command chain AND its full-queue existing/new destination payload contract,
not just generic ID-list backend support. User declined further logs: do not ask.

## BREAKTHROUGH: modern native panel command identified

Modern Save is DIRECT showSheetCommand -> panelLoadingStrategy.requestTemplate,
panelId PAadd_to_playlist, params URL-encoded URL-safe base64. Existing public
ANDROID-mix.json contains it behind signInEndpoint.nextEndpoint on row0 menu
item3. Prior research missed it by looking only at normal-player action-bar
sign-in nextEndpoint (which reloads watch), rather than feed/row sign-in wrappers.
Other stock search/browse captures have same modern Save route. This establishes
a modern native Save entrypoint, NOT just a legacy service request.

Example params -gYNCgtkUXc0dzlXZ1hjUQ%3D%3D -> hex
fa060d0a0b6451773477395767586351 -> nested field111.field1 = dQw4w9WgXcQ.
Native path: aili.a creates get_panel request ailh; aili.e(bfnf) reads bfnf.c
panelId / d params. bfnf generated schema fields2 string panelId,3 string params.
ailh request writes panelId/params/formData/query/continuation; J/K normalize
through k. Native show-sheet handler icr.b case1 reads bhts.c bfnd; aozm.J
handles async sheet loading from bfnd.f bfnf via aili.e -> aili.g -> native
callback update. No custom UI/network account handling would be necessary for
the verified single-video entrypoint. next_response.proto already partially
models showSheetCommand443434441 and PanelLoadingStrategy inlineContent1.
Need inspect bfnd schema before declaring requestTemplate field number (likely2,
not yet verified here). Do NOT confuse ShowSheetCommand with Share showActionSheet.

Actual READ-ONLY public get_panel probes using exact current ANDROID context:
- single params111.1=dQw4w9WgXcQ ->401 UNAUTHENTICATED, explicitly sign in required.
- repeated params111.1 with dQw4w9WgXcQ +9bZkp7q19f0 ->400 INVALID_ARGUMENT.
- same two cases with raw base64 rather than URL-escaped padding ->same401/400.
- empty nested111, unknown99 ID alone, repeated field2 IDs without field1 ->400.
These are NOT proof of a working batch schema. Repeating field1 is not a valid
verified route; do not ship it. Unknown-fields/control error messages give no
field-level details. No further brute-force field guesses are justified.
Scripts probe-save-panel{,-raw,-controls}.py and metadata/save-panel-*.json.
Native decompiles save-modern-java/{aili,ailh,bfnf,icr,aozm}.java and logs.
Broader native string scan newly found get_panel; modern-save-routes.txt.

Public stock Android browse capture for playlists PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI
and PLMC9KNkIncKtPzgY-5rmhvj7fax8fdxoj only found single-row Save requestTemplates.
Their header menus did not supply a verified whole-queue payload. Full responses
metadata/playlist-save-*.json, capture-playlist-save.py, playlist-save-capture.log.
Public grep.app PAadd_to_playlist:5results, all single-video examples (NewPipe
fixture/userscript/other JSON). AddToPlaylistParams/AddToPlaylistPanelParams/
SaveToPlaylistParams and combined getPanelEndpoint/showSheet+addToPlaylist queries
provided no batch schema. Exact source UI now found; remaining question is only
batch parameters or another native batch panel route.

No source edits, builds, commits or bundle. Do not claim playback/device test.
User has asked to keep trying; avoid repeating earlier false total-blocker claim
that modern invocation itself is unavailable. Modern single-item invocation is
identified; whole-queue semantics remain unverified.

Native bfnd dynamicMethod schema also VERIFIED requestTemplate field2 (bfnf);
inlineContent field1. Dump modern-save-loading-schema.txt. Modern single Save
can be represented using existing dispatcher with minimal proto fields; no new
bytecode hook is required just to open this panel. Batch payload remains missing.

## PoC36 candidate, protocol verification still pending

See STATE.md and save-modern-candidate-review.md. Experimental scalar111.1 CSV
header action now implemented as test candidate, with explicit blocking finding
and NO source commit. Real APK/tests/build pass, not proof of authenticated Save.
Actual ShowSheet is bhul443434441 (not bhts441221881 drawer); schema verified from
DEX. Scalar2 accepts valid playlistId source;3/4/5 repeated-ID-only controls400.
Scalar1 CSV and invalid-text controls BOTH401, so no batch proof. Do not conceal
this caveat or rerun these probes. Await phone tests before completing gate/commit.

## PoC36 PHONE FAILURE — CSV hypothesis rejected

User: modern UI changes correctly; existing destination gives generic error,
New playlist creates empty playlist. Scalar111.1 comma-separated IDs are NOT
a batch source. Candidate5files removed, clean PoC35 restored. No source commit
or gate bypass. Archived patch is failure history ONLY, never auto-restore it.
Modern UI route remains useful; need actual batch source, not CSV or repeated1.

## Post-failure structural discovery in progress

FindBatchSaveEndpoints scans generated navigation extensions registered on azga
for repeated-string first fields by decoding RawMessageInfo descriptors (not guessed
field positions). Candidates aygk444949225,azuu124747980,bduy407683780,
bhdv75581109,bieo68997349,bjeq68997401 plus knownazutCreateDialog. Source/script
FindBatchSaveEndpoints.java, output native-playlist-batch-endpoints.txt and
-batch-endpoint-handlers.txt. Candidate native handlers idf/iyz/izd/izc/iyy now
being inspected. Do NOT call these Save endpoints until actual handlers establish
semantics. Native repeated ID inputs may be a different command from single-video
get_panel. Additional111 nested-field3/4/5 message controls400, no batch discovery.
No new source edits/builds after withdrawing PoC36.

Further post-failure scans: all direct NavEndpoint repeated-string fields (not
only first field) yield18candidates; all-batch-endpoints.txt/handlers.txt. Excluded:
axmz account linking, bhdv/binc SMS, bieo/bjeq offline download/remove, beuy request
usesnotification context, bile generic modal (not a Save action), azkm comment/post
creation route, azut known CreateDialog, azuu CreatePlaylistService. Remaining
acfg/ajcu/idj/afla handlers being inspected (bkho watch-playlist-like candidate
may expose temporary-list source). New schemas in save-batch-java/.

Field111 root scan: bcca oneof111 mapsbbbf withtwo strings1/2, but native uses
show bcca is LOGGING CLIENT EVENTS (LogSamplerImpl, hundreds event loggers). This
is a COINCIDENTAL same-number/two-string shape, NOT GetPanelParams. Do not use it
as Save schema evidence. Sparseother111matches clientInfo/config, not Save proven.
Root-batch-parameter mapping scratch is a false lead; no Native Save batch claim.
Native GetPanel opaque params still not decoded by identified client class.

## Post-failure bounded investigation outcome

The direct-navigation repeated-string schema scan found18candidate types. Inspected
handlers were unrelated features: account linking, SMS, offline actions, creation,
notifications, modal/permissions, transactions, URL handling. No modern batch Save
command verified. bcca111/bbbf schema match is CLIENT EVENT LOGGING, established by
LogSamplerImpl and hundreds of event-log users, not an actual GetPanelParams schema.

A different temporary-list source was verified read-only: navigation/resolve_url
with watch_videos?video_ids=A,B returns watchEndpoint.params containing repeatedIDs5.
Passing the server-issued endpoint videoId/params/playerParams into next returns
TLGG2ygyGf2t5wQyMDEwMjAyNg with EXACT ordered IDs dQw4w9WgXcQ,9bZkp7q19f0.
Scripts resolve-save-source.py/fetch-save-source.py; metadata/resolve-save-source.json
and save-source-next.json. BUT get_panel with this real TL in111.2 returns400.
Thus this direct temporary-playlist bridge is NOT working/verified; no build made.
TL header contains only disabled Sharing unavailable, no batch Save command.
Direct www.youtube.com/watch_videos fetch is proxy tunnel403; no bypass attempted.
Current public Harmony-Music/NewPipe export watch_videos links, but that does NOT
prove the modern native picker supports those TL sources. Don't repeat these probes.

Source cleanPoC35. Local compiled outputs are still FAILED PoC36 until rebuilt;
never copy local patches/build/libs MPP as a rollback. Published PoC35 is known
rollback bundle. No new source tests, commits or bundle after removing candidate.
Missing capability remains actual whole-queue Save input/scoped native batch bridge.
No device logs requested, no source commit gate bypassed, no working Save claim.
