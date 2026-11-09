# Native playlist queue — preparation review

No PR opened. This report and local review tooling stay outside the upstream feature diff.

## Scope and source

Base: `e5464395441e916b643af4e2175c4e253e6df487` (chosen clean main baseline).
Branch: `feature/youtube-native-playlist-queue`, reviewed HEAD `db992a56488e1284ed81366db096ac0ee4445c62`. Three review commits are local; source/build branches were not pushed during this preparation. Public phone-tested baseline: PoC54 Undo confirmed; PoC55 removes its diagnostic messages.
Supported target remains **YouTube 21.20.405 only**. Current selection default stays true as requested for iterative testing; this is not a decision about an upstream default. Queue is in memory; process-restart persistence is not implemented. No expanded compatibility or Premium entitlement claims.

## Changes made during this review

1. `8da741f23`: prevent a presentation refresh exception from rolling back only the panel after native playback and the local queue have committed. Preserve the accepted queue and log the controls-refresh error. Precommit rejection and restoration paths remain unchanged. This addresses a concrete inconsistent-state path, not a claimed phone reproduction.
2. `4837262b4`: declare `QueueUndoActions.java` as a Gradle test input. Its JVM fixture compiles this source dynamically; changes must invalidate cached test results.
3. `db992a564`: delete unused `patch_playQueueRow` interface/bridge and the two discovery requirements used solely by that dead bridge. Native row clicks keep their existing listener/dispatch. Remove unused queue proto schemas from abandoned ShowSheet/SavePanel/CreatePlaylistEndpoint experiments. Protobuf unknown fields remain preserved. Update the patch description to its current feature set and replace misleading PoC/old implementation comments. Keep the experimental/version limitation explicit.

## Native behavior and ownership reviewed

| Area | Native path / authority | Isolation and evidence |
| --- | --- | --- |
| Rows and panel | Accepted playlist controller model; native item/adapter/binders | Injection follows native stale-response checks. Queue rows/header carry nonserialized ownership flags. Ordinary playlist models fall through. |
| Playback and navigation | Native mutable queue, service navigator, sequencer selection and repeat/shuffle | Local flags gate interception. UI snapshots are not treated as the backend owner. Native methods handle navigation and shuffle ordering. |
| Notification controls | Both capability adapters and the playback-state callback | Recompute from active queue service after native state resets; original adapters and other delegates remain. |
| Save whole queue | Owned header command -> native picker Bundle -> owned response/callback | Ordinary picker requests unchanged. Expand actual add mutation actions; new playlist dialog receives the full list. Existing playlist confirmation/Undo uses returned set-video IDs. No comma-joined video IDs/global save-selection state. |
| Removal/Clear Undo | Modern watch toast factory and its three endpoint slots | Consume only the local queue capability; ordinary Save and playlist Undo keep original callbacks. Expired/replaced queue tokens cannot become native server commands. |
| Fullscreen | Existing quick-actions renderer, setter, reset and size emitter | Add only missing queue playlist action. Native theme/visibility/layout/callbacks retained; clearing ownership restores the original model. |
| Shorts and startup | Shared player/video-ID/lifecycle callbacks and weak live owners | Shorts suspend queue ownership; resume requires appropriate native evidence. No-player starts use native watch intent minimized, with identity-bound timeouts. |

Fingerprints use `app.morphe.patcher.Fingerprint`, stable diagnostics/enums/named resources/signatures and related discovered references. Required matches use `matchSingle`, `single`, or checked match counts. Bounded instruction relationships are checked; no hardcoded obfuscated host descriptor/member or numeric Android resource ID was found in production discovery. Numeric proto field discriminators are schema tags, not Android IDs. The new cleanup removes an obfuscated name from a comment too.

## Shared paths / coexistence

- `OpenVideoIntentPatch`: context transforms execute in finalize after consumers of cached instruction positions. No hook registration when queue is unselected. Existing feed filters/open-channel callbacks retain routing. Queue-minimized context applies only to the requested video.
- `OpenVideosFullscreenHookPatch`: bounded minimized-start request temporarily overrides automatic fullscreen only for that explicit queue start. Existing ordinary-video behavior has a focused fixture.
- `FlyoutUtils`: queue inclusion marker gates added actions; owned row menus suppress duplicate feed queue actions. Shared ID reset changes and metadata title caching are part of the cumulative feature and must be visible to a future reviewer.
- Original titles / DeArrow / rating hooks: queue reuses captured metadata and original commands. Shared bind/parser fingerprints are invalidated before reuse. The small shared title-marker normalization change also applies outside the queue; do not claim that every cumulative change executes only for queues.
- Disable playlist autoplay: remains in native dispatcher paths; queue does not bypass its automatic-advance veto. Manual navigation keeps native intent types. Loop video's existing playback handler remains authoritative; queue repeat reads the native mode.
- Hidden player buttons / fullscreen actions: no replacement listener or forced visibility is added by the queue's availability computation. Native visibility/settings remain in place.
- Downloads / system share / native Save: native endpoints and original callback paths retained, with queue-owned data rebinding only. Unselected verification checks no queue native interfaces/hooks and no transitive queue dependency in the related patch selection.

Combined patch application verifies discovery/coexistence and DEX/resources; it does not prove every enabled-setting combination on a phone.

## Conventions and cumulative diff

Reviewed feature-owned runtime state/helpers, native discovery/injection modules, shared-file diffs, schema/resources, test input setup, and focused test coverage; prior exact-hook/stock-Save reviews remain supporting evidence. Kotlin uses official repo style; Java keeps existing helpers, narrow state and typed native bridges. No reflection framework, generated APK/MPP/log/review files, release patch lists, or agent workflow files belong in the source diff. License/NOTICE unchanged. No configured formatter is claimed.

The feature is substantial (49 changed files against the base); upstream review should explain the native ownership boundaries and shared changes rather than present it as a tiny UI-only patch. Kotlin discovery stays separate from Java runtime behavior. This preparation does not rewrite history or rebase onto an unverified upstream version.

## Validation

- Publication fix: `:patches:test :patches:buildAndroid` passed in 1m12s. Kotlin daemon failed to compile, then the supported fallback compile succeeded. Existing25passed/5unrelatedJamAPKskips; no phone exception injection claimed.
- Test input fix: `:patches:test --info` passed in21s. Gradle explicitly reported added `playlistQueueUndoActionsSource` and executed tests.
- Final source: combined real-APK run, string checks, tests and distributable build PASSED in2m42s (20related patches plus queue; SDK12DEX/class hierarchy/resource compilation and native panel/menu/fullscreen/Undo semantic checks).25tests passed,5unrelatedJamAPKskips;90675strings checked without issues. An earlier combined run was deliberately interrupted to remove the newly found unused bridge before final validation; not counted as passed.
- Unselected real-APK run: PASSED1m56s with20related patches and queue absent; SDK12DEX/class hierarchy/resources and no queue native hook/interface or transitive dependency.
- Final MPP integrity: PASSED CRC/currentYouTubeMPE/38queueKotlinclasspayloads exact,11056703bytes, SHA256`4785b8939a96d9a75778a73d7c66e95c8d2f21ee85194487bfc82e41c2ce4a27`.18stocknativeSave methods compared unchanged on finalcombinedDEX. Build is for local validation; no new phone bundle published by this preparation step.

Logs and gate evidence: `/workspace/scratch/native-playlist-readiness-*`.

## Device checks and limits

The user has confirmed features incrementally, including shuffle, fullscreen queue UI/clear visibility, empty-player minimized startup, modern share, full queue save to existing/new playlists and existing-playlist Save Undo, and most recently removal/Clear Undo. That is historical evidence, not a claim that every flow was retested on the final cleanup build.

Before calling the feature ready for upstream review, one focused phone pass should cover normal account playlists/player Save and Undo; queue background next/previous and returning from Shorts; reorder/shuffle/repeat; whole-queue Save existing/new and Save Undo; remove/Clear Undo; stale Undo after a newer edit/video/close. No attached device is available here and no additional log capture is requested.

Native queue attachment is a multi-step host operation. This review fixes the observed source-level postcommit panel-only rollback bug; it does not prove recovery from every exception thrown inside native attachment. A controls refresh that fails can remain stale until a later native update; the error remains logged and playback is preserved. Metadata lookup is best-effort and can depend on server/client experiments. Newly added custom strings currently have English source text; native/reused labels use existing translations and additional translations follow the repository resource workflow. Server-supplied Save response shapes and all native schemas are verified for the chosen client, not guaranteed for future versions.

Decision: prepare and retain this evidence; no PR or new compatibility claim. All three commits passed the mandatory staged-tree review gate. Automated preparation is complete; the focused device pass above remains outstanding.

## Subsequent phone delivery

After this preparation the user asked to test the reviewed fixes. PoC56 reviewed-cleanup.mpp was uploaded from the exact validated artifact; source db992a564 and artifact536eaac were pushed. No source changes or redundant rebuild. SHA and current payloads reverified. Phone checklist in build README; result pending. No PR opened.

## Delivery correction after PoC56 phone import failure

The original archive integrity check was incomplete: it verified ZIP/current extension/Kotlin payloads but did not require the root Android classes.dex. The subsequent unselected validation regenerated the archive via jar after buildAndroid. User saw zero patches in Manager. The source tests and real-APK checks passed, but the delivered MPP was invalid. PoC57 re-runs buildAndroid last, restores rootDEX and checks both the actualJVMloader171patch inventory and AndroidDEX2686classes/queuegetter/checksums. No source behavior changed. FinalSHA ac77dc4b0f2bf9b66c043284bcf35003b8675a95544f9f0303d21fc9de29a011,11915324bytes. BadPoC56 negativecheck nowfails missingrootDEX. Deliveryworkflowgate updated. AndroidManager import remains phone-pending.

## Phone result

After receiving corrected PoC57 the user reports "ok all working". Record the import and requested regression pass as user-confirmed working. No independent device execution claimed. PoC57 is the working baseline; PoC56 remains a broken historical bundle. No PR authorized or opened.
