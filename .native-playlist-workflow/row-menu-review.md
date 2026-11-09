# Native playlist queue row menu: independent review

2026-10-19; gpt-6.1-sol/medium; READ-ONLY production review at HEAD
6239139bbfcf8ceaeb216df2a325aba02802c082, branch feature/youtube-native-playlist-queue.
Reviewed seven modified tracked files and explicitly read untracked PlaylistMenuHook.kt.
No production edits, staging, builds, commits, publication or device checks performed.

## Blocking corrections before the gate

1. **Wrong interface makes exact row context hook unreachable.**
   NativePlaylistQueuePatch.java:122 declares a new FlyoutMenuVideoId interface and
   onRowMenuOpened:649 requires it. PlaylistRowHook.kt:172-185 installs the different
   FlyoutUtils$FlyoutMenuVideoIdInterface. Nominal interface identity matters even
   though both have patch_getVideoId. No native row implements the new nested
   interface, so extraction never runs. System share can use a previous menu's ID
   or fall back to the current playing video instead of the tapped queue row; an
   existing system-share override can consume the endpoint without opening anything.
   Minimum correction: use the already installed shared interface in this guard
   and delete the redundant nested interface. Retain exact QueueRow ownership.

2. **Row Save label promises a different operation.**
   rowMenu:937 uses playlist_save, which stock resources define as "Save playlist".
   This is correct for the unchanged header but wrong for AddToPlaylistEndpoint
   containing one video ID. Existing native accessibility_save_to_playlists is
   "Save to Playlists" and matches the agreed row operation. Use that localized
   native string for the row. Evidence: retained unselected decoded stock resources,
   scratch/native-playlist-validation-tmp/playlist-queue-unselected3729320713886670569/
   apk/resources/package_1/res/values/strings.xml:185,2032. No new string needed.

3. **Required dismissal discovery silently chooses an unverified candidate.**
   PlaylistMenuHook.kt:91-98 picks the nearest single-register noarg void call in
   the next six instructions via minByOrNull. It does not check the receiver, opcode,
   relationship to the sheet lifecycle, or terminal control flow. This is not a
   proven dismissal contract and fails the required fail-closed discovery rule.
   Current stock DEX DOES have the intended target: original callback is immediately
   followed by invoke-virtual on this sheet's v7 receiver to its native superclass
   bl()V, then return-void. Minimum correction: assert this observed immediate
   terminal relationship, receiver and resolved declaring type/supertype, failing
   clearly on a changed layout; or discover that exact lifecycle method through a
   scoped fingerprint. Do not retain nearest-within-six as a guessed fallback.
   Keep the existing original-instruction ExternalLabel branch. No broad research
   or speculative popup path is needed.

## Small maintainability corrections

- OfflineVideoEndpoint.Action.ADD=11 misnames native inference. Stock
  row-menu-java/bfhw.java defines ACTION_ADD=1 and ACTION_INFER_AUTOMATICALLY=11.
  Wire value11 is correct for the captured endpoint; rename it to
  INFER_AUTOMATICALLY and use that constant in rowMenu. Preserve onAdd fetch and
  native eligibility, not a forced ADD policy. This is naming, not a payload bug.
- Update the proto Menu comment (still says header-only/save-only) and the renamed
  onRemoveCommand comment (still says only swipe) to describe actual shared use.
- PlaylistMenuHook's related factory/sheet scans are bounded by the discovered
  row opener and exact callback interface, so they are not a global host-class scan.
  Nevertheless reusable discovery belongs in Fingerprints.kt under AGENTS.md;
  use named/scoped declarations for the callback and terminal dismissal relationship.
  Improve sheetTypes failure text to include count/candidates and distinguish missing
  from ambiguous, as the other required discovery diagnostics already do. Avoid
  unexplained string conversions where MethodReference already supplies strings.
  These style changes do not warrant unrelated refactors or extra framework helpers.

## Correctness and injection findings

- Actual emitted DEX inspected in native-playlist-row-menu-emitted-dex-inspection.log
  against row-menu-open-dex.txt and row-menu-bottomsheet-dispatch-dex.txt. Row opener
  observes the exact native four-object map result v5 after move-result-object and
  before show-menu dispatch. Original p1 is later overwritten; hook does not use it.
- Sheet service interception occurs after native endpoint validation, context copy,
  optional toast/toggle and media logging. v8 remains callback receiver; v2 is native
  endpoint/result scratch; v3 is initialized HashMap; v7 remains sheet receiver.
  Non-null result is check-cast back to callback endpoint type before original
  invocation. Null branches to original dismissal, retaining other native branches.
  No inserted instruction overwrites a live receiver, splits invoke/move-result,
  creates an uninitialized constructor use, or changes protected control flow in
  the observed methods. This is direct actual-DEX inspection beyond SDK structure.
- onRemoveCommand reuses existing exact ownership, accepted controller-model identity,
  exact native row lookup and selected/current-video/pending-selection/drag guards.
  Unowned and non-hide commands return their original endpoint. Stale owned hide
  events are consumed without native/account mutation. Duplicate ID/current-row
  exclusion is also checked at activation. Failed publication retains original
  model through existing rollback. Header save, swipes and reorder contracts remain.
- Save contains tapped video ID, Share serializes field1 for that ID, Download uses
  that ID for both endpoint and GetDownloadAction. No Mix playlist ID, fabricated
  eligibility, metadata request or direct downloader was added. Native parser/fixture
  evidence in row-menu-followup.md supports these schemas; server/UI remains pending.

## Shared interactions and cumulative scope

- Downloads override still receives native inferred handling through existing iax.e,
  allowing its setting to handle or fall through. System share uses current service
  handler, preserving OPEN_SYSTEM_SHARE_SHEET and link-format settings. Interface
  correction above is essential for exact-ID behavior with that override.
- FlyoutUtils's valid typed-video branch clearing playlist/post/comment IDs fixes
  actual system-share priority conflicts. Additional history/Shorts flag clears are
  justified: FlyoutPatch.kt typed consumers are full non-Litho watch-history video
  and You playlist video records; neither establishes a Shorts context. The typed
  path already returns before horizontal-shelf-history detection; a retained true
  history flag is stale. These flags affect Native queue/PiP/Save to Watch Later
  applicability and ChannelWhitelist actions, so stale flags must not carry forward.
  The native queue adds the same exact video interface without coupling shared code
  to queue inclusion. Empty IDs preserve previous behavior. Nonvideo parsing unchanged.
- Existing flyout patch remains dependency; Downloads/System share are optional,
  not new feature dependencies. Queue-unselected shared runtime does not depend on
  the queue interface. No listener replacement or native Save/Download/Share policy
  bypass is introduced. Related original-title binder shared fingerprint is cleared
  before matching and video-ID field is derived consistently across matched binders.
- Checked complete cumulative tracked file list/stat against clean-main
  e5464395441e916b643af4e2175c4e253e6df487 (33 tracked paths), plus new MenuHook (34th).
  Unchanged engine/notification/channel/metadata/reorder/rating/tests retain the
  reviewed baseline scope recorded in scratch/native-playlist-poc21-review-notes.md
  and its preceding retained reviews; this is not a claim of newly reinspecting all
  4,760 cumulative lines. Current runtime state/publication/removal and new hook paths
  reviewed directly. PoC21 notification completion hooks are unchanged and user
  reported phone success remains valid for that earlier fix only. default=true and
  exact21.20.405 compatibility remain. No release lists/dependencies/generated outputs
  or scratch files are part of the current eight-file source change.

## Evidence and proportionate follow-up

Read actual successful logs tests-final-01, apk-combined-09, apk-unselected-01,
row-menu-buildAndroid-01. Combined/unselected logs show 12 SDK DEX files and 58,109
class hierarchy/resource verification passed; combined verifies native panel field
assignability. Implementation reports final XML totals25:20 passed,5 unrelated Jam
skips,0 failures. Standalone retained successful worker output/directory lacks full
log; no repeat is required solely to obtain one. These checks cover the pre-fix tree,
not future corrected code. No native UI/server or Android runtime success inferred.

After fixes, smallest additional distinct check: assert the actual patched row type
implements the exact interface used by onRowMenuOpened, that its generated getter
loads the binder-derived String field, and that the menu-opening map flows to that
helper. This catches the actual unreachable-guard regression and complements SDK
hierarchy verification. Recheck dismissal receiver/terminal relationship in emitted
DEX and run justified final source/APK/build checks after discovery changes. No new
JVM parser roundtrip/schema snapshot or Android runtime emulation is warranted.

Pending phone checks: row menu contents/order; Save adds exact tapped video;
Download respects native account/eligibility and optional external-downloader settings;
Share exact nonplaying tapped video with both share modes, including preceding
playlist/post/history/Shorts menu context; Remove absent on selected/current rows,
removes correct nonplaying row and closes sheet, preserves playback/order and
rejects stale/pending/drag events; ordinary account playlist/flyout actions unchanged;
background notification Next/Previous still works. No new log capture requested.

Decision: do not commit this exact tree. Correct blocking items, then primary owns
final staged-tree review record and mandatory pre-commit gate.

## Focused correction re-review (2026-10-19)

Read current exact production diff, new MenuHook and full new harness assertions.
All three original production blockers are resolved: the guard now names the installed
shared interface; row Save uses the correct stock label; the scoped dispatch fingerprint
requires immediate virtual noarg void dismissal on the method's this register then return,
and installer checks its declaring class belongs to sheet hierarchy. Null still branches
to original dismissal; nonnull restores callback type. Inference enum/comments corrected.
No new production correctness blocker identified in this focused review.

Read actual review-tests-02, review-combined-01, review-unselected-01 and
review-buildAndroid-01 logs. All successful; combined/unselected now report 58,108
classes, reflecting removal of redundant interface, and12DEX hierarchy/resource checks.
Combined output confirms newly added row-interface/getter/map/branch assertions ran;
unselected confirms queue row interface and menu hooks absent. git diff --check passed.
These remain structural checks; phone/server pending list above remains unchanged.

One requested regression-coverage gap remains before final gate: verifyNativeRowMenuPaths
checks native row implements the shared interface but does not inspect the runtime
onRowMenuOpened INSTANCE_OF guard at all. Restoring the original wrong nested guard
would therefore still pass this new checker. Minimum correction is an emitted-DEX check
that this runtime helper's type guard names the exact shared interface present on the
native row (plus owned QueueRow), and reaches the shared context extractor, or an
executable equivalent. This directly addresses the actual regression without source
snapshots/emulation. Existing getter check establishes a native String field read by
native code, not specifically binder identity; direct production review establishes
binder-derived field selection. Do not overstate that assertion in the handoff.

Focused verdict: production corrections accepted; finish exact runtime guard identity
regression coverage and validate that bounded harness change, then primary can finalize
the mandatory staged-tree gate. No production edits/builds performed by reviewer.


## Primary final gate review

Reviewed the final eight-file staged tree and cumulative34-file feature diff.
The final test-only correction now inspects emitted onRowMenuOpened INSTANCE_OF
guards for QueueRow and the exact shared interface, plus the existing extractor
call. Primary ran the verifier against saved actual corrected combined/unselected
DEX (accepted) and earlier wrong-guard DEX (rejected for intended guard diagnostic);
logs native-playlist-row-menu-guard-{positive,unselected,negative}.log. Production
findings are resolved; no further blocking finding. The getter assertion is only a
native String-field consistency check; source review establishes binder identity.
Final actual MPP/default=true/payload/CRC/hash checks passed. Phone/server and
Premium-wording limits remain as above; no native parser roundtrip claimed.
