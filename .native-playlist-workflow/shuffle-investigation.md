# Shuffle investigation — stock YouTube21.20.405, 2026-10-20

Scope: investigation only, no source changes/build/MPP/commit. User requests native
logic investigation first. PoC25 Share user-reported working. No agents or device.
Repo HEAD07bdd17ac4d26f4c9fb0d8cb307b8f91f0f8fc8d; source clean.

## Verified native paths (stock APK, never hardcode these names in production)

Cached stock decompiles: /workspace/scratch/native-playlist-inspection/java/.
- ppo.java constructor: compact_playlist_panel_header, R.id.shuffle ImageView,
  native click oyn case15. ppo.d bind enables shuffle from ahwb.d() OR server
  shuffle metadata (ayqy), hides for no panel/bfzu.y; ppo.k(mode,shuffle) sets
  icon.selected from observable state unless server-reload metadata present.
- oyn.onClick case15: without server reload endpoint -> aowt.h(!selected).
  With ayqy fieldbit1024 -> execute server endpoint with ALLOW_RELOAD=true.
  Our local renderer supplies no such metadata, so it takes existing local path.
- aowp.h(boolean) and nik.h(boolean) forward to active aovo.f(boolean).
  niq.f/g/h delegate to current navigator's setter/enabled/capability; wrapper.w
  rebinds navigator/repeat/shuffle together. Existing bridge discovers these types.
- aokc.f checks h() capability (not Next availability), calls nig.f and e(false).
  aokc.e bit16 represents shuffle enabled, also computes Next/Previous availability,
  notifies aovm listeners. aove.a emits anry(next/prev/repeat/shuffle), iov subscription
  propagates state to ppo.k. Use these observers, not a second enabled-state owner.
- nig.f(true) sets flag, creates a random permutation with Collections.shuffle ONLY
  if either cached array is null; builds successor a[] and predecessor b[] by index.
  Records current index g as traversal boundary. No selection/load/list mutation.
  f(false) only clears enabled flag; it retains permutation and boundary for reenable.
- nig.a() Next: follow successor; return-1 instead of returning to g unless repeat-all
  (aojl.c==1). nig.b() Previous: return-1 at g unless repeat-all. Otherwise predecessor.
  Repeat-one remains handled by aojj.C on AUTOPLAY; manual skips use same shuffle links.
  On this LOCAL path visible row ordering stays unchanged; traversal is randomized.
- aokc.n dispose clears queue/loop state, disables shuffle and nulls both arrays.
  Do not call full dispose just to refresh shuffle indexes: it also clears items.

Authoritative DEX confirmation, avoiding reliance on JADX warnings:
- shuffle-native-methods.txt: original nig.a/b/f, confirms cache/null conditions,
  boundary/repeat branches, Collections.shuffle and array writes.
- shuffle-array-writes.txt: APK-wide writes to nig.a only in nig.f and aokc.n.
- shuffle-owner-references.txt: only hji.c new-instance and aokc.n type check.
These files are under /workspace/scratch/native-playlist-inspection/.
No claim about device UI/reproducibility or all server playlist variants.

## Our current integration and concrete gap

NativeQueueEngineHook.kt discovers shuffle interface/mutable nig implementation and
attaches local rows to aokc. Existing original UI click/service/observer path is
already available. PlaylistRepeatHook currently enables ONLY repeat; local panel
lacks backend shuffle capability metadata so native header normally disables Shuffle.

publish() in NativePlaylistQueuePatch.java: metadata-only update keeps backend and
updates snapshot; entry changes call patch_attachQueue(panel). That bridge reuses
existing aokc, accepts replacement rows and preserves repeat, but neither invalidates
native shuffle arrays nor handles shuffle explicitly. aokc.p -> aojj.t replaces nii
rows/selection; neither clears those arrays. Therefore enabling UI alone introduces
stale index links after add/remove/reorder (same-length reorder is also relevant).
This is a source/control-flow finding; no device reproduction claimed.

## Smallest next implementation, not yet authorized by investigation-only request

1. Enable existing Shuffle view only for exact owned panel when native backend
   reports support; keep original click listener and observer-driven selected state.
2. Keep native flag/permutation authoritative, no custom shuffler or physical reorder.
3. When local entries actually change, safely invalidate native successor/predecessor
   cache and rebuild via original enabled setter after rows/current selection update.
   Discover arrays from native shuffle setter instructions; no obfuscated constants,
   no full dispose. Preserve repeat/current video/time. Leave metadata-only refresh
   and off/on native semantics alone. Validate original control flow before editing.
4. Review native notify timing/player cached button refresh, service reattachment and
   Shorts handoff; do not silently reset enabled state if native instance replaced.

## Proportionate verification

No need for a new JVM reimplementation of random shuffle. No source-text tests.
Native discovery changes: stockAPK resolution/application + SDKDEX verification;
combined repeat/Loop, player+notification controls, playlist-autoplay suppression,
background-play and queue-unselected checks. No other shuffle-specific patch found
in current YouTube Kotlin/runtime sources by shuffle search; structural overlaps
above still require code-path review. Do not change other patches to force precedence.

Phone checks when implemented: >=4items; enable doesn't restart video; icon follows
native state; Next/Previous/autoplay follow same traversal across player/notification;
no-repeat traversal stops at native boundary; repeat-all wraps; repeat-one replays
current; toggle off works at final item and preserves current position; one-item;
add/remove/reorder while enabled has valid navigation; metadata refresh doesn't
reshuffle; background/Shorts return preserves mode and queue. Check ordinary account
playlists unchanged. Do not claim these checked before a device test.
