# native queue mix2 diagnostic build

[download the mpp](https://github.com/CaptKraken/morphe-patches/raw/refs/heads/build/native-queue-mix-diagnostics-mix2-fc9fe932f-dev11.mpp)

based on [fc9fe932f](https://github.com/CaptKraken/morphe-patches/commit/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4), including the feed-preview startup fix. patches version: **2.1.0-dev.11**. logs identify this build as `NativeQueueDiag mix2`.

## steps

1. repatch with this mpp. include **Add to queue** and **Enable debugging**, alongside your usual patches.
2. select **Native queue**.
3. open **Morphe -> Miscellaneous -> Debugging** and enable **Enable debug logging**.
4. reproduce the mix issue once.
5. open **Export debug logs**, search `NativeQueueDiag`, and wait for the list to filter.
6. tap **Copy** and paste the text into the [pr comment](https://github.com/MorpheApp/morphe-patches/pull/4404), or use **Export to file** and attach it.

**export before force-stopping or restarting youtube.** no protobuf or stack-trace logging is needed.

## checks and source

unit checks passed (59 patch tests and 378 youtube tests; 5 optional apk tests skipped). actual apk patching with strict fingerprint and sdk validation passed on **21.30.216** and **21.44.528**. reporter phone confirmation is pending.

source: the [base commit](https://github.com/CaptKraken/morphe-patches/tree/fc9fe932ffe4aad9b05d5426d3324e8a50f3d3f4) plus [diagnostic source archive](diagnostic-source.zip). to reproduce, check out that commit, extract `diagnostics.patch` from the archive, apply it, and run `./gradlew :patches:buildAndroid` with the repository's build prerequisites.

sha256: `2eb4d56ff74d902909e2cb156ff34d4c220ed0a3dd8313b29a0422aa59a71f55`

see [LICENSE](LICENSE) and [NOTICE](NOTICE).
