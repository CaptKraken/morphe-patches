# Current checkpoint — PoC50 startup failure diagnostic published

Source 4eea46758de265599cbf900d184b5104493028b9 on feature/youtube-native-playlist-queue; base e5464395441e916b643af4e2175c4e253e6df487. Artifact 20db65e085066d0c4630bfabf953d526fefdb71a on builds/youtube-native-playlist-queue. Both pushes confirmed, source/build trees clean.
https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc50-startup-failure-diagnostic.mpp
SHA256 810eedfed3c847c9c7fb47554f6916d4a9ffaa79464fa9eeb07fd6f4d0836d00; 11907072 bytes.

## User feedback and current status
PoC49 FAILED to remove the generic Could not display native playlist queue toast. User previously confirmed first queue creation, native MINIPLAYER and preserved browsing all work. Exact exception unknown, no attached device and no further log requests allowed. Do not claim this issue fixed.
PoC50 ONLY replaces generic Logger diagnostic with Queue/<stage> <exceptionSimpleName>. Stages playback/renderer/panel/attach/repeat/rows/headers/controls/fullscreen are assigned before existing calls. Original throwable still logged, original rollback/returns/publishing-finally unchanged. One file 14 insertions/1 deletion; no native hooks/resources/behavior changes. Phone can now expose exact failure boundary without a log capture. Final user reply should call it a diagnostic build, ask for new short message, never claim fix.

## Investigation
Read actual pqj/pqh/pqg model factory/accept/render, emitted patch_updatePanel; niq/aokc/aojx/nig and emitted patch_attachQueue. Ordinary null playlist model is valid and native builder defaults initialize queueMode/tracking. DO NOT add nonnull-model guard. No exact root established. Broad publish try covers post-commit row/header/control refresh; its catch can roll panel back after native/backend/extension state is committed. This is a correctness concern to resolve once actual throwing step known, not proof of which call throws. QueueFullscreenActions.refresh has its own different-message catch. Native playback response may arrive independently of panel/controller IDs; do not assume panel visibility proves accepted backend. Weak notification/player controls may outlive active bindings; needs ownership analysis if controls stage fails. No speculative readiness/transaction fix bundled in diagnostic.
Pinned Logger 1.9.0-dev.4 javap confirms supplied message appears via Utils.showToastLong and exception logged. File native-playlist-start-diagnostic-logger-api.txt.

## Actual validation
:patches:test :patches:buildAndroid passed31s.29 total24pass5 unrelated JamAPKskips0failure/error. native-playlist-start-diagnostic-build.log. No tests mirroring stage assignments added. No fresh realAPK application for diagnostic-only extension change: PoC48 selected/unselected/native-order evidence applies to unchanged hooks. No phone reproduction claim.
CRC/currentYouTubeMPE/36queueKotlinclass payloads exact (earlier37 count includes directory), hash/size in start-diagnostic-integrity.json. Mandatory exact staged review/gate passed; normal commit, no bypass. Saved staged/cumulative diff and review in /workspace/scratch/native-playlist-start-diagnostic-*.

## Next
Await PoC50 short Queue/<stage> exception type, then fix concrete cause with proportionate validation. Preserve working mini/browse flow, default=true and21.20.405only, existing queue additions and Last visibility, Save/New/Undo, ordinary player actions/account playlists. Remove temporary diagnostics when cause fixed.
PoC47 whole-queue New Save/picker USERCONFIRMED; PoC45 existing Save toast+Undo USERCONFIRMED. PoC48 firstqueue/miniplayer works witherror. PoC49 callback post fixes source-confirmed reentry hole but did not fix reported toast.

## Recovery/workflow
Read /workspace/AGENTS.md and WORKFLOW.md. No agents/delegation/modelrouting. One step per gated source commit/MPP. Source/build uploads authorized, no PR. No new log requests. Shared watchcontext hook MUST run FINALIZE after cached-index filters; preserve descriptor-freeze-before-filters regression check. No old branch copying. Save draft does not Publish snapshot or verify restore. PreviousSTATE-history-before-poc50.md; start-queue-failure-diagnostic-poc50.md.
