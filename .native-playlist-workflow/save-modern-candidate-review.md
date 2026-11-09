# PoC36 experimental native Save sheet review

Source: five staged files on PoC35 HEAD559a3354f9f46df4217efb99810735c61fa96ee4.
No feature-source commit. Pre-commit review is BLOCKED, intentionally: batch semantics
are not established. Never bypass the hook or replace this finding with a pass based
on tests, compilation, or the signed-out401 response.

Scope/implementation: queue header Save only; normal dispatcher opens native modern
PAadd_to_playlist sheet. Per-row Save, Clear, ratings, queue state and playback hooks
unchanged. Small QueueSaveActions helper builds typed native command/parameters.
Preserves complete queue order/duplicates, without changing selected index/entries.
Proto tags from stock capture / native generated schemas; no obfuscated production
references, resource IDs, new hooks, settings, custom UI or auth/network layer.

BLOCKER: scalar111.1 comma-separated list is a protocol hypothesis. HTTP401 also
occurs for arbitrary invalid ID text, so401 DOES NOT validate batch semantics.
Phone must verify every queued video saved via existing AND New playlist. If it
only saves one, errors, spins or silently omits items, withdraw/revise this candidate.
Do not call this a finished implementation. Single-video exact wire payload matches
captured stock parameters; that proves only the single-video command serialization.

Conventions/examples: existing QueueRatingActions native-command helper; existing
QueueRatingActionsTest Java fixture pattern; existing playlistMenu native navigation
item. java.util.Base64 and StringJoiner supported at extension minSdk26. No streams
or extra API-level requirement. No unrelated metadata/UI changes. Native sheet field
443434441 is bhul; schema1 is panel loading bfnd; bfnd.requestTemplate2 bfnf;
bfnf panelId2/params3. Earlier bhts441221881/icr case1 is drawer, not actual sheet.
Generic aozm loading code is shared. Actual sheet handler is njy.b.

Interactions: existing native menu navigation/dismissal handles Save exactly as
stock row ShowSheet commands. No callback replacements/early returns/native state
writes. Hide video action buttons/compact action bar don't affect header command;
SaveToWatchLater and normal/account playlist paths unchanged. Feature dependency
and inclusion unchanged. Unselected execution isn't changed; no fresh unselected
run needed for this feature-local action and typed proto-only addition.
No new strings: existing localized playlist_save label and bookmark icon retained.
Version21.20.405 only; selection defaulttrue verified. No broader support claimed.

Validation CURRENT staged implementation:
- :patches:test:27tests,22passed,5unrelatedJamAPKskips,0failures/errors.
- :patches:validatePlaylistQueue combined19related on stock21.20.405: passes;
  SDK12DEX verification/crosshierarchy58171classes/resources and existing queue
  parser/menu/playback/fullscreen invariants; log save-modern-candidate-combined.log,
  BUILD SUCCESSFUL2m33s. This is not Android device or authenticated Save testing.
- Final :patches:buildAndroid + verifyNativeQueueDefaultsLocal: pass10s after checks.
- MPP CRC/current YouTubeMPE/all32native-queueKotlinpayloads exact match.
- git diff --cached --check: passes.
- Mandatory pre-commit hook run: expected failure, explicitly blocking findings.
  Log save-modern-candidate-gate.log. NO source commit or gate bypass.
All command logs under /workspace/scratch/native-playlist-*.

Phone: queue3distinct videos, select middle, Header Save -> modern sheet; save to
existing playlist and inspect all3in order; repeat using New playlist. Cancel/Back
must leave playback/time unchanged. Test one-video queue too. Native membership/
duplicates, portrait/fullscreen and clear/recreate checks follow if basic batch works.
No attached device/emulator, no further log request.

PHONE RESULT: FAILED. Existing Save errors, New playlist empty. All candidate
source changes removed; clean PoC35 restored. Archived patch is not to be reapplied.
