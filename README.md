# Native playlist queue — step 1, fix 1

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step1-fix1.mpp)

Source: [c583aceef](https://github.com/CaptKraken/morphe-patches/tree/c583aceef6261fb91064e80f92b1c3a4ef656ace)

Replace the previous local MPP source in Morphe Manager with this file. Patch an **untouched YouTube 21.20.405 APK**, explicitly selecting **Native playlist queue**. It is unchecked by default. Use patches from this source only to avoid mixing full Morphe bundles.

## Fix in this build

An ordinary video can have no accepted playlist model. This build observes completed native video transitions and creates the first display model through YouTube's own builder when needed. It reads the native active-video identity rather than an optional response field, preserves the receiver register at transition exits, and can restore the original null model. Readiness and publication failures now have separate messages.

This remains a fresh incremental rebuild from main; no code was copied from the old queue branch.

## Phone checks

1. Play a regular video A outside a playlist, then minimize it or scroll to recommendations.
2. Open another video B's feed three-dot menu and choose **Play next in native queue**.
3. The native portrait Queue panel should show A, B, while A continues without restarting or seeking.
4. Add C in the same way; expect A, C, B.
5. Close the player, open another regular video and repeat. The temporary display should reset.
6. Check that the action is absent from the gear menu, Shorts and account playlists, and that the existing Add to queue action still behaves independently.

This milestone includes insertion and native panel presentation only. Queued titles may be video IDs. Row playback, automatic advancement, player Next/Previous, editing, unique entries, shuffle/repeat, metadata enrichment, fullscreen access, ratings and saving are not implemented yet.

## Validation

The final source passed both focused production-state tests and stock-APK application with Android SDK DEX/resource verification alongside six related patches. Generated-bytecode inspection confirmed that callbacks use the preserved controller receiver and retain native return branch targets. String validation checked 90,670 strings. The final Android bundle contains classes.dex; ZIP integrity and all non-manifest payloads were checked. Phone confirmation remains pending.

Source license and NOTICE are preserved. Only distribution manifest metadata differs from the build output. The original step 1 file remains here for reproduction; use the fix 1 download above.

SHA-256: `532a5e9d29dffbfe60b5dcf99e1131a9f87dbe2e1f93a54e21996a4b65361eec`
