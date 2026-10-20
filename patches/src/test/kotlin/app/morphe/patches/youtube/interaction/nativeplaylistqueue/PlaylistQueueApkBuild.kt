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
import app.morphe.patches.youtube.interaction.downloads.downloadsPatch
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
import app.morphe.patches.youtube.layout.sharesheet.openSystemShareSheetPatch
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
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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
        addToQueuePatch, saveToWatchLaterButtonPatch, downloadsPatch, openSystemShareSheetPatch(), loopVideoPatch,
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
            verifyNativeRowMenuPaths(dexDir, mode != "unselected")
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

/** Check the emitted native video bridge and queue-only menu dispatch paths. */
private fun verifyNativeRowMenuPaths(directory: File, selected: Boolean) {
    val dexFiles = directory.listFiles()!!.filter { it.extension == "dex" }.map { file ->
        file.inputStream().buffered().use { DexBackedDexFile.fromInputStream(Opcodes.getDefault(), it) }
    }
    val classes = dexFiles.flatMap { it.classes }
    val queueRow = "${EXTENSION_CLASS.removeSuffix(";")}\$QueueRow;"
    val videoIdInterface = "Lapp/morphe/extension/youtube/patches/utils/FlyoutUtils\$FlyoutMenuVideoIdInterface;"
    val extensionOwner = EXTENSION_CLASS.removePrefix("L").removeSuffix(";")
    val rowTypes = classes.filter { queueRow in it.interfaces }
    val menuOpenCalls = classes.flatMap { owner -> owner.methods.mapNotNull { method ->
        method.implementation?.instructions?.toList()?.let { instructions ->
            instructions.indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.toString()?.startsWith(
                    "L$extensionOwner;->onRowMenuOpened(Ljava/util/Map;)V") == true
            }.takeIf { it >= 0 }?.let { Triple(owner, method, instructions) }
        }
    } }
    val menuDispatches = classes.flatMap { owner -> owner.methods.mapNotNull { method ->
        method.implementation?.instructions?.toList()?.let { instructions ->
            instructions.indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.toString()?.startsWith(
                    "L$extensionOwner;->onQueueMenuCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)") == true
            }.takeIf { index -> index >= 0 && instructions.getOrNull(index + 2)?.opcode == Opcode.IF_NEZ &&
                    instructions.getOrNull(index + 3)?.opcode == Opcode.GOTO_16 }
                ?.let { Triple(owner, method, instructions) }
        }
    } }
    if (!selected) {
        check(rowTypes.isEmpty() && menuOpenCalls.isEmpty() && menuDispatches.isEmpty()) {
            "Native playlist row menu bridge leaked into an unselected build"
        }
        println("Native playlist row menu bridge absent when unselected")
        return
    }

    val videoType = rowTypes.singleOrNull()
        ?: error("Expected one queue row type in emitted DEX, found ${rowTypes.size}")
    check(videoIdInterface in videoType.interfaces) {
        "Native queue row does not implement the shared FlyoutUtils video-ID interface"
    }
    val videoIdGetter = videoType.methods.single { it.name == "patch_getVideoId" }
    val getterInstructions = videoIdGetter.implementation!!.instructions.toList()
    val getterField = getterInstructions.single { it.opcode == Opcode.IGET_OBJECT }
        .let { (it as ReferenceInstruction).reference as FieldReference }
    check(getterField.definingClass == videoType.type && getterField.type == "Ljava/lang/String;" &&
            getterInstructions.last().opcode == Opcode.RETURN_OBJECT) {
        "Queue row's shared video-ID getter does not return its native String field"
    }
    check(classes.flatMap { it.methods }.any { method ->
        method.name != "patch_getVideoId" && method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.IGET_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference == getterField
        } == true
    }) { "Binder-derived video-ID field is not read by native code in emitted DEX" }

    val extensionClass = classes.single { it.type == EXTENSION_CLASS }
    val rowMenuHelper = extensionClass.methods.single { it.name == "onRowMenuOpened" }
    val rowMenuInstructions = rowMenuHelper.implementation!!.instructions.toList()
    val instanceOfTypes = rowMenuInstructions.filter { it.opcode == Opcode.INSTANCE_OF }
        .mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }.toSet()
    check(queueRow in instanceOfTypes && videoIdInterface in instanceOfTypes) {
        "Row menu helper does not guard both queue ownership and the native row's shared video-ID interface"
    }
    check(rowMenuInstructions.any { instruction ->
        instruction.opcode == Opcode.INVOKE_STATIC &&
                (instruction as? ReferenceInstruction)?.reference?.toString()?.startsWith(
                    "Lapp/morphe/extension/youtube/patches/utils/FlyoutUtils;->extractFlyoutIdFromMap(Ljava/util/Map;)V"
                ) == true
    }) { "Shared row menu video context extractor is not reached by the emitted helper" }

    val menuOpen = menuOpenCalls.singleOrNull()
        ?: error("Expected one row menu context extraction call, found ${menuOpenCalls.size}")
    val (menuOwner, menuMethod, openInstructions) = menuOpen
    val openCallIndex = openInstructions.indexOfFirst { instruction ->
        (instruction as? ReferenceInstruction)?.reference?.toString()?.startsWith(
            "L$extensionOwner;->onRowMenuOpened(Ljava/util/Map;)V") == true
    }
    val openCall = openInstructions[openCallIndex] as FiveRegisterInstruction
    val mapResult = openInstructions.getOrNull(openCallIndex - 1) as? com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
    val mapFactory = openInstructions.getOrNull(openCallIndex - 2) as? ReferenceInstruction
    val factoryReference = mapFactory?.reference as? MethodReference
    check(mapResult != null && openCallIndex >= 2 &&
            openInstructions[openCallIndex - 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            mapResult.registerA == openCall.registerC && factoryReference != null &&
            factoryReference.returnType.startsWith("L") &&
            factoryReference.parameterTypes.map { it.toString() } == List(4) { "Ljava/lang/Object;" }) {
        "Row menu context extraction is not immediately after its native four-object map factory"
    }
    println("Native row menu context bridge: ${menuOwner.type}->${menuMethod.name}, shared interface and native String getter verified")

    val dispatch = menuDispatches.singleOrNull()
        ?: error("Expected one consumed native menu removal path, found ${menuDispatches.size}")
    val dispatchInstructions = dispatch.third
    val removeIndex = dispatchInstructions.indexOfFirst { instruction ->
        (instruction as? ReferenceInstruction)?.reference?.toString()?.startsWith(
            "L$extensionOwner;->onQueueMenuCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)") == true
    }
    check(removeIndex >= 0 && dispatchInstructions[removeIndex + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            dispatchInstructions[removeIndex + 2].opcode == Opcode.IF_NEZ &&
            dispatchInstructions[removeIndex + 3].opcode == Opcode.GOTO_16) {
        "Native removal result does not branch between the original callback and dismissal"
    }
    val branchAddresses = HashMap<Int, Int>()
    var address = 0
    dispatchInstructions.forEachIndexed { index, instruction ->
        branchAddresses[address] = index
        address += instruction.codeUnits
    }
    fun branchTarget(index: Int): Int {
        val branch = dispatchInstructions[index] as OffsetInstruction
        val instructionAddress = branchAddresses.entries.single { it.value == index }.key
        return branchAddresses[instructionAddress + branch.codeOffset]
            ?: error("Native row menu branch target is not an instruction boundary")
    }
    val callbackIndex = branchTarget(removeIndex + 2)
    val dismissalIndex = branchTarget(removeIndex + 3)
    check(dispatchInstructions[callbackIndex].opcode == Opcode.CHECK_CAST &&
            dispatchInstructions.getOrNull(callbackIndex + 1)?.opcode == Opcode.INVOKE_INTERFACE &&
            dispatchInstructions[dismissalIndex].opcode == Opcode.INVOKE_VIRTUAL &&
            (dispatchInstructions[dismissalIndex] as ReferenceInstruction).reference
                .let { reference -> reference is MethodReference && reference.returnType == "V" &&
                        reference.parameterTypes.isEmpty() } &&
            dispatchInstructions.getOrNull(dismissalIndex + 1)?.opcode == Opcode.RETURN_VOID) {
        "Native removal branch does not preserve the original callback and terminal dismissal paths"
    }
    println("Native row menu dispatch preserves original callback and dismissal branches")
}

/** SDK structural checks do not catch a base protobuf value stored in a concrete panel field. */
private fun verifyNativePanelAssignment(directory: File, selected: Boolean) {
    val dexFiles = directory.listFiles()!!.filter { it.extension == "dex" }.map { file ->
        file.inputStream().buffered().use { DexBackedDexFile.fromInputStream(Opcodes.getDefault(), it) }
    }
    val playbackInterface = EXTENSION_CLASS.removeSuffix(";") + "\$NativePlayback;"
    val owners = dexFiles.flatMap { it.classes }.filter { playbackInterface in it.interfaces }
    check(owners.size == if (selected) 1 else 0) { "Unexpected native playback bridge inclusion" }
    val panelInterface = EXTENSION_CLASS.removeSuffix(";") + "\$QueuePanel;"
    val panels = dexFiles.flatMap { it.classes }.filter { panelInterface in it.interfaces }
    check(panels.size == if (selected) 1 else 0) { "Queue header ownership must be included only with this patch" }
    if (!selected) return
    val panelFlag = panels.single().methods.single { it.name == "patch_isQueueOwned" }
        .implementation!!.instructions.single { it.opcode == Opcode.IGET_BOOLEAN }
        .let { (it as ReferenceInstruction).reference as FieldReference }
    check(dexFiles.flatMap { it.classes }.flatMap { it.methods }.single { it.name == "patch_markQueueRows" && it.implementation != null }
        .implementation!!.instructions.any {
            it.opcode == Opcode.IPUT_BOOLEAN && (it as ReferenceInstruction).reference == panelFlag
        }) { "Publishing local rows must also mark the exact native header model" }
    verifyNativeShuffle(dexFiles.flatMap { it.classes })
    val clear = owners.single().methods.single { it.name == "patch_clearQueue" }
        .implementation!!.instructions.toList()
    val wrapper = owners.single()
    val autoplayType = wrapper.fields.single { it.name == "patch_lastAutoplay" }.type
    val clearCalls = clear.mapIndexedNotNull { index, instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { index to it }
    }
    val initialize = clearCalls.first { (_, reference) ->
        reference.definingClass == wrapper.type && reference.parameterTypes == listOf(autoplayType)
    }.first
    val releaseFlag = clear.indexOfFirst { instruction ->
        instruction.opcode == Opcode.IPUT_BOOLEAN &&
                ((instruction as ReferenceInstruction).reference as FieldReference).name == "patch_localQueue"
    }
    val responseType = wrapper.methods.single { it.name == "patch_localPanel" }.returnType
    val hydrate = clearCalls.single { (_, reference) -> reference.parameterTypes == listOf(responseType) }.first
    val dispose = clearCalls.single { (_, reference) ->
        reference.name == "patch_disposeWithoutPlayback"
    }.first
    check(releaseFlag >= 0 && releaseFlag < initialize && initialize < hydrate && hydrate < dispose) {
        "Clear queue must release local interception, hydrate ordinary navigation, then dispose the old queue"
    }
    val guardedDispose = dexFiles.flatMap { it.classes }.flatMap { it.methods }
        .single { it.name == "patch_disposeWithoutPlayback" && it.implementation != null }
    val guardedCalls = guardedDispose.implementation!!.instructions.mapNotNull {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)
    }.toList()
    val beginChange = dexFiles.flatMap { it.classes }.flatMap { it.methods }
        .single { it.toString() == guardedCalls.first().toString() }
    val acquireChange = beginChange.implementation!!.instructions.mapNotNull {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)
    }.single { it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;" }
    check(guardedCalls.size == 4 && guardedCalls[2] == guardedCalls[3] &&
            guardedCalls[2].definingClass == acquireChange.definingClass &&
            guardedCalls[2].parameterTypes == listOf("Ljava/lang/Object;") &&
            guardedDispose.implementation!!.tryBlocks.single().exceptionHandlers.single().exceptionType == null) {
        "Native disposal must acquire the existing queue guard and release it on success and failure"
    }
    println("Native clear queue restores ordinary navigation and guards selected-item callbacks during disposal")
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

/** Shuffle links refer to row indexes: replacing rows must rebuild them without losing the mode. */
private fun verifyNativeShuffle(classes: List<ClassDef>) {
    val mutableQueue = classes.single { type -> type.methods.any { it.name == "patch_clearShuffleOrder" } }
    val clear = mutableQueue.methods.single { it.name == "patch_clearShuffleOrder" }
        .implementation!!.instructions.toList()
    val disableIndex = clear.indexOfFirst { it.opcode == Opcode.INVOKE_VIRTUAL }
    val disable = (clear[disableIndex] as ReferenceInstruction).reference as MethodReference
    val nativeSetter = mutableQueue.methods.single { it.toString() == disable.toString() }
    check(disable.parameterTypes == listOf("Z") && disable.returnType == "V" &&
            (clear[disableIndex - 1] as NarrowLiteralInstruction).narrowLiteral == 0) {
        "Shuffle cache reset must disable the original mutable shuffler first"
    }
    fun linkFields(methodInstructions: Iterable<Instruction>) =
        methodInstructions.filter { it.opcode == Opcode.IPUT_OBJECT }.mapNotNull {
            ((it as ReferenceInstruction).reference as FieldReference).takeIf { field -> field.type == "[I" }
        }.toSet()
    val resetLinks = linkFields(clear)
    check(resetLinks.size == 2 && resetLinks == linkFields(nativeSetter.implementation!!.instructions) &&
            clear.indexOfFirst { it.opcode == Opcode.IPUT_OBJECT } > disableIndex) {
        "Shuffle reset must invalidate both original index maps after disabling their use"
    }
    val playback = classes.single { type -> type.methods.any {
        it.name == "patch_attachQueue" && it.implementation != null
    } }
    val attach = playback.methods.single { it.name == "patch_attachQueue" }.implementation!!.instructions.toList()
    val responseType = playback.methods.single { it.name == "patch_localPanel" }.returnType
    fun reference(index: Int) = (attach[index] as? ReferenceInstruction)?.reference as? MethodReference
    val clearIndex = attach.indices.single { reference(it)?.name == "patch_clearShuffleOrder" }
    val acceptIndex = attach.indices.single { index -> reference(index)?.let {
        it.returnType == "V" && it.parameterTypes == listOf(responseType)
    } == true }
    val restoreIndex = attach.indices.single { index -> reference(index)?.let {
        it.name == disable.name && it.parameterTypes == listOf("Z") && it.returnType == "V" &&
                attach[index].opcode == Opcode.INVOKE_INTERFACE
    } == true }
    val enabledField = nativeSetter.implementation!!.instructions.single { it.opcode == Opcode.IPUT_BOOLEAN }
        .let { ((it as ReferenceInstruction).reference as FieldReference) }
    val getterName = mutableQueue.methods.single { method ->
        method.parameterTypes.isEmpty() && method.returnType == "Z" &&
                method.implementation?.instructions?.any {
                    it.opcode == Opcode.IGET_BOOLEAN && (it as ReferenceInstruction).reference == enabledField
                } == true
    }.name
    val captureIndex = attach.indices.single { reference(it)?.name == getterName && reference(it)?.returnType == "Z" }
    check(captureIndex < clearIndex && clearIndex < acceptIndex && acceptIndex < restoreIndex &&
            (attach[captureIndex + 1] as OneRegisterInstruction).registerA ==
                    (attach[restoreIndex] as FiveRegisterInstruction).registerD) {
        "Native shuffle mode must be captured before reset and restored after accepting replacement rows"
    }
    check(playback.methods.single { it.name == "patch_updateSnapshot" }.implementation!!.instructions.none {
        (it as? ReferenceInstruction)?.reference?.toString()?.contains("patch_clearShuffleOrder") == true
    }) { "Metadata-only publication must not reset shuffle order" }
    println("Native shuffle: original index maps reset safely, mode restored after row replacement, metadata snapshot preserved")
}
