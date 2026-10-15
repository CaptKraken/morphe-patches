# Native Queue — preview 15

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including the upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview15.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview15.mpp).

This preview changes queue editing and player-session behavior:

- The playing entry cannot be removed. Its native swipe action and custom Remove menu are omitted, and the queue model rejects delayed removal events after selection changes. Other entries remain editable and reorderable.
- Closing/dismissing the player resets the queue and modes, restores the native backend when available, and drops cached response/controller/navigation references. Cleanup does not refresh or reopen the closed player. Minimize, PiP, pausing, screen-off playback and closing only the Queue panel retain the queue.
- Opening another regular video while queued plays it and appends it at the end. An existing entry is selected without moving or duplicating it. Prefetched metadata cannot append videos, and late responses cannot create an empty queue. Explicit account playlists retain native ownership.
- Delayed refreshes skip a closed player, and a closed player's cached response is not used to seed a new queue.

All previously confirmed features remain: native queue UI, automatic and manual navigation, unique entries, reordering/removal, repeat/shuffle, titles/details, native ratings/undo and the native Save playlist menu/dialog. Preview 15 needs phone testing of removal protection, Home/search opening, X/swipe/PiP dismissal, and starting a fresh queue after closing. Account-cache invalidation also needs device coverage for account switching, sign-out/incognito and votes already in flight.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone for YouTube. Select **Native temporary queue (experimental)** alongside other desired patches from this source. Patch the untouched YouTube 21.20.405 APK. Keep **Override feed flyout queue button** enabled if needed.

Built from source commit `af49a28574e44223769f9abe5dce4044bbc25547` on `feature/youtube-native-queue`. Preview manifest metadata is applied only to this artifact; the contribution source retains upstream project metadata and README.

Validation: all 51 queue tests passed; the full patch test task had 54 passing tests and 5 unrelated Jam APK tests skipped. The project resource checker validated 90,681 strings. Actual patch/dependency application, shared player-type hook ordering, closed-session cleanup assertions, native binding checks, Android DEX/cross-DEX verification and resource compilation passed against stock 21.20.405. The Android bundle contains `classes.dex` and retained upstream license notices. No real account vote or playlist creation request was sent during cloud checks. No emulator or Android device was attached. Only 21.20.405 is advertised.

[Usage and device checklist](https://github.com/CaptKraken/morphe-patches/blob/af49a28574e44223769f9abe5dce4044bbc25547/docs/youtube-native-queue.md) · [Branch review](https://github.com/CaptKraken/morphe-patches/blob/af49a28574e44223769f9abe5dce4044bbc25547/docs/youtube-native-queue-review.md)

SHA256: `d683dce6e3074a27eb15b28ff80894d34abe701adf5e6ffa40252815c5a506a2`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
