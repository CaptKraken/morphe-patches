# Native Queue — preview 28

Independent experimental fork by CaptKraken, based on [Morphe Patches](https://github.com/MorpheApp/morphe-patches). This is not an official upstream release. Original attribution, [LICENSE](LICENSE) and [NOTICE](NOTICE) are retained, including upstream notices embedded in the bundle.

Download [native-queue-21.20.405-preview28.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/native-queue-21.20.405-preview28.mpp).

This preview addresses a reproduced false error during automatic queue refreshes. Player metadata can arrive before replacement native views bind. The model retains that metadata, but a refresh previously displayed “Could not update queue” solely because its weak navigator/controller reference was unavailable. Automatic metadata, account-state, and playback callbacks now defer that unavailable-UI case. The next native panel bind renders the retained model. Explicit queue actions still report unavailable UI, and actual refresh exceptions still log/show errors.

The captured log contains Next followed by a new video loading, then the generic toast without a queue exception, and subsequent Next actions that load videos. This is consistent with the reproduced background-refresh failure. It does not by itself prove which non-exception branch emitted that particular toast. Two low-volume info messages now distinguish explicit unavailable-UI requests from native navigation returning no playback descriptor. They occur on failure paths; no per-frame or broad navigation tracing was added.

A before/after JVM regression through the actual metadata handler reproduces the old generic toast with no exception. The corrected handler retains metadata silently while native UI references are absent; the next panel bind encodes it. Explicit Show Queue with unavailable UI, real background refresh exceptions, and native null-commit errors still report failures. Successful navigation stays quiet. Native/UI/scheduler access uses test doubles; actual extension handlers and queue models execute unchanged. The preceding first/last-item response-ownership regression also passes.

Disable the preceding preview source, import this file in Morphe Manager, and select **Native Queue** alone as the source for YouTube. Patch an untouched YouTube **21.20.405** APK. Manager may retain saved selections; ensure Native temporary queue (experimental) is selected.

Phone checks:

- Use player Next/Previous repeatedly while titles/channel/statistics are arriving. Confirm each requested video plays, metadata appears, and transitions do not produce a false queue-update toast.
- Check portrait/fullscreen and transitions through PiP. Confirm queue selection follows playback and native Next/Previous still respect queue boundaries.
- Retest Shuffle/Repeat on and off, including while the last item is still playing.
- If a toast remains, capture the fresh log: real failures now identify either unavailable navigator/controller references, a missing native playback descriptor, or a caught exception.

Validation: all **77 queue tests passed**, the before/after background-refresh regression and earlier ownership regression passed, resource formatting passed for **90,681 strings**, and standalone, seven-related-patch combined, and Native Queue-unselected APK validation passed with Android SDK DEX/cross-DEX verification and resource compilation. The Android `.mpp` bundle was built. No Android device or emulator is attached.

Built from source commit [`ace040b394576fed23469bd1f579a85f46e2af82`](https://github.com/CaptKraken/morphe-patches/commit/ace040b394576fed23469bd1f579a85f46e2af82) on `feature/youtube-native-queue`. Preview branding changes only artifact metadata; source metadata and notices remain unchanged.

Reproduce with the configured JDK and Android SDK:

```sh
./gradlew :patches:test :patches:checkStringResources :patches:validateNativeQueue :patches:validateNativeQueueCompatibility :patches:validateWithoutNativeQueue :patches:buildAndroid -PqueueApk=/absolute/path/youtube.apk
```

SHA256: `d3538db193d19242d00fd824edccd8de1436637a8091f1d5d73cd6d361f13ca3`

Older preview files are superseded. This branch distributes experimental builds; source development is on `feature/youtube-native-queue`.
