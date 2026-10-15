# Native YouTube queue preview 2

Download [youtube-native-queue-21.20.405-preview2.mpp](https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-queue-preview/youtube-native-queue-21.20.405-preview2.mpp).

This replaces preview 1 after the first device report of playback stopping during enqueue and a subsequent resume crash. The native store switch explicitly stops/reopens playback; preview 2 suppresses that dispatcher during local edits, populates the store before attaching it, and retains the playing endpoint. It also resolves the initial video's title.

Disable the preceding local preview source, import this file in Morphe Manager, and use **Morphe Patches (queue preview 2)** alone for YouTube. Select **Native temporary queue (experimental)** alongside the other desired patches from that same source. Patch the untouched YouTube 21.20.405 APK.

Built from source commit `88bf1778e3215cb622a2b12379032b919f1b5abb` on `feature/youtube-native-queue`.
Ten queue tests and real-APK DEX/resource verification passed. The reported crash stack was not present in the exported debug logs; uninterrupted playback and pause/resume still require a device retest.

Instructions: https://github.com/CaptKraken/morphe-patches/blob/88bf1778e3215cb622a2b12379032b919f1b5abb/docs/youtube-native-queue.md

SHA256: `bc8b6c84a71bdc34773bb5078766adaf7391ebc65a55f794fa7028db4ee8ccfe`

The older `youtube-native-queue-21.20.405.mpp` file is superseded. This branch only distributes experimental builds; source development is on `feature/youtube-native-queue`.
