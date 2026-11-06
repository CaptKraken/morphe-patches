# native queue mix3 diagnostic build

[download the mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/native-queue-mix-diagnostics-mix3-fc9fe932f-dev11.mpp)

based on [fc9fe932f](https://github.com/CaptKraken/morphe-patches/commit/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4), including the feed-preview startup fix. patches version: **2.1.0-dev.11**. logs identify this build as `NativeQueueDiag mix3`.

this is a diagnostic build, not a fix for the wrapped mix command. it captures both command branches, the card's native mix flags and link, the branch youtube actually chooses, and the context passed through taps and command routing. ids are fingerprinted so we can compare them without printing their values. logging runs on these events, not a timer.

## setup

1. repatch with this mpp. include **Add to queue** and **Enable debugging**, alongside your usual patches.
2. select **Native queue**.
3. open **Morphe -> Miscellaneous -> Debugging** and enable **Enable debug logging**. protobuf and stack-trace logging are not needed.
4. start a fresh app session, then reproduce the following in order using the same failing mix.

## capture in one session

1. with no playback or queue, use the mix's three-dot menu -> **Play next in queue**. note whether it starts a queue. close the player and clear any queue before the next step.
2. tap that mix card normally with no playback. let it open, then close the player and clear the queue. this records youtube's native branch choice without an active queue.
3. start a native queue from an ordinary video. try adding the same mix through its three-dot menu -> **Play next in queue**, then **Play last in queue** if available. note which actions work.
4. with that queue still active, tap the mix card normally. note whether the **Add to queue or watch Mix?** dialog appears, and try **Add to queue** if it does.

if convenient, also try one working mix or small ordinary playlist through the menu as a comparison. no need to test every screen or playlist type.

## send back

open **Export debug logs**, search `NativeQueueDiag`, and wait for the list to filter. use **Export to file** and attach the file to the [pr comment](https://github.com/MorpheApp/morphe-patches/pull/4404), or use **Copy** if the filtered output fits. include a short note saying which steps failed.

**export before force-stopping or restarting youtube.** use the in-app export rather than a logcat screenshot, so long command trees are preserved.

## checks and source

unit checks passed: 59 patch tests and 382 youtube tests; 5 optional apk tests skipped. actual apk patching with strict fingerprint and sdk validation passed on **21.30.216** and **21.44.528**, including the feed-card patches and related playback/title patches. reporter phone confirmation is pending.

source: the [base commit](https://github.com/CaptKraken/morphe-patches/tree/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4) plus [mix3 diagnostic source archive](diagnostic-source-mix3.zip). to reproduce, check out that commit, extract `diagnostics.patch`, apply it, and run `./gradlew :patches:buildAndroid` with the repository's build prerequisites.

sha256: `cd70d3cf326838c145822d003c4dfaf6c8835ee9127a6530507d246afd7bfaab`

## older build

the [mix2 mpp](native-queue-mix-diagnostics-mix2-fc9fe932f-dev11.mpp) and its [source archive](diagnostic-source.zip) remain available for existing links. use mix3 for this capture.

see [LICENSE](LICENSE) and [NOTICE](NOTICE).
