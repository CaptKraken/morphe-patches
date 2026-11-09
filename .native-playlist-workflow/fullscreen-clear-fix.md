# Fullscreen playlist action retained after Clear queue — 2026-10-20

User phone report: cleared local queue from fullscreen side panel; fullscreen
playlist button remained. PoC33 entrypoint works on phone; PoC34 title fix still
awaits explicit feedback. No further logs/screenshots requested; no agents/device.

Verified stock21.20.405 native cause path:
- Existing refresh bridge calls mzx.j(newModel) and resets only mzx.e (rendered).
- mvz case19 requests apys.k -> apys.j -> apys.r. apys.r returns its cached
  ComponentTree (apys.e) if nonnull, regardless of replacement apyj model.
- Thus original standalone model can be restored correctly in Java, yet old rendered
  quick-actions button survives. Serialization/helper tests did not cover this
  native presenter lifetime; they never proved UI disposal.
- Native watch reset mzx.k(fju) calls j(null), resets e/n, and apys.oN(null).
  fju parameter is unused (native instructions overwrite it with null).
- apys.oN unmounts view tree, nulls cached e and layout callback, releases component
  subscriptions/presenter callbacks. Subsequent native render constructs a new tree.
  This is UI-only; no player backend/navigation/queue disposal method is involved.

Small fix: existing typed patch_refreshQuickActions bridge invokes the already
fingerprinted original native reset with null before applying retained original
model and re-emitting native dimensions. Removes incomplete direct rendered-flag
write; uses native reset for all associated presenter state. Original data, native
visibility/filter controls/layout callback retained. No custom views, new state,
queue recreation, reload, pause, activity launch or orientation change.

Two files: FullscreenPlaylistHook.kt and PlaylistQueueApkBuild.kt. Existing emitted
DEX verifier extended to require native reset before setter and confirm native
reset calls the same setter. SDK verifies call type/access/register compatibility.
Combined19related realAPK + JVM suite running fullscreen-clear-combined.log. Build
MPP AFTER validation, mandatory exact-tree gate before one fix commit. No full
unselected/standalone repeat warranted: same discovered types/dependency/inclusion
boundary; new invoke is within feature-only bridge, covered by combined SDK check.
Other-patch overlap: QuickActionsMargin callback unchanged; native cleanup/rebind
lets quick_actions.e and icon filters run again (Hide video action buttons settings
remain effective). HideRelated/fullscreen transitions remain native; action payloads
unchanged. NativeReset only cleans this controller's Elements presenter; all other
queue/backend/Loop/autoplay/media/Shorts/shared helper paths untouched.

Phone required: fullscreen queue -> clear -> panel closes and playlist action absent
when controls next appear; playback/time continuous, other actions intact; recreate
queue without restarting player -> button reappears; close/open/rotate/account
playlists/hide settings. APK/static checks do not prove phone rendering behavior.

Delivered PoC35: source559a3354f9f46df4217efb99810735c61fa96ee4 and artifact
1f342cdb6ac736806dc642b7fa873c53a6783af7 pushed. Exact gate passed. Combined APK
validation passed1m41s, final build/default5s and CRC/currentMPE/all32payload verified.
Phone checks pending; STATE has exact artifact/hash/logs. No further source edit.
