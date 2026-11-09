# Native playlist queue development workflow

Read /workspace/AGENTS.md. Work directly in the primary conversation; no subagents,
model routing, delegated stages, or handoffs. The user removed that flow entirely.
Keep this workflow, checkpoint and review tooling outside the upstream patch diff.

Before implementation/review, read MAINTAINER-CONVENTIONS.md for the concrete Java,
Kotlin/Smali, refactoring, and fingerprint patterns established by the maintainer's
PR #3953 edits. Apply them while preserving tested behavior and existing validation.

1. Read STATE.md, inspect the actual branch/HEAD and local changes. Resume the first
   unfinished step; do not restart completed work or overwrite user changes.
2. Select one user-agreed feature/fix. Establish its observable behavior, native
   state owner/control flow, existing project examples, interactions and validation
   before editing. Separate observed facts from hypotheses.
3. Implement the smallest complete change. Preserve native UI/actions and shared
   patch behavior. Do not add future-feature infrastructure or unrelated cleanup.
4. Review the actual staged and cumulative feature diffs. Resolve blocking findings
   and run proportionate checks. Follow the mandatory pre-commit gate in AGENTS.md;
   never bypass it or substitute a build for semantic review.
5. Commit one feature/fix normally after the gate passes. Build/publish phone-test
   bundles only within the user's authorization. Device behavior remains pending
   until tested on a device; no source-text or JVM test proves Android playback.
6. Update STATE.md after meaningful findings and checks and before finishing. Keep
   current feature/status, branch/base/HEAD, changed files, decisions, real outcomes,
   artifact/hash/link, user feedback, limitations and next action concise.

Reuse valid evidence for unchanged code. Do not rerun expensive checks without a
source change or concrete concern. Poll existing builds; avoid duplicate builds.
No additional debug-log requests: use existing APKs/captures and honest device limits.

The environment start_skill stores recovery copies of AGENTS.md, this workflow,
STATE.md and the local commit hook. Preserve newer existing copies on recovery.
Update saved copies when work finishes. Saving configuration is distinct from
publishing an environment snapshot and does not execute setup or prove restoration.
Delivery gate learned from PoC56: APK validation/test tasks can regenerate the JVM jar archive and strip its root classes.dex even after buildAndroid passed. Run :patches:buildAndroid LAST after all Gradle checks, copy to a unique delivery file, then check THAT file. Require root classes.dex (not just generated/extensions/signature.dex), valid DEX SHA1/Adler32, native queue getter, ZIP CRC/current MPE/Kotlin payloads, and actual PatchLoader.Jar inventory. The JVM loader check alone cannot detect missing Android DEX; both checks are required. Use /workspace/scratch/native-playlist-inspection/VerifyQueueBundle.java (read-only external tool), with dependencies from the current Gradle test worker classpath excluding project main/test class dirs. No further archive-producing Gradle calls after finalization without repeating this gate. Verify uploaded download hash/inventory too. AndroidManager execution remains a device check.

CURRENT: History rebuilt into16behavior commits on feature/youtube-native-playlist-queue-dev at bddfcc67f3b643df63363ccb9c759284ffa6982d. FulltreeidenticalPoC58. Historicalmain-basedbranch/PoC57 andarchiveddevbulkbranch preserved. Read STATE.md and FEATURE-HISTORY.md. Awaitphone/userreview; noPR/agents/newfeatures.
