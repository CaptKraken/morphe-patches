# Native Queue — preview 16

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview16.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview16.mpp).

This preview adds thumbnail duration badges to the native Queue rows. The existing exact-video metadata lookup now reads YouTube's formatted length text, with its normal thumbnail time overlay as a fallback. The value is supplied to the native playlist duration field, so YouTube controls the badge's styling and playing indicator. Localized lengths and videos longer than an hour retain YouTube's formatting. Missing lengths and live/upcoming status overlays do not create a fabricated duration. Duration updates retain row identity, ordering, selection and playback modes. Entries whose metadata lookup is unavailable can still lack a badge.

Previously confirmed features remain: native queue UI, automatic and manual navigation, unique entries, reordering/removal, repeat/shuffle, titles/channel/views/upload age, native ratings/undo, and the native Save playlist menu/dialog. Preview 15's session behavior is retained: the playing entry cannot be removed; dismissing the player clears the queue; opening another regular video appends and plays it without duplicating an existing entry. Minimizing, PiP, and closing only the Queue panel retain the queue.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Select **Native temporary queue (experimental)** alongside other desired patches from this source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `f481a2ffca5b35315b6c0b301b31329003cf34fa` on `feature/youtube-native-queue`. Preview manifest metadata is applied only to this artifact; the contribution source retains upstream project metadata, README and notices. Development notes are kept outside the contribution source.

Validation: all 54 queue tests passed; the full patch test task had 57 passing tests and 5 unrelated Jam APK tests skipped. The project resource checker validated 90,681 strings. Actual patch/dependency application, native duration-field binding, existing navigation/session/rating/save assertions, Android DEX/cross-DEX verification and resource compilation passed against stock 21.20.405. The Android bundle contains `classes.dex` and retained upstream license notices. No real account vote or playlist creation request was sent during cloud checks. No emulator or Android device was attached. Duration badge appearance, transition to the playing indicator, and durations after reordering need phone verification. Account switching, sign-out/incognito and votes already in flight also need device coverage. Only 21.20.405 is advertised.

SHA256: `7baff10868473b80799b552331ceb99f7c85a141734c4f2a0965792ec193d412`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
