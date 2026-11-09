# Native playlist queue row menu implementation checkpoint

Updated 2026-10-19. Repo `/workspace/morphe-patches`, branch
`feature/youtube-native-playlist-queue`, baseline `6239139bb`. Changes remain
unstaged/uncommitted. No phone is attached; device validation remains pending.

## Changed files

- `extensions/youtube/src/main/proto/app/morphe/extension/youtube/innertube/playlist_queue.proto`
  adds verified native menu item/endpoint schemas for save, download, share and hide.
- `extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch.java`
  emits row actions from the exact video ID; download uses native Action.ADD and
  GetDownloadAction, share serializes the native video entity, and Remove is omitted
  for selected/current rows. Rename/reuse `onRemoveCommand` for swipe and menu paths.
- `extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/utils/FlyoutUtils.java`
  valid typed video context clears stale playlist/post/comment/history/short values.
- `patches/src/main/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/Fingerprints.kt`
  adds bounded row menu-open discovery anchored on the stable endpoint tag and the
  discovered native video type.
- `.../PlaylistRowHook.kt` bridges the binder-derived exact video-ID field and calls
  context extraction immediately after the native wrapper map factory.
- `.../PlaylistRemovalHook.kt` sends swipe actions through shared `onRemoveCommand`.
- New `.../PlaylistMenuHook.kt` discovers native row callback/sheet relationships,
  intercepts only local Remove and branches to the original dismissal instruction.
- `patches/src/test/kotlin/app/morphe/patches/youtube/interaction/nativeplaylistqueue/PlaylistQueueApkBuild.kt`
  adds Downloads and Open system share sheet to existing combined/unselected harness
  selections. No new test infrastructure or source snapshot tests were added.

## Native control flow and emitted DEX evidence

Native fixtures and original control-flow evidence remain under
`/workspace/scratch/native-playlist-inspection/`, especially
`row-menu-fixture-evidence.txt`, `row-menu-open-dex.txt`, and
`row-menu-bottomsheet-dispatch-dex.txt`. The generated queue row menu uses captured
native Download/Share/Save schemas and localized native labels. Share payload is the
verified entity field 1; download parameters match captured `CAIQAA%3D%3D`.

Combined patched DEX inspection is saved at
`/workspace/scratch/native-playlist-row-menu-emitted-dex-inspection.log`. In `oxk.h`,
after the native four-object map factory returns into `v5`, `onRowMenuOpened(v5)`
runs before construction/dispatch of the original native menu. In the native sheet
endpoint dispatcher, `onRemoveCommand(v2,v3)` returns into `v2`; non-null goes through
original typed callback and original dismissal. Null branches directly to the original
`bl()` dismissal. No duplicate dismissal call/return or early return bypasses native
hide dispatch. The pre-existing swipe hook calls that same guard. APKs passed SDK DEX
and cross-DEX hierarchy checks, which validate encoding and register structure.

## Validation

- `./gradlew :patches:test` passed (fourth attempt) at
  `/workspace/scratch/native-playlist-row-menu-tests-04.log` passed initially; then the
  final source was rerun and passed at
  `/workspace/scratch/native-playlist-row-menu-tests-final-01.log`. Final XML totals: 25
  tests, 0 failures/errors, 5 unrelated Jam APK-dependent skips. Earlier attempts
  failed on builder typing, missing import, and mutable-method resolution; prior
  test log paths were overwritten by subsequent attempts and are not represented as
  preserved separate logs.
- Combined queue + related patches, including Downloads and Open system share sheet:
  passed `:patches:validatePlaylistQueue` against stock 21.20.405, log
  `/workspace/scratch/native-playlist-row-menu-apk-combined-09.log`, output APK
  `/workspace/scratch/native-playlist-row-menu-combined.apk`, SHA-256
  `c451e6082c26fd61f2d4b418d335fd2b815e09fe160e706cc515e9171cf3ccad`. SDK DEX,
  cross-DEX hierarchy (58,109 classes), native panel field compatibility, resources
  and APK alignment passed.
- Queue-only standalone validation also succeeded against stock 21.20.405. The command
  was run without tee, so there is no full saved log; session output recorded 12 DEX,
  cross-DEX hierarchy OK (58,109 classes), native panel field compatibility,
  resources/alignment success. Patched DEX directory:
  `/workspace/scratch/native-playlist-validation-tmp/playlist-queue-standalone14920978863716539061`.
  No rerun solely for logging.
- Related patches with Native playlist queue unselected passed against stock 21.20.405;
  log `/workspace/scratch/native-playlist-row-menu-apk-unselected-01.log`. Queue was
  absent per harness verifier; Downloads and Open system share sheet applied. SDK DEX,
  hierarchy (58,109 classes), resources and alignment passed.
- Actual generated menu endpoints have not been round-tripped through a JVM parser test.
  The production fields were checked against captured stock native parser/fixture
  evidence above. No phone/emulator behavior test was run.
- Final `:patches:buildAndroid` passed after the final test run. Log:
  `/workspace/scratch/native-playlist-row-menu-buildAndroid-01.log` (BUILD SUCCESSFUL,
  9s). It was run last because tests regenerate the patch JAR. No source changes after
  successful combined/standalone/unselected APK applications.
- `git diff --check` passed; no files staged, no commit or publication. The harness
  applied 19 named related patches plus the queue in combined mode. Unselected mode
  applied the 19 named related patches without the queue.

## Remaining

Implementation/build work is complete. Primary/reviewer should inspect the unstaged
source diff and perform the mandatory review gate. No device test was run. Do not
stage, commit, publish, or create the primary review record in this worker task.

## Independent review corrections (2026-10-19)

Applied the bounded findings from `row-menu-review.md`:

- `onRowMenuOpened` now checks the exact shared
  `FlyoutUtils.FlyoutMenuVideoIdInterface` installed by `PlaylistRowHook`; removed the
  unused duplicate queue interface.
- Row Save uses `accessibility_save_to_playlists` (header queue-save label unchanged).
- Offline action enum now names wire value 11 `INFER_AUTOMATICALLY`; documented
  captured native GetDownloadAction parameters. Updated queue menu and removal comments.
- Moved native callback/terminal dismissal matching into scoped
  `playlistRowMenuDispatchFingerprint` in `Fingerprints.kt`. Required callback must be
  immediately followed by a no-argument virtual void call on the dispatch method's
  `this` register and then `return-void`; hook verifies dismissal owner is the native
  sheet or its superclass. Missing/ambiguous/moved relationships fail closed; branch
  still targets the exact original dismissal instruction.
- Extended existing APK harness with emitted-Dex assertions for shared interface
  identity, row String getter field use, map-factory-to-context callback order, and
  true callback/null dismissal branch preservation. Unselected mode verifies the queue
  row interfaces and these hook calls are absent.

Review correction validation so far:

- First `:patches:test` attempt failed during Kotlin compile on TypeList/list conversion
  and iterable indexing in the tightened dismissal matcher. Log:
  `/workspace/scratch/native-playlist-row-menu-review-tests-01.log`.
- Second `:patches:test` passed after fixes, log:
  `/workspace/scratch/native-playlist-row-menu-review-tests-02.log`. Actual emitted-DEX
  harness assertions have compiled but still need real APK execution.
- Combined + unselected real APK validation, final build, emitted-DEX results and final
  source status are pending; append results here before handoff.

Review-correction validation complete:

- Final `:patches:test` passed: 25 total, 0 failures/errors, 5 APK-dependent Jam
  skips. Log `/workspace/scratch/native-playlist-row-menu-review-tests-02.log`.
  The preceding attempt failed during compile before tests and is retained at
  `native-playlist-row-menu-review-tests-01.log`.
- Final combined 21.20.405 queue + 19 named related patches passed with Downloads and
  Open system share sheet selected. Log
  `/workspace/scratch/native-playlist-row-menu-review-combined-01.log`. SDK DEX and
  cross-DEX hierarchy passed (58,108 classes); native playback verifier passed. New
  emitted-Dex checks reported: the row implements the shared FlyoutUtils interface,
  `patch_getVideoId` returns its native String field, `onRowMenuOpened` is immediately
  after the four-object native map factory, and nonnull callback vs null dismissal
  branches both preserve the original native control flow. Resources/APK alignment
  passed. APK output is
  `/workspace/scratch/native-playlist-row-menu-review-combined.apk`.
- Final unselected + 19 named related patches passed. Log
  `/workspace/scratch/native-playlist-row-menu-review-unselected-01.log`; new checker
  confirmed queue row interface/menu hooks absent. SDK DEX and cross-DEX hierarchy
  passed (58,108 classes), resources/alignment passed.
- Final `:patches:buildAndroid` passed after tests and both APK validations. Log
  `/workspace/scratch/native-playlist-row-menu-review-buildAndroid-01.log` (6s).
- `git diff --check` passed. Tracked source remains unstaged; no commit, publish, or
  device test. Final implementation awaits focused independent re-review.


## Final focused verifier correction (2026-10-19)

Added a minimal emitted-DEX regression assertion to the existing
`verifyNativeRowMenuPaths` harness. It inspects the actual `onRowMenuOpened` helper
and requires `INSTANCE_OF` references for both the queue-owned `QueueRow` and the
exact shared `FlyoutUtils.FlyoutMenuVideoIdInterface` already installed on the native
row, plus the existing call to
`FlyoutUtils.extractFlyoutIdFromMap(Map)V`. This closes the case where the old
wrong-interface runtime guard could pass a checker that only examined the native row.
No production or APK patching code changed.

Validation used the existing emitted DEX directories and reflective invocation of the
private harness verifier (`InvokeRowMenuVerifier`): corrected combined DEX at
`/workspace/scratch/native-playlist-validation-tmp/playlist-queue-combined5141016908030083660/patched/dex`
was accepted; corrected unselected DEX at
`/workspace/scratch/native-playlist-validation-tmp/playlist-queue-unselected444337062482801130/patched/dex`
was accepted; pre-correction combined-09 DEX at
`/workspace/scratch/native-playlist-validation-tmp/playlist-queue-combined12140612717647862983/patched/dex`
was rejected with `Row menu helper does not guard both queue ownership and the native
row's shared video-ID interface`. No full APK reapplication was run.
`:patches:compileTestKotlin :patches:test` passed with the changed source (repeat log
`/workspace/scratch/native-playlist-row-menu-verifier-tests-01.log`; first execution
compiled the correction and passed in 31s). Final `:patches:buildAndroid` passed, log
`/workspace/scratch/native-playlist-row-menu-verifier-buildAndroid-01.log`.
`git diff --check` passed. No staging, commit, publication or device testing.
