# Active fix — PoC43 FAILED: HTTP400 before picker opens

User confirmed400appears before choosing a destination. Restoring completely stock native picker request; keep serialized queue snapshot and batch destination/New binding. Multi-video destination uses native UNKNOWN state(add-and-close), since first-video checkbox cannot describe batch membership. Five files WIP, tests+combined APK running native-playlist-stock-picker-combined.log. No new bundle yet. Historical published checkpoint follows.

# Current checkpoint — PoC43 published; phone test pending

Source 51ad54a7918184f4a7b7bbcaab46efd0bcaf631a, branch feature/youtube-native-playlist-queue. Base e5464395441e916b643af4e2175c4e253e6df487. Artifact 89bd4626da8e134b0e4f6db256bca03699adfdaa, builds/youtube-native-playlist-queue. Both pushes confirmed, both trees clean.
https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc43-save-destination-commands.mpp
SHA256 9665746d84089f7378868e8a423946fcb15146dea1832dcdf9b9bba8b6231def; 11886668 bytes.

## User result and current change
PoC42 FAILED: user saw only ONE video saved from THREE. Exact runtime loss point not established; no authenticated device here. Do not claim PoC42 works.
PoC43 replaces transient native endpoint-object field/interface with extension-owned serialized local selection field500000000, copied to native fragment saved Bundle. NOT a native/backend parameter. Retains native repeated-ID query and explicitly binds owned native response destination Add and New/dialog/direct-create commands to all IDs. Unexpected partial/unrecognized commands go through original native error/close path instead of silent singleton saving. Ordinary player/per-row/account Save lacks marker and returns original response. Native legacy list/checkbox picker retained (modern batch UI unresolved). No global selection, CSV, shared row factory/resolver overrides, custom account client or playback writes. Play last already separately committed in PoC41; not a remaining implementation task.
Details: save-command-binding-poc43.md. PoC42 note save-real-batch-implementation.md is historical and superseded here. Earlier history STATE-history-before-poc42.md.

## Actual validation
Final exact-source tests:28total,23passed,5unrelatedJamAPKskips,0fail/errors. Combined19related real21.20.405 passed1m50s; finalunselected+tests+MPP passed2m17s; SDK12DEX/hierarchy58204/resources. Native request/response/parser/argument hooks present only selected. Ten ordinary Save/auth/serializer/error methods exactly match stock. CurrentYouTubeMPE/all34Kotlinpayloads/CRC/defaulttrue/gate passed. Evidence /workspace/scratch/native-playlist-save-commands-*. Initial development fixture extra-parenthesis javac failure corrected, not published; integrity script first used nonexistent .jar then correctly compared archive to compiled Kotlin classes. No production-code check failures remain.
Mandatory exact-staged review/gate passed, normal source commit and normal artifact commit. All7source/test/protofiles reviewed; cumulative feature diff reviewed. No agents used.

## Pending phone acceptance
PoC43 MUST still be tested: THREE distinct queued videos/current-middle -> existing and New each contain all3/order. Ordinary player/per-row/account Save; firstalready-saved/partialmembership/uncheck/WatchLater; cancel/rotation/clear/reorder while picker open; continuous playback/controls. JVM/mock/DEX validation does not prove backend saving. No attached Android device/emulator and no new log requests. If user still sees1, distinguish selection transport vs returned native command form before proposing another fix; do not assume backend rejects batches.

## Persistent workflow
Read /workspace/AGENTS.md and WORKFLOW.md. No subagents/modelrouting/delegation. One agreed fix/feature per gated commit and MPP. User explicitly authorizes source+test MPP uploads to CaptKraken/morphe-patches feature/builds branches; no new routine permission question.21.20.405only/defaulttrue. Preserve ordinary YouTube behavior and intercept queue-owned actions only. Current review evidence scratch native-playlist-save-commands-review.json; .git review is naturally stale after commit and must be regenerated for next staged tree.
