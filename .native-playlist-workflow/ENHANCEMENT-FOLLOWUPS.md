# Native queue enhancement follow-ups

## Implemented; user phone retest passed: fetch missing queue duration directly by video ID

Recorded 2026-11-02, explicitly requested by the user for
`feat/youtube-native-queue-enhancements`. Implemented locally on 2026-11-02 in six uncommitted files. Full tests, a strict
21.23.492 APK check and the local MPP build pass; phone confirmation is pending.
Evidence: `/workspace/scratch/queue-duration-implementation-20261102/REVIEW.md`.

Problem: an individual queued video such as Late Kids’ “Run” can show its title
and channel with no duration. Queue metadata enrichment searches by title and
requires an exact video-ID match. A successful search can omit the requested
video entirely. There is no independent duration fallback, and the existing
metadata-completeness predicate does not require a duration.

Agreed behavior: when a known queued video has no duration, resolve its length
through a direct video-ID metadata request, reusing the existing player metadata
request/cache where practical. Do not make duration depend on finding the video
in search results. Keep existing native formatted bylines, views and age text;
merge the missing field without replacing richer metadata.

Implementation review points:
- Key the result to the requested video ID; never assign the current player's
  duration to a different queued video.
- Keep network work off the main thread and apply results only to the relevant
  live queue entry. Reuse existing in-flight/cache handling where appropriate.
- Preserve valid captured durations. Handle live streams and genuinely unavailable
  lengths without a retry loop or mandatory request for every playlist row.
- Verify that a missing exact search match can still obtain a known duration by
  ID, and that late/failed responses do not erase metadata or alter queue order.

Evidence: `/workspace/scratch/queue-duration-review-20261102/REVIEW.md` and
`summary.json`. The two public search probes show that “Run” misses the relevant
artist while “Late Kids Run” returns duration; the user's exact video ID and
original phone response were not captured. This is separate from the unconfirmed
background stale-title issue #4283.


Rebased on 2026-11-02 to upstream/dev `b031d6408` (2.1.0-dev.6), new feature
HEAD `cf424a72a`. All nine feature commits replayed unchanged; the duration diff
is preserved byte-for-byte and remains uncommitted. The prior duration build
worked on the user's phone. Three strict 21.30.216 APK checks plus suites/build
pass on the new base; its phone retest is pending. New artifact and checklist:
`/workspace/scratch/queue-duration-rebase-20261102/`.

Latest result, 2026-11-02: user reports "all good i think" after the rebased
dev6 enhancement build and full checklist. No issues reported. Duration changes
remain uncommitted; publication/source push has not been requested.
