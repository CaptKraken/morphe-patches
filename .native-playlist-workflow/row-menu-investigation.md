> CORRECTION after PoC22 phone failure: Video.menu is field19, NOT18. Initial
> mapping below was wrong. Field18 is native text; it caused missing row overflow
> buttons. PoC23 fixes the declaration and checks the actual captured native row.

# Native playlist queue row menu investigation

2026-10-19; read-only source investigation at feature/youtube-native-playlist-queue,
HEAD 6239139bbfcf8ceaeb216df2a325aba02802c082, stock YouTube 21.20.405.
Scope: native row overflow with Save video to playlist, Download video, Share video;
user subsequently includes native remove/hide for nonplaying rows. Premium Play next
is excluded. No production edits/build/commits or phone tests in this stage.

## Observed native path and minimal direction

Current renderer (`NativePlaylistQueuePatch.java:913+`) supplies no row menu. Stock
`bfzy` dynamicMethod maps its `r:beox` menu to protobuf Video field **18**. Existing
`playlist_queue.proto` Menu already represents the same beox/beou wrapper used by
header overflow, so reuse it as Video.menu=18. Stock oxk.g binds menu presence to
its existing `three_dots_menu` ImageView and attaches native hul case12 listener;
oxk.h passes beou to aqxt.b, retaining exact bfzy as endpoint.tag plus row logger.
No new custom UI/listener replacement or navigation backend is indicated.
Sources: scratch/native-playlist-inspection/java/{bfzy,oxk}.java (bind around580,
h around699); fresh row-menu-java/{bfzy,beox,beou,beor,beow,aqxt}.java.

MenuNavigationItem existing field66441108 is beos (text1/icon2/endpoint3).
Menu service item **66441155** is beow, text1/icon2/serviceEndpoint3 (other
fields omitted). This gives a native menu item schema without new UI plumbing.

## Endpoint facts and remaining blockers

- Save existing account playlist: **AddToPlaylistEndpoint extension69487224**, body
  video_id string1 (`axvo`). `lpp.ij` reads it from navigation_endpoint and adds that
  exact ID to `ailp`, which requests playlist/get_add_to_playlist; pbv renders native
  selectable account playlists. This differs from the header's existing
  CreatePlaylistDialogEndpoint85650004, which saves the whole local queue as new.
  Use axvo for the row. Verified stock source row-menu-java/{axvo,lpp,ailp,pbv}.java.
- Download: **OfflineVideoEndpoint73080600** (`bfhx`), body video-id oneof string1,
  action enum2; ACTION_ADD=1, ACTION_INFER_AUTOMATICALLY=11 (`bfhw`). Native iax.b
  reads ID/action then iax.e performs it. **ID+ADD alone is not proven sufficient**:
  e case1 requires offlineability (`bfjk`) from endpoint field4 or native row's
  bfzy.v metadata, unless native feature flag fE permits absent metadata. Our rows
  currently omit this. Alternative OfflineVideoWithOfflineability path idg case1
  also explicitly requires metadata; it is not a magic ID-only shortcut. Need a
  bounded follow-up to establish required offlineability handling/server-issued
  endpoint, or record native Download as blocked rather than invent eligibility.
  Downloads patch hooks iax.e at entry, so external override works before this
  native validation when enabled. Do not bypass it or call external downloader
  directly, and do not force Downloads into dependency selection merely for queue.
- Share current native command: **ShareEntityServiceEndpoint387863284** (`bjgz`),
  serializedSharedEntity string1; field2 enum (default1), field3 sheet ID,
  field4 failure endpoint, field5 client params, field6 spinner config. arpm.b
  sends serialized entity unchanged to share/get_share_panel via aigg. **Exact
  serialized video entity bytes are not established by APK schema alone**. A
  base64 protobuf video-ID payload is plausible but remains a hypothesis; cannot
  advertise working Share from guessing it. No matching writes of bjgz payload
  occur in stock code; likely server supplied. Older bhiv unified-share fragment
  also forwards opaque serialized data and does not resolve this uncertainty.
  Need a bounded follow-up with an existing native endpoint fixture or documented
  verified payload, not unrelated metadata research.

All obfuscated names above are investigation references only, not production
fingerprint anchors. Exact proto field numbers are verified wire schema; new
patch-time bridge discovery must still follow repo fingerprints if needed.

## Removal added by user

Existing hide command is NavigationEndpoint.hideEnclosingAction77297742 with
hack bool1=true; removalAction() already emits it for native swipe. The row menu
can use this same command, same nonplaying render predicate (index != selected
AND ID != currently playing). Activation must retain onSwipeCommand's checks:
exact QueueRow ownership, current published native model, exact row identity,
playing ID, pendingSelection and dragSession. Do not add a second removal owner.
**Existing interception is only installed in NativeButtonDispatchFingerprint's
onClick for ButtonRenderer. Menu service renderer dispatch may use a different
handler**, so adding the same command to MenuItem alone is not proven to reach
onSwipeCommand. Planning needs to trace menu item dispatch and call the shared
local remover after native item validation, preserving unrelated endpoints and
all original listeners. Inspect original control flow before choosing hook.

Wording: searched existing decoded English stock resource names/values (unselected
APK resource output, omitting Morphe-added entries). Native `remove`="Remove" and
`accessibility_remove_from_playlists`="Remove from Playlist" exist. No stock text
"Remove from queue" or "Hide video from playlist" found. Premium/Mix server labels
cannot be verified from these resource strings; the user's screenshot is evidence
for Mix text, not a Premium label. Do not claim Premium wording verified. Native
`add_video_to_offline`="Download video", `accessibility_share_video`="Share video",
`accessibility_save_to_playlists`="Save to Playlists" are available localized labels.

## Interactions to preserve

1. Downloads: existing iax.e hook and EXTERNAL_DOWNLOADER_ACTION_BUTTON setting
   must select external vs native action, with tapped row ID even when unselected.
2. Open system share sheet: ShareEndpointCommandFingerprint resolves arpm.b and
   existing override intercepts it. `OpenSystemShareSheetPatch` derives URL from
   FlyoutUtils (falls back to currently playing video), **not bjgz payload**. Queue
   bfzy is currently neither FlyoutMenuVideoIdInterface nor ProtocolBufferFieldInterface,
   so FlyoutUtils.extractFlyoutIdFromObject cannot derive its ID. Merely using native
   share endpoint risks sharing current playback instead of another queue row. A
   small typed ID bridge/current row context hook needs planning. Existing shared
   FlyoutPatch adds the same interface for history/You-tab playlist models; reuse
   its conventions and preserve patch-unselected behavior and clearing stale IDs.
3. Flyout menu shared filtering/injected buttons: native aqxt menu travels shared
   menu item/popup/bottom sheet machinery. FlyoutUtils observes buttons and adds
   queue/copy/etc actions based on extracted IDs. Do not suppress/reorder these or
   force the queue's three requested entries over existing user hide settings.
   Need inspect actual menu initializer overlap before editing. PlayerFlyoutMenu
   litho settings filter itself is primarily player settings; native row menu is
   beou path, so blanket claims that every litho rule applies are unsupported.
4. Account playlist/save paths and whole-queue header Save remain native; local
   removal interception must consume only owned hide commands. Existing row click,
   reorder, swipe, rating and notification/backend ownership must remain effective.

## Smallest proposed delivery and validation

Likely production changes: playlist_queue.proto + renderer methods, localized
existing resource references; minimal typed/hook work only for concrete menu
context/removal dispatch gaps. Do not add generalized action frameworks/caches.
Do not proceed as though all four actions are ready: Download offlineability,
Share serialized entity, and actual menu removal-dispatch hook are unresolved.
Primary/planner can select a bounded additional discovery question or narrower
working increment, without inventing device success.

Focused behavioral tests should verify per-row targeting, omit removal on selected
or currently playing rows, stale menu removal guard, queue state unchanged for
Save/Share/Download. Wire schema tests should be native-parser resolution, not
source-text snapshots. Real APK application/SDK DEX+resources required if injections
change, with Downloads + Open system share sheet + actual shared flyout overlaps,
and queue-unselected. Phone checks: menus on playing/nonplaying rows, native Save
chooses tapped video, native Download with override off/on, Share with override
off/on targets tapped ID, removal preserving active playback/order, stale-open menu
once selected, ordinary account playlist unchanged. No attached phone or new logs.

## Evidence created this stage

/workspace/scratch/native-playlist-inspection/row-menu-java/*.java
row-menu-{decompile,dispatch-decompile,endpoint-decompile,schema-decompile}.log
row-menu-{strings,commands,save-callers,share-callers,endpoint-writers}.txt
row-menu-{download-dispatch,share-dispatch,save-schema}.txt
Read-only helper FindRowMenuStrings/FindRowMenuFields.java in same scratch directory.
Decompiler emits known inconsistent-code warnings; use saved stock DEX dump for
control-flow claims, not reconstructed branches alone. No actual patch or device
validation ran in investigation.

## Supplemental investigation supersedes initial blockers

See row-menu-followup.md. Existing native JSON/binary fixtures proved video Share
entity encoding and native inferred Download + onAdd GetDownloadAction route;
minimal field schemas and native policy flow established. Native menu removal
dispatch and row-open map hook traced in stock DEX. Implementation can proceed
with bounded proto/render/hooks plus small shared typed-ID context correction.
Initial uncertainties above are historical; no phone success is inferred.
