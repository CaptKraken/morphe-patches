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
import app.morphe.patches.youtube.layout.flyout.flyoutPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playertype.playerTypeHookPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.hookVideoIntentContext
import app.morphe.patches.youtube.shared.openVideoIntentPatch
import app.morphe.patches.youtube.video.information.videoInformationPatch
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
    description = "Adds a temporary video queue using YouTube's playlist interface and playback controls. " +
            "Supports Play next/last, reordering, shuffle, repeat, fullscreen playlist controls, " +
            "saving the queue to a playlist, and Undo for removal or clearing. " +
            "Experimental. YouTube 21.20.405 only.",
    default = true
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        flyoutPatch,
        videoInformationPatch,
        videoIdPatch,
        playerTypeHookPatch,
        fixProtoLibraryPatch,
        openVideoIntentPatch
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
        hookVideoIntentContext(EXTENSION_CLASS, "prepareQueueStartContext")
        installPlaylistPanel(this)
        installRatingCapture(this)
        installPlaylistRemoval(this)
        installFullscreenPlaylist(this)
        hookElement("$EXTENSION_CLASS->onElementLoaded")
        hookVideoId("$EXTENSION_CLASS->onVideoChanged(Ljava/lang/String;)V")
        hookBackgroundPlayVideoId("$EXTENSION_CLASS->onVideoChanged(Ljava/lang/String;)V")
        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
