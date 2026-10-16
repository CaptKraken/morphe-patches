# Native Queue — preview 17

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview17.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview17.mpp).

This preview fixes shared patch behavior and stale queue edits:

- Native Queue menu injection now runs after the existing Add menu setting and playlist/video checks.
- Existing Play next/Play last rows that are overridden for the local queue are reused; only missing actions are added. Disabling the override keeps the original YouTube actions and the separate local actions.
- The player overlay's queue action can start a queue with the currently playing video and open its native panel. Ordinary Watch Later saving retains its original path.
- Drag events are checked against the originating renderer's order revision. Stale events from an older queue/order are consumed without moving entries in the current queue. Separate renderers retain separate revisions even when their protobuf contents compare equal.
- Repeat/shuffle callbacks are inserted before the existing forwarding code using the project's parameter-cloning and free-register utilities. Original method bodies, including earlier patch hooks, remain available when the local queue does not handle a request.

Previously confirmed features remain: native queue UI, automatic and manual navigation, unique entries, reordering/removal, repeat/shuffle, titles/channel/views/upload age/duration badges, native ratings/undo, and the native Save playlist menu/dialog. The playing entry cannot be removed; dismissing the player clears the queue; opening another regular video appends and plays it without duplicating an existing entry. Minimizing, PiP, and closing only the Queue panel retain the queue.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Select **Native temporary queue (experimental)** alongside other desired patches from this source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `dc2171ec3ba7c4b630dd58a0a1342ee1bd0ab796` on `feature/youtube-native-queue`. Preview manifest metadata is applied only to this artifact; the contribution source retains upstream project metadata, README and notices. Development notes and the additional combination/isolation harnesses are kept outside the contribution source.

Validation: all 60 queue tests passed; the full patch test task had 63 passing tests and 5 unrelated Jam APK tests skipped. The resource checker validated 90,681 strings. Patch/dependency application, Android DEX/cross-DEX verification and resource compilation passed against stock 21.20.405, both standalone and alongside Save to Watch Later button, Restore original titles, Return YouTube Dislike, Disable playlist autoplay, Hide player overlay buttons and Loop video. The combination check inserted test hooks into the native repeat/shuffle methods before Native Queue ran and confirmed both survived. The same related patches were also checked with Native Queue unselected: its flag stayed false, native control hooks were absent, and the persistent queue path remained available. These checks cover the tested patch selection/order, not every possible patch or setting combination. The Android bundle contains `classes.dex` and retained upstream license notices. No real account vote or playlist creation request was sent during cloud checks. No emulator or Android device was attached. Menu actions, overlay queue startup, rapid reordering, repeat/shuffle and ordinary playlists still need phone testing with this preview. Only 21.20.405 is advertised.

SHA256: `34c49b201b34f67acf0b90f7fd31c99d597d247e21b9aeabbeba195b791f28ae`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
