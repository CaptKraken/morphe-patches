# Current checkpoint — PoC51 unbound service fix published

Source 8c72a131ec6ff67e3494489c234ede93e1a1fb31 on feature/youtube-native-playlist-queue; base e5464395441e916b643af4e2175c4e253e6df487. Artifact bcfba0d1e8d9ff0d4410632cf4f1d13f9085b7ca on builds/youtube-native-playlist-queue. Both pushes confirmed; both trees clean.
https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc51-unbound-service.mpp
SHA256 2b1a65b638954638588bfdda194a6532c1e8ac8cc1495c8d8b57d856d1384c5b;11906974bytes.

## User evidence and fix
PoC50 screenshot says Queue/playback NullPointerException. Happens initially; after closing player/recreating queue no error. Queue/video/miniplayer/browsing work. Actual stage narrowed to playbackFor. Found service.patch_getVideoId().equals(activeID) dereferences nullable native result. Actual emitted aowp.patch_getVideoId explicitly returnsnull when aowz.q.a playerabsent. Native owners constructor-initialized; activeplayer holder nullable. VideoInformation.getVideoId initialized empty and documented returns empty when no player, nevernull. Fix both unsafe service-ID comparisons (resolver and restoration observer) to activeID.equals(serviceID), annotate ServiceNavigator ID @Nullable and document it. Other serviceID comparisons already nonnull queueID receiver. Unbound service is skipped, publish(false) leaves pending start for existingcontroller/video/player/native-response retries. No blanket catches/error suppression/extra state/timer/helper/nativehooks. Remove PoC50 temporary stage diagnostics, restore genuine Logger message. Onefile7insertions16deletions.
PoC49 sourceconfirmed synchronouscallbackhole fix retained but DID NOT removeoriginaltoast; no claim otherwise. No null-playlistmodel readinessguard: ordinarymodelnull legitimate nativebuilderfallback remains.

## Actual validation/review
Exact staged review/gate passed, normalcommit. :patches:test :patches:buildAndroid PASS23s;29total24pass5 unrelatedJamAPKskips0failure/error. No test merelyrestatingnull-safeString.equals or source regex. Actual native nullablegetter/controlflow and allfourserviceID usages reviewed. native-playlist-start-null-service-contract.txt actual previously emitted21.20.405 bridge; stockaowp/aowz lifetimes and VideoInformation contract inspected. No freshAPKrunneededforruntime-onlycomparison/annotation/diagnosticremoval; priorPoC48selected/unselected SDK/nativeorder evidence applies to UNCHANGEDhooks, not newdeviceproof. CRC/currentYouTubeMPE/36queueKotlinclasses exact; build/integrity/review/staged/cumulative artifacts /workspace/scratch/native-playlist-start-null-service-*.

## Pending phone
PoC51: firstqueue afterrestart without error; queue/videoMINIMIZED and browsingretained. Thenclose/recreate, notificationNextPrevious/background, Save/New/Undo. Noattacheddevice/newlogcapture; do not claim phonepass until usertests. Iferrorpersists exact newboundary would need investigation; diagnostics removednowcauseidentified. Broadpublish postcommitrollbackconcern deferred, not mixedinto narrowfix.

## Invariants/recovery
Read /workspace/AGENTS.md and WORKFLOW.md. No agents/modelrouting/delegation. Onefixpergatedcommit/MPP. Source/builduploads authorized; noPR. No newlogrequests. 21.20.405only/defaulttrue. PrefernativeUI/logic. OrdinaryplayerSave/accountplaylists unaffected. PoC47wholequeueNew/picker USERCONFIRMED; PoC45existingSavetoast+Undo USERCONFIRMED. PoC48mini/browsework. Sharedwatchcontext hook MUST be FINALIZE aftercached-indexfilters; preservenativedescriptor-freeze-before-filter check. Never copyoldbranch. Configdraftsave doesnotPublishsnapshot or verifyrestore. PriorSTATE-history-before-poc51.md and start-queue-null-service-poc51.md.

## Phone confirmation
User reports PoC51 works. Initial first-start null service-ID failure resolved. No claim of separate exhaustive regression testing.
