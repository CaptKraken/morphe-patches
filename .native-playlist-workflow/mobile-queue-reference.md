# YouTube mobile queue reference — checked 2026-10-20

Official Android documentation, fetched successfully:
https://support.google.com/youtube/answer/9546304?hl=en&co=GENIE.Platform%3DAndroid
Title: Queue videos on YouTube - Android phone & tablet - YouTube Help.
Exact instruction: "Select Play next in queue or Play last in queue."
Mobile queue requires YouTube Premium. Same guide describes queue More -> Save to
playlist, choosing an existing playlist/Watch Later or creating a new playlist,
and a bottom confirmation message. This confirms BOTH native mobile insertion
choices; do not confuse with YouTube Music or desktop-only Add to queue.
Our patch currently exposes Play next; no Play last implementation requested yet.
Official help is documentation, not verification that every menu/server experiment
in21.20.405 exposes both simultaneously. Native local APK string table did not
contain these server-provided action labels; absence there was not proof of absence.
Fetched page scratch /workspace/scratch/queue-doc-research/official-android.{html,txt}.
No code/build/new bundle for this documentation request.

Additional screenshot walkthrough read successfully:
https://smartphones.gadgethacks.com/how-to/unlock-youtubes-new-video-queue-your-mobile-device-0385268/
Published2022-12-19 (historical rollout UI, not proof every detail persists in2026).
Shows Play last in queue, collapsed bottom queue, drag-to-reorder once >=2videos,
and per-item three-dot removal. Article says mobile and desktop queues did not sync
at rollout; do not assert current cross-device policy from this older guide.
Current official Help confirms Play next/last and Save to playlist; these plus
historical reorder/remove evidence are not an exhaustive current Premium queue
specification. Repeat/shuffle, swipe UI, restore persistence/fullscreen/background
behavior not independently verified as Premium queue features in these sources.
Do not infer Premium queue parity merely because our native playlist panel has them.
