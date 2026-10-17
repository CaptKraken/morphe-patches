# Native playlist queue — step 2, fix 1

[Download the Android patch bundle](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-step2-fix1.mpp)

Source: [6187044ec](https://github.com/CaptKraken/morphe-patches/tree/6187044ecb695842b2b8a7b243ad9db373227620)

Replace the previous local MPP source in Morphe Manager. Patch an **untouched YouTube 21.20.405 APK**, using patches from this source only. **Native playlist queue remains selected by default**.

## Fix

Long channel names, such as **Starbound Extra and Starbound HFY**, can now be identified from the native card's avatar label or accessibility description when the flyout channel name is unavailable. The exact matching channel text goes into YouTube's existing single-line channel field, while views/age stay in the separate metadata field. Native layout, ellipsis, fonts and dimensions are unchanged. Channel names containing separators are preserved, and an unrelated label does not authorize splitting the text.

This uses data already loaded with the card and makes no extra queue-metadata request. Existing success toast, non-expanding additions, A/C/B insertion order and title-patch hooks remain unchanged. Missing or unrecognized metadata retains its previous fallback.

Further metadata/UI polish is deferred. The next functional milestone is tapping a queued video to play it; that is not included in this regression bundle.

## Phone checks

1. Start a regular video outside an account playlist, then add the Starbound video to the native queue.
2. Manually expand Queue. Expect its channel to truncate with an ellipsis on one line, with views/age underneath, like YouTube's regular playlist.
3. Check a short channel name and one containing a separator. Adding another video should preserve insertion order and should not expand the panel automatically.

## Validation

Five focused queue/metadata tests passed, including the Starbound regression with unavailable flyout data, missing avatars, localized labels and separators inside channel names. The full task passed eight tests; five existing Jam tests were skipped because no YouTube Music APK was supplied. The production parser separated channels in all 20 captured native cards without flyout data.

Stock 21.20.405 patch application alongside eight related patches and Android SDK DEX/resource verification passed. The final Android MPP build/default-selection check passed. Phone verification of the reported Starbound row remains pending; the regression fixture reconstructs its metadata from the screenshot.

LICENSE and NOTICE are preserved. Only distribution manifest metadata differs from the build output. Earlier bundles remain available for reproduction.

SHA-256: `623827bcf6f69c13f2dea44c213463e6ed3999f6ed76dc699d76c5e49aac9a9a`
