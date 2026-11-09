# Fullscreen playlist entry point — 2026-10-20

Research only, stock YouTube 21.20.405. No source edits/commit/build/new MPP.
Source remains e1a6ef238d461d6847f91f0f83fc44d1c1b9798d (PoC32).
User asks to investigate native fullscreen Show playlist button and side panel.
No agents used. No attached phone/emulator; no device validation claimed.

## Verified native paths

- Existing queue already binds native EngagementPanelPlaylistPanelController pqj
  and model pqh. Constructor panel ID engagement-panel-playlist; pqj.l renders
  accepted native panel and registers through native engagement manager aheg.
  pqj.m(model) builds native batg engagement panel descriptor. This supports using
  the existing queue model for the native landscape panel, rather than a second UI.
- Native collapsed entry point listener oyn.onClick case16 checks manager.K(panelID),
  builds bhss show-panel command (d=1,e=engagement-panel-playlist), dispatches through
  original ahnn. Do not assign an enum meaning to d=1 without further verification.
- pjb.run case7 also opens playlist through manager.S(native descriptor,true,
  native command context,false). pge.l handles bhss playlist commands and defers
  dispatch until its native player state is ready. Preserve this transition path;
  do not force orientation or panel View visibility ourselves.
- Fullscreen playlist icon belongs to quick_actions.e Elements UI. Existing repo
  QuickActionButtonsFilter identifies yt_outline_experimental_playlist and
  yt_outline_list_play_arrow; respects HIDE_QUICK_ACTIONS and
  HIDE_QUICK_ACTIONS_PLAYLIST_BUTTON. Native integration must retain those settings.
- QuickActionsElementSyntheticFingerprint resolves mvj.a(View). Its final branch
  binds quick_actions_element_container to mzx.m, plus native layout/touch/overlay
  callbacks. Earlier apparent mwc lead was false: mvj case3 handles hide-controls
  top button, not quick-actions. nbo binds quick-actions overlay into timebar UI.
- mzx receives bapk via qcy.f subscription + kct case14. It builds native apyj
  through apzr.d and binds via apys.k. Native visibility and cleanup are already
  handled in mzx.i/k and its subscriptions; retain these.
- kct case14 obtains accepted response contents singleColumnWatchNextResults
  (bcuw oneof78882851 -> bfwq), bfwq.n (wire field37), then bgvi extension
  bapq.a (153515154) -> bapk. bapk also wraps many OTHER Elements components;
  recognizing that extension alone does not identify a quick-actions component.

## Remaining discovery / false leads excluded

Available native-mix-full.pb and android-next.pb captures both have single-column
contents but NO wire field37. They cannot establish the fullscreen button model
or condition that includes playlist action. The normal playlist engagement model
alone is not proof that a button will be generated. Need inspect actual native
quick-actions model/template inclusion/binding before adding production hook.
Both captures contain list_play_arrow icon bytes inside transcript Elements global
asset table; this is NOT evidence of a captured fullscreen playlist button. Models
erroneously saved as *.quick-actions.pb during exploration are transcript models,
not usable quick-action fixtures. No production inference should use those files.
Shared EngagementPanel.open merely tracks open panel IDs; it does NOT open UI.

## Smallest intended implementation and checks

Reuse native quick-actions playlist action + original show-panel command, with our
already accepted native panel/model when local queue owns playback. No custom
fullscreen button/layout, duplicate queue state/controller, forced visibility,
account-playlist interception, or changes to other patches. Discover exact button
model inclusion contract first. Production references must be derived through
Fingerprints/stable resources/types, not obfuscated research names or numeric IDs.

Native-change checks: real APK resolution/application + SDK DEX/resource verification;
combined Hide video action buttons/QuickActionsMargin, HideRelated, fullscreen open/
exit, player controls and queue-unselected. No tests that merely match source text.
Phone: active queue -> fullscreen icon -> right panel; rows/count/actions same queue;
close/reopen/rotate retains position/order; account playlists unchanged; native hide
playlist-button and hide-all quick-actions settings still effective.

Decompiled files: /workspace/scratch/native-playlist-inspection/fullscreen-java/
(mvj,mzx,mzv,mvz,nbo,pge,pjb,kct,qcy,bapk,bapl,bapo,bapq,apzr,bfwq,bcuw).
Existing pqj,oyn source: /workspace/scratch/native-playlist-inspection/java/.
No source changes means no new build/tests warranted for this investigation.

## Verified follow-up and implementation (same date)

Correction to initial research: bcvl.g is playerOverlays (wire14), NOT contents;
bfwq is playerOverlayRenderer (78882851), not singleColumnWatchNextResults.
Initial no-field37 findings were valid for those captures but the container name
in earlier notes/checkpoint was wrong. Native setter trace itself was correct.

New public Android21.20.405 responses with phone dimensions/device context DO contain
playerOverlays.playerOverlayRenderer field37. Both standalone and public account
playlist responses use quick_actions.eml and model386137609. Playlist adds field17:
button1(icon2,onTap4,a11y5), style2=2. Tap command169495254 -> showPanel138681778 ->
panelID1 engagement-panel-playlist. Standalone omits17; normal playlist uses
list_play_arrow icon; Mix uses mix icon. Other values/layout data are preserved,
including native actions/templates/config; no captured visitor/tracking copied.
Captured responses remain scratch only. Probe scripts and outputs metadata/fullscreen-*.

Implementation adds this binding ONLY while published local queue model and native
backend own playback. Existing account playlist action is returned untouched.
QueueFullscreenActions captures the original accepted quick-actions model with a
weak native controller reference; null setter clears baseline. Publication/release/
account handoff trigger refresh only when ownership differs from last binding.
No queue state in this helper; renderedWithQueue is a presentation refresh cache.
The typed native bridge calls the original setter, resets its original rendered
flag (discovered from native cleanup), and re-emits BehaviorSubject's LAST measured
size via its native getter/emitter. This runs original visibility/measure/render
callback, preserving lifecycle/fullscreen/filter decisions. No activity launch,
orientation override, custom View or duplicate panel/back-end. Refresh failures log
and cannot roll back successful playback attachment. Native class/interface/parser
references discovered from stable resource callback, scoped signatures and native
Size constructor relation; no obfuscated production names/numeric resource IDs.

Interactions reviewed: QuickActionsMargin shared fingerprint invalidated before
reading; original view callback/listener unchanged. Hide video action buttons native
quick_actions.e/icon filtering sees the SAME native icon/component. No forced
visibility or filter bypass. HideRelated and fullscreen open/exit native transition
manager remain original; original panel registration pqj.l and show command used.
Loop/autoplay/player+notification paths unchanged; existing combined harness covers
19 related patches. Native-only install; unrelated selections do not gain dependency
on queue patch. Tests explicitly verify absence of controller bridge unselected.

Validation in progress/final results in STATE. Known early failures corrected before
successful checks: Kotlin API usage (required checkCast type, typed getInstruction,
CharSequence descriptor and nonexistent mutableMethodBy); callback flag constraint
and IPUT vs IPUT_BOOLEAN discovery. No phone/emulator; neither APK verifier nor
captured-message checks prove Android fullscreen rendering. One feature/commit.

Final review corrections: require publishedModel!=null, because null==null during a
controller handoff is not ownership. Do not gate action-binding ownership by hidden
PlayerType: native UI visibility already owns this; background watch model loading
must retain queue action for foreground return. Final runtime ownership is nonnull
queue + nonnull published model + current controller model identity + native local
queue backend. Visibility stays native. These guards touch no playback state.

Delivery complete: source34d7630e60c6ab9e85bc12d6d7511b35250881c2, gated/pushed;
PoC33 fullscreen-playlist MPP artifactb5941234a728154f5898d13cd47b0e1e4b4619b4 pushed.
Final SHA e0ebdb31b4cc0752785b824875887703873c4536dbaf4fa58a84824730be6a92.
Validation details and outstanding phone checks in STATE.md. No device verification.
