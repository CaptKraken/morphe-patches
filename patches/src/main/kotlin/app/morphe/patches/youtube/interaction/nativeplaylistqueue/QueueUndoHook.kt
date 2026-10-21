/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Parse feedback with the native registry and chain its existing snackbar callback. */
internal fun installQueueUndo(context: BytecodePatchContext, endpointType: String) = with(context) {
    val endpoint = mutableClassDefBy(endpointType)
    val command = "Lapp/morphe/extension/youtube/patches/queue/QueueUndoActions\$Command;"
    val defaultEndpoint = endpoint.fields.single {
        it.type == endpointType && AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val parser = generateSequence(classDefBy(endpointType)) { it.superclass?.let(::classDefByOrNull) }
        .firstNotNullOf { type -> Fingerprint(definingClass = type.type, name = "parseFrom",
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            returnType = type.type).originalMethodOrNull }
    endpoint.interfaces.add(command)
    endpoint.methods.add(ImmutableMethod(endpointType, "patch_parseQueueCommand",
        listOf(ImmutableMethodParameter("[B", null, null)), "Lcom/google/protobuf/MessageLite;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(4)).toMutable().apply {
        addInstructions(0, """
            sget-object v0, $defaultEndpoint
            invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
            move-result-object v1
            invoke-static {v0, p1, v1}, $parser
            move-result-object v0
            return-object v0
        """)
    })
    val click = queueUndoClickFingerprint(endpointType).matchSingle()
    val call = click.instructionMatches[1]
    val registers = call.instruction as FiveRegisterInstruction
    val instructions = click.originalMethod.implementation!!.instructions.toList()
    val dismissalBranch = instructions[call.index + 1]
    fun address(index: Int) = instructions.take(index).sumOf { it.codeUnits }
    val dismissalCast = click.instructionMatches[2]
    val dismissalLoadIndex = dismissalCast.index - 1
    val dismissalLoad = instructions[dismissalLoadIndex]
    check(dismissalLoad.opcode == Opcode.IGET_OBJECT &&
            (dismissalLoad as TwoRegisterInstruction).registerA ==
            (dismissalCast.instruction as OneRegisterInstruction).registerA &&
            dismissalBranch.opcode == Opcode.GOTO_16 &&
            address(call.index + 1) + (dismissalBranch as OffsetInstruction).codeOffset ==
            address(dismissalLoadIndex)) {
        "Native playlist queue: snackbar callback must branch to its discovered native dismissal"
    }
    // Consume only queue-owned commands. Other snackbar actions, including native
    // playlist Save Undo, retain the exact original callback and dismissal path.
    click.method.addInstructionsAtControlFlowLabel(call.index, """
        invoke-static {v${registers.registerD}, v${registers.registerE}}, $EXTENSION_CLASS->onQueueMenuCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)Lcom/google/protobuf/MessageLite;
        move-result-object v${registers.registerD}
        if-eqz v${registers.registerD}, :dismiss_queue_undo
        check-cast v${registers.registerD}, $endpointType
    """, ExternalLabel("dismiss_queue_undo", click.method.getInstruction(call.index + 1)))
}
