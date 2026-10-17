# Native playlist queue — step 1, fix 3

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step1-fix3.mpp)

Source: [063fb067d](https://github.com/CaptKraken/morphe-patches/tree/063fb067df785bc8c813f1ec3b65bdf90d13a678)

Replace the previous local MPP source in Morphe Manager with this file. Patch an **untouched YouTube 21.20.405 APK**. **Native playlist queue is selected by default** in this test bundle. Use patches from this source only to avoid mixing full Morphe bundles.

## Changes in this build

**Play next in native queue** dismisses the menu and shows the existing localized **Added to queue** toast without explicitly expanding the playlist panel. Open the queue yourself from YouTube's native collapsed playlist entry when ready. The unused open-panel bridge was removed; all playlist rendering remains native.

**Native playlist queue** now has default selection enabled for repeated phone testing. Compatibility remains limited to YouTube 21.20.405.

YouTube's [official Android queue help](https://support.google.com/youtube/answer/9546304?hl=en&co=GENIE.Platform%3DAndroid) documents queue additions without interrupting the current watch session, but does not specify panel expansion after each addition. Exact current stock Premium behavior was not reproduced here; this build follows the user's requested browsing behavior.

This remains a fresh incremental rebuild from main; no code was copied from the old queue branch.

## Phone checks

1. Import this MPP and check that **Native playlist queue** is included in the default patch selection.
2. Play a regular video A outside a playlist, then minimize it or scroll to recommendations.
3. On video B's three-dot menu, choose **Play next in native queue**. Expect the confirmation toast while browsing stays in place, with no automatic panel expansion.
4. Return to A and manually open its native Queue entry. Expect A, B, while A continues without restarting or seeking.
5. Close the panel, add C, and verify the same unobtrusive confirmation. Manually reopen the panel and expect A, C, B.
6. Close/reopen the player and create a fresh queue. Existing Add to queue behavior should remain independent.

This milestone includes insertion and native panel presentation only. Queued titles may be video IDs. Row playback, automatic advancement, player Next/Previous, editing, unique entries, shuffle/repeat, metadata enrichment, fullscreen access, ratings and saving are not implemented yet.

## Validation

The two focused state tests, stock-APK application alongside six related patches, and Android SDK DEX/resource verification passed. The final Android build passed. A local metadata check loaded the actual MPP definition and verified default selection is true. No string resources changed. Manual confirmation of browsing preservation and Manager selection remains pending.

Source license and NOTICE are preserved. Only distribution manifest metadata differs from the build output. Earlier bundles remain here for reproduction; use the fix 3 download above.

SHA-256: `537e15d5f51cc4dde9a0a279f220fe0d0a61a68e59e650cc66c7a029679e4c44`
