/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SAVE_ACTIONS = "Lapp/morphe/extension/youtube/patches/queue/QueueSaveActions;"
private const val LIST = "Ljava/util/List;"

/** Supply a real batch only to a picker launched by our queue header; keep native callbacks. */
internal fun installQueueSavePicker(context: BytecodePatchContext, endpointType: String) = with(context) {
    val request = QueueSaveRequestFingerprint.matchSingle().originalClassDef
    val picker = queueSavePickerFingerprint(request.type).matchSingle()
    val factory = queueSavePickerFactoryFingerprint(picker.originalClassDef.type, endpointType).matchSingle()
    val endpointInterface = SAVE_ACTIONS.removeSuffix(";") + "\$Endpoint;"
    val pickerInterface = SAVE_ACTIONS.removeSuffix(";") + "\$Picker;"
    val endpoint = mutableClassDefBy(endpointType)
    endpoint.interfaces.add(endpointInterface)
    // Local extension state; the generated protobuf schema never serializes this field.
    val selection = ImmutableField(endpointType, "patch_queueSaveVideoIds", LIST,
        AccessFlags.PRIVATE.value, null, null, null).toMutable()
    endpoint.fields.add(selection)
    endpoint.methods.add(ImmutableMethod(endpointType, "patch_getQueueSaveVideoIds", emptyList(), LIST,
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null, MutableMethodImplementation(2))
        .toMutable().apply {
            addInstructions(0, """
                iget-object v0, p0, $selection
                return-object v0
            """)
        })
    endpoint.methods.add(ImmutableMethod(endpointType, "patch_setQueueSaveVideoIds",
        listOf(ImmutableMethodParameter(LIST, null, null)), "V",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null, MutableMethodImplementation(2))
        .toMutable().apply {
            addInstructions(0, """
                iput-object p1, p0, $selection
                return-void
            """)
        })

    val factoryInstructions = factory.originalMethod.implementation!!.instructions.toList()
    val serialize = factoryInstructions.withIndex().single {
        it.value.getReference<MethodReference>()?.let { reference ->
            reference.name == "toByteArray" && reference.returnType == "[B" && reference.parameterTypes.isEmpty()
        } == true
    }
    val putArguments = factoryInstructions.single {
        it.getReference<MethodReference>()?.toString() == "Landroid/os/Bundle;->putByteArray(Ljava/lang/String;[B)V"
    } as FiveRegisterInstruction
    val serializeCall = serialize.value as FiveRegisterInstruction
    check(serializeCall.registerC == factory.originalMethod.p0Register + 1 &&
            serializeCall.registerC <= 15 && putArguments.registerC <= 15) {
        "Native playlist queue: picker must serialize its original endpoint after constructing arguments"
    }
    // p1 is overwritten by the serialized bytes immediately afterwards. Transfer the
    // owned snapshot before that write, on both the existing/new Bundle branches.
    factory.method.addInstructionsAtControlFlowLabel(serialize.index, """
        invoke-static {v${serializeCall.registerC}, v${putArguments.registerC}}, $EXTENSION_CLASS->onQueueSaveArguments(${endpointInterface}Landroid/os/Bundle;)V
    """)

    val instructions = picker.originalMethod.implementation!!.instructions.toList()
    val argumentsField = instructions.mapNotNull { it.getReference<FieldReference>() }
        .filter { it.type == "Landroid/os/Bundle;" }.distinct().single()
    val mutablePicker = mutableClassDefBy(picker.originalClassDef.type)
    mutablePicker.interfaces.add(pickerInterface)
    mutablePicker.methods.add(ImmutableMethod(mutablePicker.type, "patch_getQueueSaveVideoIds", emptyList(), LIST,
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null, MutableMethodImplementation(2))
        .toMutable().apply {
            addInstructions(0, """
                iget-object v0, p0, $argumentsField
                invoke-static {v0}, $EXTENSION_CLASS->getQueueSaveVideoIds(Landroid/os/Bundle;)$LIST
                move-result-object v0
                return-object v0
            """)
        })
    val listField = request.fields.single { it.type == LIST }
    val readList = instructions.withIndex().single {
        it.value.opcode == Opcode.IGET_OBJECT &&
                it.value.getReference<FieldReference>()?.toString() == listField.toString()
    }
    val listRegister = (readList.value as TwoRegisterInstruction).registerA
    val add = instructions.withIndex().drop(readList.index + 1).first {
        it.value.getReference<MethodReference>()?.toString() == "Ljava/util/List;->add(Ljava/lang/Object;)Z" &&
                (it.value as FiveRegisterInstruction).registerC == listRegister
    }
    check(instructions[add.index + 1].opcode != Opcode.MOVE_RESULT) {
        "Native playlist queue: request insertion unexpectedly consumes its boolean result"
    }
    // The initializer has reused p0 by now. Get the actual picker from the native
    // response callback, whose singleton ID was just added to the request list.
    val videoRegister = (add.value as FiveRegisterInstruction).registerD
    val videoRead = instructions.take(readList.index).last {
        it.opcode == Opcode.IGET_OBJECT && (it as TwoRegisterInstruction).registerA == videoRegister
    }
    val callbackType = videoRead.getReference<FieldReference>()!!.definingClass
    val callbackRegister = (videoRead as TwoRegisterInstruction).registerB
    val callbackPicker = classDefBy(callbackType).fields.single { it.type == mutablePicker.type }
    val scratch = picker.method.findFreeRegister(add.index + 1, listRegister, callbackRegister)
    check(scratch <= 15 && callbackRegister <= 15 && listRegister <= 15) {
        "Native playlist queue: request selection bridge requires low registers"
    }
    picker.method.addInstructionsAtControlFlowLabel(add.index + 1, """
        iget-object v$scratch, v$callbackRegister, $callbackPicker
        invoke-static {v$scratch, v$listRegister}, $SAVE_ACTIONS->onPickerRequest($pickerInterface$LIST)V
    """)
}
