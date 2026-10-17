# Native playlist queue — step 1

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step1.mpp)

Source: [feature/youtube-native-playlist-queue](https://github.com/CaptKraken/morphe-patches/tree/b766dd67053d4b05a1b834658f7681b5ac39e77c)

This is a fresh, incremental rebuild from main, not a continuation of the old preview bundles.

Import the MPP as a local patch source in Morphe Manager, use an **untouched YouTube 21.20.405 APK**, and explicitly select **Native playlist queue**. The patch is not selected by default. This bundle contains the regular patches too: select patches from this source only rather than mixing two full Morphe bundles.

## Phone checks

1. Start a regular video A outside an account playlist.
2. Open a different video's feed three-dot menu and choose **Play next in native queue**.
3. The native portrait Queue panel should show A then B. A should continue without restarting or seeking.
4. Add C the same way. The panel should show A, C, B.
5. Check that the action is absent in the player gear menu, Shorts, account playlists, and the playing video's own menu.
6. Close the player and reopen a video; the temporary display should reset. Check ordinary Add to queue behavior independently if that patch is selected.

This milestone implements insertion and native panel presentation only. **Queued titles may show video IDs. Row playback, automatic advancement, player Next/Previous, editing, duplicate prevention, shuffle/repeat, metadata enrichment, fullscreen access, ratings and saving are not implemented.** Do not use Next or automatic playback as a test of queue navigation yet.

## Validation

Two focused production-state tests passed. Stock-APK application and Android SDK DEX/resource verification passed for the new patch alone, the final source combined with six related patches, and those related patches with the queue patch unselected. String validation checked 90,669 strings. The final Android bundle contains classes.dex and passes ZIP integrity checks. Phone behavior remains pending.

The bundle differs from the build output only in distribution manifest name/description/author/source/website. Source license and NOTICE are preserved.

SHA-256: `45c5b572480bd2ef33bbe2f16f6b2658595826e1228ae8d11f3aa9b9713fcb49`
