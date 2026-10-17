# Native playlist queue — step 1, fix 2

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step1-fix2.mpp)

Source: [0ed0cfd89](https://github.com/CaptKraken/morphe-patches/tree/0ed0cfd89221ea719b471a9ea54dbecea408de15)

Replace the previous local MPP source in Morphe Manager with this file. Patch an **untouched YouTube 21.20.405 APK**, explicitly selecting **Native playlist queue**. It is unchecked by default. Use patches from this source only to avoid mixing full Morphe bundles.

## Change in this build

A successful **Play next in native queue** now shows the existing localized **Added to queue** toast. It uses the same project helper as the error messages and runs only after the native panel accepts the addition. Background refreshes and unsuccessful additions do not trigger a success toast.

The previous fix allowing initialization without an existing playlist model is included and has been confirmed working by the user.

This remains a fresh incremental rebuild from main; no code was copied from the old queue branch.

## Phone checks

1. Play a regular video A outside a playlist, then minimize it or scroll to recommendations.
2. Open another video B's feed three-dot menu and choose **Play next in native queue**.
3. Confirm **Added to queue** appears. The native portrait Queue panel should show A, B, while A continues without restarting or seeking.
4. Add C in the same way; expect A, C, B.
5. Close the player, open another regular video and repeat. The temporary display should reset.
6. Check that the action is absent from the gear menu, Shorts and account playlists, and that the existing Add to queue action still behaves independently.

This milestone includes insertion and native panel presentation only. Queued titles may be video IDs. Row playback, automatic advancement, player Next/Previous, editing, unique entries, shuffle/repeat, metadata enrichment, fullscreen access, ratings and saving are not implemented yet.

## Validation

The Android build and string-resource validation passed. Existing tests ran with no failures; device-only skipped tests are not counted as validation. No new test or APK hook validation was added for this one-line toast call. Native fingerprints, injected bytecode hooks, playback handling and dependencies are unchanged from the preceding APK-verified build. Phone confirmation of the new success toast remains pending.

Source license and NOTICE are preserved. Only distribution manifest metadata differs from the build output. Earlier bundles remain here for reproduction; use the fix 2 download above.

SHA-256: `69c1c0a1845f0bcf94d983bdc0be82b66c504b1577efc498ce4499674a78f6b4`
