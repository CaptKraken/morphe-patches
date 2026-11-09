# Native shuffle implementation / review notes

Scope: native queue Shuffle, one feature commit. Original click listener, observers,
random algorithm, navigation and repeat rules retained. No new strings/UI/settings.

- NativeQueueEngineHook discovers mutable shuffle setter via stable Collections.shuffle
  fingerprint scoped to already discovered queue type; extracts flag writer + two [I
  index arrays. Getter is resolved through that flag read and native shuffle interface;
  remaining boolean interface getter is capability. Missing/ambiguous paths fail.
- New mutable bridge disables only mutable shuffle flag without observer notification,
  then nulls both original index arrays. attachQueue now captures current native flag,
  clears links before accept, restores repeat and shuffle through queue observers after
  acceptance. Fresh bridge locals8 (v0..v7, p0=v8,p1=v9), no wide parameters/unsafe
  original-method registers. Native owns mode and rebuilds random links. Snapshot-only
  path unchanged, no full dispose, no player load/seek call added.
- Native header ImageView field discovered from shuffle resource -> findViewById ->
  cast -> field store, all consecutive native instructions. Typed helper only sets
  enabled status. Runtime exact owned panel + active local backend + native capability
  guard; original click/listener/icon selected state remain intact. Header capability
  refreshed from native queue event too, avoiding disabled view after reattachment.
- Focused APK verifier checks actual native cache fields/reset/accept/restore order,
  captured/restored flag register and snapshot path. Initial verifier incorrectly
  matched interface and implementation by method name; corrected concrete selection.
  First SDKDEX/hierarchy had passed before verifier failed. Second run passed cache
  check; final review added header refresh then fresh final combined/unselected run.
  No production fix bypassed a verifier failure. See logs for actual final outcomes.

Interactions reviewed: existing header RepeatHook shared path, native repeat setter,
autoplay-suppression/Loop, player and notification availability/native observers,
background/Shorts owner changes. No new listener/return/setting overrides. Native
mode event includes shuffle bit16; native aove observer re-evaluates navigation and
emits repeat/shuffle state. Header enabled status isn't gated on Next availability,
so reaching traversal end doesn't prevent toggling shuffle off. Ordinary panel paths
return before changes; queue-unselected verifier checks no generated host interfaces.

Device checks pending: four-item traversal, icon on/off incl final item, current
video/time preserved, Prev reversal, auto advance, repeat modes, one item, edits with
shuffle enabled, metadata refresh unchanged order, notification/background/Shorts,
ordinary playlists. Visible rows retain normal order (native local shuffle traverses
links rather than physically reordering rows).
