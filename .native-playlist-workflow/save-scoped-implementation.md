# Whole-queue modern Save: scoped native mutation expansion

2026-10-20, in progress. No agents. Source starts clean PoC35 559a3354.
PoC36 failure preserved; this is a DIFFERENT approach, not claiming CSV is a valid
server batch ID. Modern picker UI was phone-verified with that carrier, but saving
was not. New code binds an immutable Selection as the sheet's endpoint tag, then
expands native leaf mutation commands BEFORE their original resolvers execute.
No global request rewriting, account API client, custom picker, or static session
registry. Existing queue/player state is never modified by saving.

Native verification:
- Header Save changed from navigation wrapper to service wrapper: same native
  callback/context path as working Clear/modern Share, so prepareContext runs.
- ahhc -> apyw.n preserves original endpoint tag in standard/anchored native sheet.
  The ep_transient_sheet_ migration branch discards it; new guard sends ONLY local
  Selection tags down the existing context-preserving presentation branch. Modern
  EML contents remain unchanged, no legacy AddToPlaylistEndpoint used.
- ahnh.i logs/processes command metadata, invokes handler.b(endpoint,map), runs
  original cleanup, returns true. New hook immediately before final callback;
  failures consume only owned save actions, still execute native cleanup.
- Native endpoint bridge uses generated registry (same pattern as panel/fullscreen
  bridges), preserving opaque command fields and callbacks.
- Actual read-only browse?alt=proto response saved metadata/save-native-actions.pb:
  20 PlaylistEditEndpoint60666189 values; field1 playlistId; action2 -> addedVideoId2,
  action6=1. First sample WL/fOT0BUpITw8. Not inferred from names or JVM fixture.
- Native jtu CreatePlaylistDialogEndpoint85650004: IDs1, source playlist2,
  selection entity key8. If8 is set native ignores explicit list and queries entity;
  expansion clears8 and2, sets full ordered snapshot. This addresses empty-new path.
- Native kcf direct CreatePlaylistEndpoint124747980: repeated IDs1/source2; expands
  IDs and clears mutually exclusive source. Preserve title/privacy/opaque fields.
- Single real video retains original native playlist edit/toggle behavior. Batch
  permits ADD only and matches carrier (or omitted target); unexpected destructive
  commands fail locally rather than remove account content. Batch undo/remove from
  destination is not implemented; device check must assess repeat-save UI behavior.

Changed feature only: header Save, immutable selection helper/protos, final native
command hook and tag-preserving presentation guard, focused JVM fixture, APK
verification. No changes to per-row Save, queue/playback order, other patches,
resources, declared compatibility/defaults. 21.20.405/defaulttrue unchanged.

Validation in progress. Initial Java missing List import and Kotlin missing patch
context compilation failures fixed. Intermediate combined APK checks passed before
last presentation/entity8 changes; NOT current evidence. Current command log:
/workspace/scratch/native-playlist-save-scoped-current-combined.log .
Unselected native APK verification passed1m33s: no native queue Save dispatch,
endpoint bridge or presentation guard; 12DEX/hierarchy/resources passed. Final
helper visibility/import cleanup now has staged-tree tests+combined APK running
in save-scoped-staged-combined.log. Needed: final MPP/default/integrity, completed
staged review/gate, one source commit and PoC37 publication.

Phone checks mandatory, NOT claimed passed: existing destination receives all IDs,
New playlist nonempty/order, Watch Later, one-video native toggle, repeated batch
Save (unexpected remove currently rejected), portrait/fullscreen, cancel/back,
clear/change queue while picker open, existing membership/sign-in/account switch,
ordinary player/per-row/account playlist Save and background playback unaffected.
No attached phone/emulator. User declined logs; do not request new logs.

Completed source commit93956d2cffda4619a3271e658238e28de55e4f48, gate/source push passed.
Final staged-source combined checks passed2m2s;27/22pass/5unrelatedJamAPKskips; final
MPP/defaulttrue7s, exact MPE/all35Kotlinpayloads/CRC verified. Artifact4ba74c0 prepared,
push confirmed. SHA256c064f90b8dc06f2ef30b40e96be33136f1e3ae615946cf2161f1e17f7b7761ac.
Phone acceptance still pending. No authenticated Save success claimed.
