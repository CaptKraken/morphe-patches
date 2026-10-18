/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.fix.proto.fixProtoLibraryPatch
import app.morphe.patches.shared.misc.proto.hookElement
import app.morphe.patches.youtube.interaction.loop.sleepTimerHookPatch
import app.morphe.patches.youtube.layout.flyout.flyoutPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playertype.playerTypeHookPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.information.playerStatusHook
import app.morphe.patches.youtube.video.videoid.hookBackgroundPlayVideoId
import app.morphe.patches.youtube.video.videoid.hookVideoId
import app.morphe.patches.youtube.video.videoid.videoIdPatch
import app.morphe.util.setExtensionIsPatchIncluded

internal const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch;"
internal const val CONTROLLER_INTERFACE =
    $$"Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch$PlaylistController;"

@Suppress("unused")
val nativePlaylistQueuePatch = bytecodePatch(
    name = "Native playlist queue",
    description = "Adds a Play next action and a temporary queue with native playlist rows and rating actions. " +
            "Tap a queued row to play it or swipe it to remove it. " +
            "Drag its handle to reorder or use the queue menu to save it as a playlist. " +
            "Playback follows the queue and its repeat mode, synchronized with the player Loop switch. " +
            "YouTube 21.20.405 only.",
    default = true
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        flyoutPatch,
        videoInformationPatch,
        videoIdPatch,
        playerTypeHookPatch,
        sleepTimerHookPatch,
        fixProtoLibraryPatch
    )

    compatibleWith(Compatibility(
        name = requireNotNull(COMPATIBILITY_YOUTUBE.name),
        packageName = requireNotNull(COMPATIBILITY_YOUTUBE.packageName),
        apkFileType = COMPATIBILITY_YOUTUBE.apkFileType,
        signatures = COMPATIBILITY_YOUTUBE.signatures,
        appIconColor = COMPATIBILITY_YOUTUBE.appIconColor,
        targets = COMPATIBILITY_YOUTUBE.targets.filter { it.version == "21.20.405" }
    ))

    execute {
        if (packageMetadata.versionName != "21.20.405") {
            throw PatchException("Native playlist queue currently supports YouTube 21.20.405 only")
        }
        installPlaylistPanel(this)
        installRatingCapture(this)
        installPlaylistRemoval(this)
        playerStatusHook(EXTENSION_CLASS, "onPlayerStatus", includeController = true)
        hookElement("$EXTENSION_CLASS->onElementLoaded")
        hookVideoId("$EXTENSION_CLASS->onVideoChanged(Ljava/lang/String;)V")
        hookBackgroundPlayVideoId("$EXTENSION_CLASS->onVideoChanged(Ljava/lang/String;)V")
        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
