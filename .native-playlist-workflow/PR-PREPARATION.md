Local update awaiting phone validation (cab869e11; not pushed): individual-row removal is now silent; header Clear retains uninterrupted playback and Undo; mini-player X now shows native Queue cleared/Undo and can reopen the saved video in the mini player at its whole-second timestamp, restoring the complete queue to a new backend. Confirmations5snormal/20sa11y. Source/ZIP remain local; PR3953 and its user-edited description unchanged. Current delivery/evidence/remaining checks in STATE.md. Do not publish or edit the PR until authorized after testing.

# Undo timing finding addressed locally; awaiting phone test

Local e8051a7b1 fixes removal/Clear confirmation+Undo windows to5snormal/20saccessible, using existingnativehook. Timing regression tests/all3combinedSDKchecks pass; localZIP verified. This commit is NOT pushed; draftPR3953 remains2795170b2. User push/publication hold persists. See STATE for artifact/hash and phone checklist. Do not mark the public PR finding resolved until authorized push and honest phone results.

# Open review finding after full PR diff pass

Full54-file review at2795170b2 found one concreteP2: removal/Clear Undo expiresafter10s whileYouTube's accessibilitysnackbar canremainvisible20s. See /workspace/scratch/queue-full-diff-review/REVIEW.md forsource/nativeevidence andfocusedfollowup. No codefixyet; no commits/pushes. Patchtests14pass5unrelatedskips,extension87FROM-CACHEpass,105070stringspass. KeepdraftPR3953andpushhold; previousreadinessstatementsdo notresolve thisnewfinding.

# Draft PR opened

User-created draft [#3953](https://github.com/MorpheApp/morphe-patches/pull/3953) is open against dev from CaptKraken:feature/youtube-native-playlist-queue-dev. Verified title, draft status, AI disclosure and screenshots. Current live description is cached separately in PR-CURRENT-DESCRIPTION.md; retain user edits when working on it. Earlier no-PR/permission blockers are historical; current credential still has no demonstrated upstream PR write permission. No PR-content changes made by this check.

# Latest confirmation — Patcher 2.0 phone check

User reported the delivered4a14e8bed Patcher2.0MPP “working still.” The requested initial retest target was21.20.405. Record this alongside previous supported-version phone results and the current automated2.0 checks. Do not describe it as a new exhaustive three-version regression. No further migration fix or rebuild is indicated; no PR is opened/authorized by this feedback. Earlier pending phone-import/general-working wording below is superseded by this report.

# Current Patcher 2.0 preparation — 2026-10-22

Upgrade implemented, gated and pushed at4a14e8bedd7e633e37cecdc339a1209181ab5b1f on feature/youtube-native-playlist-queue-dev. Official upstream dev344ca5e25 is merged without rewriting the 21 behavior/test/compatibility commits. Patcher/library/plugin2.0.0 and current smali are in use. Production queue implementation is unchanged; Ask flyout menu added to the relevant APK-check combination. The resulting PR diff against currentdev remains54featurefiles, without local review tooling or manually generated release content. Historical main branch and Patcher1.15fallback preserved.

The previously identified integration gap is closed for automated validation: compilation,14patchtests(+5unrelatedJamAPKskips),87extensiontests,105070strings,combined real-APK SDK DEX/hierarchy/resources/native checks on20.51.39/21.16.256/21.20.405, and21.20standalone/unselected checks passed under2.0. FinalAndroidMPP loads191patches/currentmetadata underactual2.0loader andpassed exactpayload/DEX/checksum verification locally and afterdownload. Sourceandartifactreviews/logs /workspace/scratch/queue-patcher2-integration; STATE.md has hashes/URLs.

Patcher2.0 phone import/regression remains pending; prior successful phone results cover the previous dependency baseline. Start21.20.405 and check queue/ordinarySave/playback/menu coexistence as recorded in STATE. The build README explicitly requires a compatible Manager and preserves old testedMPP. No PR opened or authorized by upgrade/readiness requests. Do not claim new Android runtime testing from SDK/JVM checks.

Earlier readiness and implementation notes below are historical; this section supersedes their pending2.0 status. Existing architecture/shared-code reviews remain relevant for unchanged code.

---

# Current readiness check — 2026-10-22

Feature and existing validation evidence are sufficient to describe a PR; no PR is authorized/opened by the readiness question. Source branch feature/youtube-native-playlist-queue-dev is clean and pushed at e85946f956fcd656dcd6614513ad7c3ceb6628d4, with21 feature/test/compatibility/fix commits after approved dev base0488bda28d4d5844a2a739d3eefd00c28afd43fa. Historical implementation and artifact branches preserved.

Current exact support is20.51.39(minimum),21.16.256,21.20.405(maximum), all user-reported successful phone tests. Latest five-second generated Save confirmation was subsequently reported working; this is not a new complete three-version device regression. Existing template minimum/maximum evidence is available; do not claim experimental/newer clients or20.21.37. Latest automated source checks:14patchpasses+5unrelatedJamAPKskips,87extensionpasses; combined real-APK SDKDEX/hierarchy/resources/native checks onall3targets and21.20unselected passed. New source/delivery and review evidence in /workspace/scratch/queue-save-duration/implementation. Some final unit tasks cached/up-to-date as logged.

Fresh upstream check: fetched dev into review-upstream/dev at344ca5e2539c4e9a5ba9272df4ccc6ba262ee49c (v1.48.0-dev.21),14upstream-only commits. git merge-tree --write-tree review-upstream/dev HEAD succeeded with no textual conflicts; tree64ed12c85efbd51b311389d29c05c19d8d95c1a5, result/workspace/scratch/queue-pr-current-merge.txt. This is virtual only: source branch/base/history unchanged. Only overlapping changed files are patches/build.gradle.kts and values/youtube/strings.xml.

Important remaining integration check: upstream commitd7c25c260 (#3945) upgrades morphe-patcher1.15.1 to2.0.0, patches-library1.9.0-dev.11 to2.0.0 and smali revision, with resource/discovery/build changes. Our successful checks use the previous dependencies. A clean text merge does not establish API/runtime compatibility. Before presenting the PR as ready for review, validate an isolated integration with currentdev: compile/tests first, adapt only actual incompatibilities, then relevant APK/inclusion checks and phone bundle if needed. No failure under2.0 has been reproduced yet; do not state migration is definitely broken or already complete.

PR narrative should explain nativeUI/sequencer reuse, ownership-guarded interceptions/shared changes, separate alternative with existing Morphe queue priority, temporary/no restart persistence, intentional Clear Undo and local shuffle command. Latest description should include3verifiedversions and five-second Save feedback. Dynamic provider-toggle handoff and screen-reader timing were not separately confirmed by latest phone feedback; list honest coverage rather than inventing mandatory device gates.

Earlier notes below are historical; their21.20-only support, old HEAD/testcounts and pending phone statements are superseded by this section. Existing shared-path review remains reference for unchanged code.

---

## Latest test-maintenance checkpoint

Commit e0a783859 reorganizes tests only; current automated totals are83extensionpasses and14patchpasses with5unrelatedJamAPKskips.48queue-specific cases:40directextension+8isolatedboundaries. No APK/device claim follows from unit tests; prior real-APK verifier source and runtime implementation unchanged. See /workspace/scratch/queue-test-cleanup/coverage-review.md and final-tests-fixed.log. Earlier validation totals below are historical.

# Native playlist queue: preparation for upstream review

Status: preparation complete; isolated branch pushed with 16 commits split by behavior, each gated. No PR opened. Dev integration and provider handoff still need phone testing. No wider YouTube compatibility claimed.

## Branches and history

- Historical implementation: `feature/youtube-native-playlist-queue`, `db992a56488e1284ed81366db096ac0ee4445c62`, based on main `e5464395441e916b643af4e2175c4e253e6df487`. Preserve this branch and its PoC/build history.
- Submission preparation: `feature/youtube-native-playlist-queue-dev`, `/workspace/morphe-patches-dev-integration`, based directly on upstream dev `0488bda28d4d5844a2a739d3eefd00c28afd43fa` (178 commits ahead of the main baseline at comparison time).
- Corrected the initial bulk port at the user's request. Final branch now has 16 clean commits, HEAD `bddfcc67f3b643df63363ccb9c759284ffa6982d`, directly on dev0488. Creation, rich cards, swipe removal, reordering, row menus, whole-queue Save, modes, media controls, Clear, Undo, fullscreen, outside insertion, minimized startup, Play last and provider priority have separate implementation steps. [Feature history](FEATURE-HISTORY.md) lists hashes and per-step evidence.
- The complete tracked tree is `580f4be90233d33aa730c4d59a64bf419f6941b9`, exactly equal to PoC58 sourcec61e4bafe. Original dev history is archived under `archive/youtube-native-playlist-queue-dev-poc58`; the main-based historical branch is unchanged. No old failed PoCs/debug/fixup ancestry was merged into the new history.

## Concise implementation overview

The patch supplies a temporary list of local video rows to YouTube's existing playlist UI and mutable playback queue. YouTube owns row rendering, the playlist panel and fullscreen container, native row gestures, sequencing, selection, repeat, player/media actions, and the Save/Share/Download UI and account requests.

The extension owns the in-memory queue snapshot, local row/header ownership, pending navigation/start intents, metadata completion, and bounded Undo capabilities. Typed bridges expose discovered native methods. Kotlin performs APK discovery and small injections; Java handles runtime decisions. Native sequencer selection is authoritative; a visible panel or prefetched metadata is not treated as proof of live playback ownership.

Interception is limited to owned local queue operations. Ordinary/account-playlist commands fall through. Save attaches the immutable list to the queue-launched picker's arguments, rebinding that response's actual add mutations and New playlist action. Ordinary player Save has no queue argument or callback context. Undo commands require a local token; native Save Undo continues through YouTube's own endpoint dispatcher. Opening an account playlist releases local panel ownership. There are no Premium entitlement or higher-bitrate/backend unlock claims.

## Shared-code review and justification

| Shared path | Why it changes / exact scope | Coexistence and remaining evidence |
| --- | --- | --- |
| `FlyoutUtils` | Add native queue menu entries only when included and for eligible regular-video menus. Nonserialized QueueRow ownership prevents duplicate native queue actions in its own rows. Reset row ownership with dev's centralized flyout reset. Typed row ID extraction clears stale playlist/Shorts context. | Preserve dev's FeedVideoActions, popup measurement, LocalQueue and history handling. Do not restore deleted post/comment fields or old delayed-reset implementation. Old queue actions retain their callbacks/settings. Runtime provider policy is a separate approved step. |
| `OpenVideosFullscreenHookPatch` | A video-specific, 10-second request keeps an explicit first-queue start minimized. Existing automatic fullscreen should not interrupt browsing. | No request means ordinary fullscreen behavior. Cancel/consume by request identity/video, not global permanent override. Focused existing JVM fixture covers subsequent ordinary navigation; device startup flow confirmed on historical PoC57. |
| `OpenVideoIntentPatch` | Add an ordered callback returning a replacement context Map before native descriptor construction; immutable or null incoming context must be supported. | Dev already has a read-only `hookVideoIntentCommand` used by FeedCardLayout. Keep it. Apply queue Map transform in finalize so it precedes that observer and descriptor construction without invalidating consumers' cached insertion indexes. Actual DEX checks cover observer/filter order. |
| `RestoreOriginalTitlesPatch.getTitle` | Normalize this helper's own internal title marker before replacement/return. Captured element titles otherwise leak marker behavior into queue rows. | Shared behavior, not queue-only execution: `VideoInformation.getVideoTitle` also calls it. Unmarked inputs are unchanged; replacement-disabled early return is unchanged. Do not claim zero shared behavior changes. Preserve dev's new title-view/listener and cache work. |
| `ChannelIdRequest` | Cache an already received title with volatile visibility; getter never starts a fetch or waits. | Existing channel-info callers and future/result behavior unchanged. Cache remains bounded at 50. |
| `ChannelIdRoutes` | Parameterize existing request body construction, retaining videoId path and adding a query body for queue-only metadata lookup. | Original title/description/channel requests still call identical videoId body method. Best-effort search requires exact returned video ID; it never supplies playback/account actions. |
| `next_response.proto` | Add typed fields needed to capture native like/dislike endpoints and entity identity. | Preserve original endpoint bytes and unknown fields; never synthesize opaque rating parameters. |
| Shared bind/parser fingerprints | Re-resolve after other patches modify common row/response/quick-action methods. | Follow established `clearMatch`, class relationships and discovered native references. No obfuscated production host descriptors/member names/numeric Android resource IDs. |
| Test/build setup | Compile actual Android-free Java/proto logic in focused fixtures; real-APK SDK verification and explicitly tracked source inputs. | Keep dev's Java17 toolchain, JUnit6 and coroutine catalog versions. Generated artifacts/research/review tooling stay outside upstream source diff. |

The strict target remains YouTube **21.20.405**, with default patch selection true as requested during phone testing. This is not a decision about an upstream default or evidence of experimental-version support.

## Current dev comparison and required adaptations

Five files overlap upstream changes; a virtual merge found only two textual conflicts: FlyoutUtils and patches/build.gradle.kts. Automatic textual merging is not treated as semantic compatibility.

- Resolve Flyout against the new centralized reset, menu/history logic and deleted fields; preserve upstream features.
- Keep dev Java17/JUnit6.1.3/coroutines1.11.0 catalog setup, add only feature's protobuf/JSON test needs and source inputs.
- Patcher is now1.15.1 and patches-library1.9.0-dev.11. Five `cloneParameters(MutableClass)` calls no longer compile because that overload is internal. Use public context `cloneParameters()` as maintained dev patches do. Other required discovery APIs compiled successfully; warnings are not presented as formatter certification.
- Add Feed video actions, Feed card layout and Series tracking to the existing related-patch APK combination because their current menu/watch/navigation hooks overlap ours. Verify original native intent arguments, queue context ordering, callbacks and unselected behavior.
- Keep strict21.20.405 compatibility. No rebase/reset/rewrite of the historical branch.

## Coexistence decision

User chose a separate alternative, not replacement/removal of Morphe's queue. Existing `Add to queue` on dev now supports the Local queue setting and persistent item list, plus the older playlist-backed mode. Local queue consumes queued entries and intercepts native NEXT/AUTOPLAY/AUTONAV and ENDED. Our queue retains played entries and delegates sequencing/repeat to the native playlist backend; action shuffle changes the supplied row order locally. They are different engines; selecting both patches in an SDK check does not prove simultaneous runtime ownership is safe.

Approved policy: when `Add to queue` is actually installed and its existing Local queue setting is enabled, Morphe's Local queue takes priority. Native queue must not start/reattach, and an active native queue must detach without pausing/seeking/reloading current playback. Preserve Morphe's stored items/settings. Enabling it invalidates pending native starts/Undo; disabling it later must not resurrect a stale native session. An old saved setting must not disable Native when the old patch is unselected. Do not replace old callbacks or silently make Morphe depend on this feature. Implemented in the separate provider-priority commit. A retained preference listener posts the handoff to the main thread after the setting cache can update. An existing native Clear transaction detaches our engine, then panel ownership is released. Failed native detach logs an error and retains ownership for retry. Turning the setting off starts no old session automatically; a later explicit Native action can create a fresh queue. Morphe stored items/settings are never written.

## Intentional differences and limits

- Clear queue Undo is an intentional addition, retained at the user's request even if Premium lacks it.
- Queue state is in memory. Process-restart persistence is not implemented.
- Normal outside-video opens insert after the playing entry; Play last remains separate. Existing duplicate entry identities are retained for reorder/Undo safety.
- Custom strings currently have English source text and use the normal resource/localization mechanism. Native/reused labels retain available translations.
- Native attachment is a multi-step host operation. The fixed postcommit presentation failure path preserves an accepted backend/panel/snapshot; this is not proof of complete native exception rollback. Failed presentation refresh may remain stale until another update and is logged.
- Native schema/server response assumptions are verified for21.20.405. Metadata lookup is best-effort. No extra Premium server capabilities claimed.

## Honest validation summary

Historical PoC57: user said "ok all working" after corrected import and focused regression checklist. Treat as user-reported phone evidence, not independent device execution or proof of this newer dev integration. PoC56 was a broken Android bundle (missing root classes.dex), clearly marked in build README/milestones. PoC57 fixed packaging without runtime changes; downloaded hash, requiredDEX/checksums/getter, current extension payloads and171-patch JVMloader inventory verified.

Historical source checks:25tests pass,5unrelatedJamAPKskips;strings;real21.20.405 combined20related+queue andunselected20related SDK12DEX/hierarchy/resources;18nativeSave methods stock-exact.

Current integration checks: initial compile exposed five now-internal API calls, fixed before the first commit. The import's realAPK combined23related+queue/SDK12DEX/hierarchy/resources and strings passed2m4s; unselected23related passed1m45s, including absence of queue hooks/interfaces and transitive dependencies. A test-only verifier update was rerun against the actual combined DEX to check FeedCardLayout sees transformed context.18stockSave methods remain instruction/register/branch/handler-exact. String check validates104288strings.

Final provider change: patch suite29pass/5unrelatedJamAPKskips/0failures; all43YouTube extension tests pass, final suite15s. The new fixture executes the compiled NativePlaylistQueuePatch with mocked external settings/scheduling/UI and native bridge interfaces. It checks provider inclusion/off/on, deferred cache notification, active detach, pending start/Undo cancellation, late response, off without resurrection, rejected detach retaining ownership, and unrelated backend protection. It is not Android/native playback emulation or phone evidence. Initial fixture-only missing boundary types were corrected before the passing final suite/commit.

Host injections/fingerprints did not change in the provider step; the import's combined/unselected native evidence applies to those same hooks. Final runtime extension/R8 and standalone Android bundle rebuilt15s after all other Gradle work. Final local candidate: `/workspace/scratch/native-playlist-dev-candidate/native-playlist-queue-dev-reviewed.mpp`, SHA256`d62027ee7d9c01f5d8652e94672d01364c167d255a446632fb442c071c82eb36`,13347439bytes. RootDEX2855084bytes/3018classes withqueuegetter; DEX SHA1/Adler32/CRC/currentYouTubeMPE/38Kotlinpayloads exact. ActualJVMloader loads190patches inclNative/defaulttrue. R8-emitted priority helper retains both inclusion/setting queries; no folded false condition. Native handoff DEX retains clear-before-release and failure-return paths. No new public MPP uploaded.

Bundle version1.48.0-dev.18/patcher1.15.1 comes from currentdev and is distinct from supported target APK21.20.405. Import with a compatible Manager still needs phone confirmation.

No attached Android device/emulator and no new log capture requests. Dev integration and setting handoff need a phone regression pass before calling this the tested successor to PoC57. Test Native with Local queue off; Morphe with Local queue on; toggle on during native playback without time reset; turn off and create a fresh native queue; confirm Morphe saved items remain. Repeat ordinary account playlist/player Save/Undo and queue background/Shorts/fullscreen/reorder/shuffle/repeat checks. Historical PoC57 stays the confirmed working fallback.

Packaging rule: run buildAndroid LAST after any tests/APK checks that can regenerate the jar. Freeze a unique copy, require rootclasses.dex (signature.dex alone is insufficient), validDEX SHA1/Adler32, queue getter, currentMPE/Kotlin payloads and actual JVMloader inventory. JVM loading does not prove Manager's Android load; both structuralDEX check and phone import matter.

Logs/review evidence: `/workspace/scratch/native-playlist-prepr-*`. Local instructions, this report and recovery state are outside the upstream diff. No PR created or permission requested for one.

The preserved PoC58 source restores upstream empty typed flyout video-ID handling, preventing an unidentified menu from inheriting the prior video. Retains current dev menu resets and other patches. Final full suites passed42s, followed by buildAndroid15s and frozen bundle/loader checks. Android menu switching and provider handoff remain device checks.

History reconstruction validation: native-hook commits each passed real21.20.405 APK/SDK/resource checks with23related selections. Queue-unselected checks passed after core and final shared startup changes. Final selected14 run2m30s/unselected1m34s; stage6 compared18stockSave methods exactly. Final exact-source test command succeeded8s with29patchpasses/5unrelatedskips restored fromcache,43extensionpasses up-to-date,104288strings executed. Each commit passed the mandatory staged-tree gate. Full per-step evidence is in FEATURE-HISTORY.md. No new MPP was needed because complete tracked source is identical to PoC58; phone dev/provider testing remains pending.
