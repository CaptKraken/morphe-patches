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
import app.morphe.patcher.dex.DexVerifier
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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.analysis.ClassPath
import com.android.tools.smali.dexlib2.analysis.ClassProvider
import com.android.tools.smali.dexlib2.analysis.DexClassProvider
import com.android.tools.smali.dexlib2.analysis.MethodAnalyzer
import com.android.tools.smali.dexlib2.analysis.RegisterType
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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
    val sdkVerifier = SdkDexVerifier(File(sdk))
    val verifier = object : DexVerifier by sdkVerifier {
        override fun verifyDexDirectory(dexDir: File) {
            sdkVerifier.verifyDexDirectory(dexDir)
            verifyNativePanelAssignment(dexDir, mode != "unselected")
        }
    }
    Patcher(PatcherConfig(input, workspace, verifier = verifier)).use { patcher ->
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

/** SDK structural checks do not catch a base protobuf value stored in a concrete panel field. */
private fun verifyNativePanelAssignment(directory: File, selected: Boolean) {
    val dexFiles = directory.listFiles()!!.filter { it.extension == "dex" }.map { file ->
        file.inputStream().buffered().use { DexBackedDexFile.fromInputStream(Opcodes.getDefault(), it) }
    }
    val playbackInterface = EXTENSION_CLASS.removeSuffix(";") + "\$NativePlayback;"
    val owners = dexFiles.flatMap { it.classes }.filter { playbackInterface in it.interfaces }
    check(owners.size == if (selected) 1 else 0) { "Unexpected native playback bridge inclusion" }
    if (!selected) return
    val providers: List<ClassProvider> = dexFiles.map(::DexClassProvider)
    val classPath = ClassPath(providers)
    val bridge = owners.single().methods.single { it.name == "patch_localPanel" }
    val analyzer = MethodAnalyzer(classPath, bridge, null, false)
    analyzer.analysisException?.let { throw it }
    for (instruction in analyzer.analyzedInstructions) {
        val store = instruction.instruction
        if (store.opcode != Opcode.IPUT_OBJECT) continue
        val field = (store as ReferenceInstruction).reference as FieldReference
        val value = instruction.getPreInstructionRegisterType((store as TwoRegisterInstruction).registerA)
        val valueType = value.type
        check(value.category == RegisterType.NULL || value.category == RegisterType.REFERENCE &&
                valueType != null && classPath.getClass(field.type).getCommonSuperclass(valueType).type == field.type) {
            "Native playback bridge stores $value in incompatible field $field"
        }
    }
    println("Native playback panel field assignments: compatible register types")
}
