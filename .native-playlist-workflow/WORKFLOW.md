# Native playlist queue development workflow

Read /workspace/AGENTS.md. Work directly in the primary conversation; no subagents,
model routing, delegated stages, or handoffs. The user removed that flow entirely.
Keep this workflow, checkpoint and review tooling outside the upstream patch diff.

Before implementation/review, read MAINTAINER-CONVENTIONS.md for the concrete Java,
Kotlin/Smali, refactoring, and fingerprint patterns established by the maintainer's
PR #3953 edits. Apply them while preserving tested behavior and existing validation.
Consult IMPLEMENTATION-PATTERNS.md for relevant examples before writing another
implementation. Follow the user's is-prefixed boolean-variable naming rule in
AGENTS.md. Record newly reviewed reusable patterns with source links, applicability,
and safety limits; distinguish user rules from observed maintainer choices.

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
   Use Git history to find older backup checkpoints.

Reuse valid evidence for unchanged code. Do not rerun expensive checks without a
source change or concrete concern. Poll existing builds; avoid duplicate builds.
No additional debug-log requests: use existing APKs/captures and honest device limits.

The Cloud startup instructions restore missing workflow files from the backup
branch. Keep STATE.md as a short handoff with the next action and evidence links.
Search archived journals by issue or symbol when earlier context is relevant.
Start a fresh chat for an unrelated issue and carry over its short handoff.
Delivery gate learned from PoC56: APK validation/test tasks can regenerate the JVM jar archive and strip its root classes.dex even after buildAndroid passed. Run :patches:buildAndroid LAST after all Gradle checks, copy to a unique delivery file, then check THAT file. Require root classes.dex (not just generated/extensions/signature.dex), valid DEX SHA1/Adler32, native queue getter, ZIP CRC/current MPE/Kotlin payloads, and actual PatchLoader.Jar inventory. The JVM loader check alone cannot detect missing Android DEX; both checks are required. Use /workspace/scratch/native-playlist-inspection/VerifyQueueBundle.java (read-only external tool), with dependencies from the current Gradle test worker classpath excluding project main/test class dirs. No further archive-producing Gradle calls after finalization without repeating this gate. Verify uploaded download hash/inventory too. AndroidManager execution remains a device check.
