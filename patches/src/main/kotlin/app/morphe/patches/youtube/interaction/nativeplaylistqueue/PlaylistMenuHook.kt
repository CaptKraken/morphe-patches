/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Observe the shared native header/row service-menu callback. The callback already validates and logs
 * the command before this point; local queue actions then call the same sheet dismissal used
 * by the native path. Unowned and other endpoints continue through the original call.
 */
internal fun installPlaylistMenuDispatch(context: BytecodePatchContext, videoType: String) = with(context) {
    val rowOpen = playlistRowMenuOpenFingerprint(videoType).matchSingle()
    fun <T> unique(name: String, matches: List<T>): T {
        check(matches.size == 1) { "Native playlist queue: expected one $name, found ${matches.size}: $matches" }
        return matches.single()
    }
    val showMenu = unique("row menu factory", rowOpen.originalMethod.implementation!!.instructions.mapNotNull {
        it.getReference<MethodReference>()
    }.filter { reference ->
        reference.returnType.toString() == "V" && "Ljava/util/Map;" in reference.parameterTypes.map { it.toString() } &&
                classDefByOrNull(reference.definingClass.toString())?.methods?.any { method ->
                    method.name == reference.name.toString() &&
                            method.parameterTypes == reference.parameterTypes.map { it.toString() } &&
                            method.returnType == reference.returnType.toString()
                } == true
    })
    val callbackType = unique("menu endpoint callback interface", showMenu.parameterTypes.map { it.toString() }.filter { type ->
        classDefByOrNull(type)?.methods?.any { method ->
            method.returnType == "V" && method.parameterTypes.size == 2 &&
                    method.parameterTypes[1] == "Ljava/util/Map;"
        } == true
    })
    val callbackMethod = unique("menu endpoint callback method", classDefBy(callbackType).methods.filter {
        it.returnType == "V" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] != "Ljava/util/List;" &&
                it.parameterTypes[1] == "Ljava/util/Map;"
    })
    val callbackSignature = callbackMethod.parameterTypes.map { it.toString() }

    // The menu factory returns YouTube's native sheet. Find it through the factory's
    // own referenced types and the callback interface carried into that sheet.
    val sheetTypes = classDefBy(showMenu.definingClass.toString()).methods.flatMap { method ->
        method.implementation?.instructions?.mapNotNull { it.getReference<MethodReference>() } ?: emptyList()
    }.map { it.returnType.toString() }.filter { type ->
        type.startsWith("L") && classDefByOrNull(type)?.methods?.any { method ->
            method.implementation?.instructions?.any { instruction ->
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass.toString() == callbackType &&
                            reference.parameterTypes.map { it.toString() } == callbackSignature
                } == true
            } == true
        } == true
    }.distinct()
    check(sheetTypes.size == 1) {
        when {
            sheetTypes.isEmpty() -> "Native playlist queue: native row menu sheet is missing"
            else -> "Native playlist queue: native row menu sheet is ambiguous: $sheetTypes"
        }
    }
    val sheetType = sheetTypes.single()
    val dispatchMethod = playlistRowMenuDispatchFingerprint(sheetType, callbackType, callbackSignature)
        .matchSingle().originalMethod
    val mutableDispatchMethod = mutableClassDefBy(sheetType).methods.single {
        it.name == dispatchMethod.name && it.parameterTypes == dispatchMethod.parameterTypes &&
                it.returnType == dispatchMethod.returnType
    }
    val instructions = dispatchMethod.implementation!!.instructions.toList()
    val dispatch = instructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.definingClass.toString() != callbackType ||
                reference.parameterTypes.map { it.toString() } != callbackSignature) {
            return@mapIndexedNotNull null
        }
        index to (instruction as FiveRegisterInstruction)
    }.let { unique("native callback invocation", it) }
    val dismissalIndex = dispatch.first + 1
    val dismissalInstruction = instructions.getOrNull(dismissalIndex)
        ?: error("Native playlist queue: native menu dismissal immediately after callback is missing")
    val dismissal = dismissalInstruction.getReference<MethodReference>()
        ?: error("Native playlist queue: immediate native menu dismissal is not a method call")
    var dismissalHierarchyType: String? = sheetType
    while (dismissalHierarchyType != null && dismissalHierarchyType != dismissal.definingClass.toString()) {
        dismissalHierarchyType = classDefByOrNull(dismissalHierarchyType)?.superclass?.toString()
    }
    check(dismissalHierarchyType != null) {
        "Native playlist queue: dismissal ${dismissal.definingClass} is outside native sheet hierarchy $sheetType"
    }
    check(dismissal.returnType.toString() == "V" && dismissal.parameterTypes.isEmpty() &&
            instructions.getOrNull(dispatch.first + 2)?.opcode == Opcode.RETURN_VOID) {
        "Native playlist queue: native menu dismissal is not the callback's terminal no-argument close"
    }

    val endpointType = callbackSignature.first()
    installQueueSavePicker(this, endpointType)
    val registers = dispatch.second
    mutableDispatchMethod.addInstructionsAtControlFlowLabel(dispatch.first, """
        invoke-static {v${registers.registerD}, v${registers.registerE}}, $EXTENSION_CLASS->onQueueMenuCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)Lcom/google/protobuf/MessageLite;
        move-result-object v${registers.registerD}
        if-nez v${registers.registerD}, :native_menu_dispatch
        goto :native_menu_dismiss
        :native_menu_dispatch
        check-cast v${registers.registerD}, $endpointType
    """, ExternalLabel("native_menu_dismiss", mutableDispatchMethod.getInstruction(dismissalIndex)))
}
