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
import app.morphe.util.findFreeRegister
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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
    val legacy = QueueSnackbarFactoryFingerprint.matchSingle()
    val factory = legacy.originalClassDef
    val modern = factory.methods.single {
        it.returnType == legacy.originalMethod.returnType && it.parameterTypes.size == 2 &&
                it.parameterTypes.last() == "Ljava/util/Map;" &&
                it.parameterTypes.first() != legacy.originalMethod.parameterTypes.first()
    }
    installQueueSaveConfirmationDuration(context, modern)
    val dispatcher = factory.fields.single { field -> classDefByOrNull(field.type)?.methods?.any {
        it.returnType == "V" && it.parameterTypes == listOf(endpointType, "Ljava/util/Map;")
    } == true }
    val callback = classDefBy(dispatcher.type).methods.single {
        it.returnType == "V" && it.parameterTypes == listOf(endpointType, "Ljava/util/Map;")
    }
    installQueuePlayerClose(this, endpointType, defaultEndpoint, callback)
    val listenerType = modern.implementation!!.instructions.mapNotNull {
        it.getReference<MethodReference>()?.takeIf { reference -> reference.name == "<init>" &&
                "Landroid/view/View\$OnClickListener;" in classDefBy(reference.definingClass).interfaces
        }?.definingClass
    }.distinct().single()
    val click = queueUndoClickFingerprint(listenerType, factory.type, dispatcher.type, callback).matchSingle()
    val instructions = click.originalMethod.implementation!!.instructions.toList()
    val calls = instructions.withIndex().filter {
        it.value.opcode == Opcode.IGET_OBJECT && it.value.getReference<FieldReference>() == dispatcher
    }.map { read ->
        val receiver = (read.value as TwoRegisterInstruction).registerA
        ((read.index + 1)..minOf(read.index + 6, instructions.lastIndex)).single { index ->
            val instruction = instructions[index]
            instruction.opcode == Opcode.INVOKE_INTERFACE &&
                    instruction.getReference<MethodReference>() == callback &&
                    (instruction as FiveRegisterInstruction).registerC == receiver
        }
    }
    // Modern buttons can carry service, navigation and command endpoints. Intercept
    // only the three dispatches supplied by this snackbar factory, not other lambda cases.
    check(calls.size == 3 && calls.distinct().size == 3) {
        "Native playlist queue: expected three modern snackbar endpoint dispatches"
    }
    for (index in calls.sortedDescending()) {
        val registers = click.method.getInstruction<FiveRegisterInstruction>(index)
        val consumed = click.method.findFreeRegister(index)
        click.method.addInstructionsAtControlFlowLabel(index, """
            invoke-static {v${registers.registerD}}, $EXTENSION_CLASS->onQueueUndoCommand(Lcom/google/protobuf/MessageLite;)Z
            move-result v$consumed
            if-nez v$consumed, :local_undo_handled
        """, ExternalLabel("local_undo_handled", click.method.getInstruction(index + 1)))
    }
}
