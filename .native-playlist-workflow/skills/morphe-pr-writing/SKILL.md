---
name: morphe-pr-writing
description: Use whenever drafting, reviewing, creating, or updating a Morphe Patches PR title or description, including copy-paste text requested in chat. Enforces the user's AI disclosure preference, current repository template, issue reference, and honest test reporting.
---

# Morphe PR writing

This is the user's local workflow, not an upstream requirement. Keep this skill
outside the upstream feature diff. Follow explicit user instructions over this
checklist. Do not infer permission to create/edit a PR or post a comment from a
request for text. Drafting a title/description needs no publication approval.

## Before drafting

1. Read the active checkout's `.github/PULL_REQUEST_TEMPLATE.md` every time.
   It is authoritative for section names and checklist wording; do not rely on
   a previous PR or a remembered template. Read CONTRIBUTING.md when applicable.
2. Identify the actual source branch/diff, linked issue, requested behavior,
   setting defaults, and material shared-path changes. Describe the final change,
   not abandoned experiments or conversational history.
3. Read recorded validation for the exact source. Separate unit tests, build and
   string checks, actual APK patch/SDK checks, user-reported phone testing, and
   direct app/visual testing. Skipped checks are not passes.

## Required content

- Include an early AI disclosure, even for a short description or text-only
  response. This is the user's explicit preference, not an upstream mandate:

  > [!NOTE]
  > this PR was developed with AI assistance from OpenAI Codex.

  Keep it before the template sections unless the user requests another location.
  Preserve the user's existing disclosure when updating their description. Do not
  invent a model/version or claim the user performed unreported tests.
- Preserve every actual template section and its order. Currently these are
  Description, Additional context, and Test results, but re-read the file.
- Reference the actual issue: `Closes #NNNN` only if the change resolves it;
  otherwise use `Refs #NNNN`. Never leave an empty `Closes #` placeholder.
- Explain the user-visible feature, relevant scope, defaults and optional controls.
  Keep additional context short; do not drown the reviewer in helper names,
  request internals, build history, or irrelevant test counts.
- Copy the test checklist verbatim from the current template, including emphasis
  and the optional experimental qualifier. Change a checkbox to checked only
  when evidence supports its full wording for the relevant final source. Do not
  replace this checklist with a custom testing section.
- Put automated results and remaining device checks in a short paragraph under
  Test results, separate from the preserved checklist. APK validation/build success
  does not establish phone playback, visual correctness or all-version support.
  Do not check minimum/maximum phone coverage from SDK verification alone.

## Mandatory final read-through

Before returning or publishing the text, compare it with the current template:

- [ ] AI disclosure is present and accurate.
- [ ] All current template sections appear in order.
- [ ] Issue reference is present and correct, when applicable.
- [ ] Feature scope, defaults and any mentioned behavior match the final diff.
- [ ] Template test checklist wording is preserved exactly.
- [ ] Checked boxes and validation claims have actual supporting evidence.
- [ ] Remaining checks are stated without presenting them as passes.
- [ ] No invented support, empty placeholders, stale PR history or unauthorized posting.

Use concise prose and the user's casual lowercase style when requested. Provide
copyable title and body when asked for text. If creating a PR is authorized, use
a structured body or `--body-file` and attach the created PR with the Codex app
artifact tool. A saved local draft is not a published PR.
