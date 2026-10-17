# Native playlist queue — step 2, fix 2

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step2-fix2.mpp)

Source: [e021250a4](https://github.com/CaptKraken/morphe-patches/tree/e021250a4952bd030b2f5603dae35254f159f487)

Replace the previous MPP source in Morphe Manager and patch an untouched **YouTube 21.20.405 APK**. Native playlist queue remains selected by default.

## Change

When native card capture has no title, the queue reuses the title already fetched by the existing flyout channel request. That request already asks for videoDetails, including the title, but previously kept only the channel information. The cached title is accepted only for the requested video ID. Native card titles retain priority, and the existing original-title processing still runs.

This adds no request or wait. The request cache remains bounded; its original channel-info API and callers retain their behavior. The exact reported video (waZyE4Xl6bo) was checked against the existing public route and returns the expected full Starbound Extra title. Phone confirmation remains pending. If both the native card and existing request lack a title, the ID fallback remains. This fix does not add a separate fallback for missing views/age.

## Phone check

Start a normal video outside an account playlist, add the same Starbound video from Watch Later, then manually expand Queue. Check its title, channel and views/age. Additions should still show the existing success toast and should not automatically expand the panel.

## Validation

The full test task passed eight tests; five existing YouTube Music APK-dependent tests were skipped. The five queue/metadata tests passed. The existing channel route returned the exact reported video's ID, full title and channel. Code review verified a nonblocking cache read, matching response identity, preservation of native card titles and unchanged channel-info behavior. No new getter-only test or broad test framework was added.

Stock YouTube 21.20.405 application and SDK DEX/resource verification passed with the queue and eight related patches. An unselected run with nine related patches, including the other channel-helper caller (Open channel of live avatar), also passed. The final Android MPP/default-selection check passed. Phone verification remains pending. Queued-row playback, player navigation, ratings, removal, repeat and shuffle remain separate future milestones.

LICENSE and NOTICE are preserved. Only distribution manifest metadata differs from the build output. Earlier bundles remain available.

SHA-256: `9c04bd600011953855150b3219fb64018015f82a2a6f01444b3c494b9c9cea36`
