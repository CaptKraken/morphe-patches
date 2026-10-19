/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.SdkDexVerifier
import app.morphe.patcher.patch.Patch
import app.morphe.patches.youtube.interaction.loop.loopVideoPatch
import app.morphe.patches.youtube.interaction.savetowatchlater.saveToWatchLaterButtonPatch
import app.morphe.patches.youtube.layout.buttons.action.hideVideoActionButtonsPatch
import app.morphe.patches.youtube.layout.buttons.overlay.hidePlayerOverlayButtonsPatch
import app.morphe.patches.youtube.layout.dearrow.deArrowPatch
import app.morphe.patches.youtube.layout.flyout.addToQueuePatch
import app.morphe.patches.youtube.layout.player.fullscreen.exitFullscreenPatch
import app.morphe.patches.youtube.layout.player.fullscreen.openVideosFullscreenPatch
import app.morphe.patches.youtube.layout.livering.openChannelOfLiveAvatarPatch
import app.morphe.patches.youtube.layout.hide.relatedvideos.hideRelatedVideosPatch
import app.morphe.patches.youtube.layout.originaltitles.restoreOriginalTitlesPatch
import app.morphe.patches.youtube.layout.returnyoutubedislike.returnYouTubeDislikePatch
import app.morphe.patches.youtube.layout.playlistautoplay.disablePlaylistAutoplayPatch
import app.morphe.patches.youtube.misc.medianotification.mediaNotificationControlsPatchYouTube
import app.morphe.patches.youtube.misc.backgroundplayback.backgroundPlaybackPatch
import app.morphe.patches.youtube.misc.spoof.spoofVideoStreamsPatch
import app.morphe.patches.youtube.misc.fix.videoactionbar.restoreOldVideoActionBarPatch
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking

/** Applies the real patch and runs SDK DEX verification. Playback behavior still requires a phone. */
fun main(arguments: Array<String>) {
    require(arguments.size in 1..3) {
        "Usage: PlaylistQueueApkBuildKt <stock-apk> [standalone|combined|unselected] [unsigned-output]"
    }
    val input = File(arguments[0]).canonicalFile
    require(input.isFile) { "Stock APK does not exist: $input" }
    val mode = arguments.getOrElse(1) { "standalone" }
    require(mode in setOf("standalone", "combined", "unselected"))
    val sdk = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is required for SDK verification")
    val relatedPatches = setOf(
        addToQueuePatch, saveToWatchLaterButtonPatch, loopVideoPatch,
        disablePlaylistAutoplayPatch, hidePlayerOverlayButtonsPatch, hideVideoActionButtonsPatch,
        restoreOriginalTitlesPatch, deArrowPatch, openChannelOfLiveAvatarPatch, returnYouTubeDislikePatch,
        hideRelatedVideosPatch, exitFullscreenPatch, openVideosFullscreenPatch,
        mediaNotificationControlsPatchYouTube, backgroundPlaybackPatch,
        spoofVideoStreamsPatch, restoreOldVideoActionBarPatch
    )
    val selected = when (mode) {
        "standalone" -> setOf(nativePlaylistQueuePatch)
        "combined" -> relatedPatches + nativePlaylistQueuePatch
        else -> relatedPatches
    }
    fun requiresQueue(patch: Patch<*>, visited: MutableSet<Patch<*>> = mutableSetOf()): Boolean =
        patch == nativePlaylistQueuePatch || visited.add(patch) && patch.dependencies.any { requiresQueue(it, visited) }
    check(relatedPatches.none { requiresQueue(it) }) {
        "Unrelated patches must not select Native playlist queue as a dependency"
    }
    val workspace = createTempDirectory("playlist-queue-$mode").toFile()
    Patcher(PatcherConfig(input, workspace, verifier = SdkDexVerifier(File(sdk)))).use { patcher ->
        patcher += selected
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }
        val result = patcher.get()
        arguments.getOrNull(2)?.let { result.applyTo(File(it)) }
        println("$mode: APK patched and SDK DEX/resource verification completed: $workspace")
    }
}
