# Codex workflow backup

Standalone backup of the local Morphe development instructions and lessons,
recorded on 2026-11-09. This branch contains no application implementation and
is not intended to be merged into `dev`, `main`, or a feature PR.

## Current instructions and lessons

- `AGENTS.md`: copy of `/workspace/AGENTS.md`.
- `.native-playlist-workflow/MAINTAINER-CONVENTIONS.md`: observed maintainer
  conventions, concrete examples, and lessons from PR #4404's refactor.
- `.native-playlist-workflow/WORKFLOW.md`: development and delivery workflow.
- `.native-playlist-workflow/STATE.md`: checkpoint journal. Read the newest
  entries at the top; older branch names and decisions are historical.
- `.native-playlist-workflow/pre-commit`: mandatory staged-tree review gate.
- `FILE-MANIFEST.json`: source locations, sizes, and SHA-256 hashes.

The other workflow Markdown/JSON files preserve historical investigations,
feature history, PR drafts, and checkpoints. Older role/delegation plans and
PoC instructions are reference material, not current instructions. Follow the
latest user decisions and current instructions rather than replaying old plans.

## Environment instructions / skills

`environment/start-instructions.txt` preserves the configured environment start
skill/instructions, including its older embedded recovery copies.
`environment/install.sh` preserves the configured setup script. These are backup
snapshots, not scripts run by this branch. Review them before reuse: older embedded
state and setup commands can predate the current workflow and dependency versions.
Prefer the separately saved current files when restoring instructions and lessons.

There were no project-local `SKILL.md` files in `/workspace/.agents` or
`/workspace/.codex` at backup time. Managed connector/plugin skills are supplied
by their providers and are not included here.

## Restore carefully

Check out this branch separately from application development. Compare with any
existing workspace copies before copying `AGENTS.md` and the workflow directory
back into `/workspace`; preserve newer files and current uncommitted work.

Install the saved gate into the application repository only when restoring its
local tooling. For example, from this backup checkout:

```sh
task_repo=/workspace/morphe-patches
task_hook_path=$(git -C "$task_repo" rev-parse --path-format=absolute --git-path hooks/pre-commit)
install -m 755 .native-playlist-workflow/pre-commit "$task_hook_path"
```

The hook requires a review record for the exact HEAD, branch, and staged tree.
Read `AGENTS.md` for generating and completing it; installing the hook is not a
substitute for review. Validation of this backup does not establish that an
environment restore or Android playback has been tested.

APKs, MPPs, caches, credentials, raw logs, old source patches, Git metadata, and
binary recovery archives are excluded. Original local files remain in place. Backup text copies normalize whitespace where required by the review gate;
the manifest records original hashes for those files.
`LICENSE` and `NOTICE` retain the repository's existing licensing notices.
