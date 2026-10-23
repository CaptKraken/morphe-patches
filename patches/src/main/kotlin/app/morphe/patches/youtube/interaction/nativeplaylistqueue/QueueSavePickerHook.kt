/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SAVE_ACTIONS = "Lapp/morphe/extension/youtube/patches/queue/QueueSaveActions;"
private const val LIST = "Ljava/util/List;"

/** Supply a real batch only to a picker launched by our queue header; keep native callbacks. */
internal fun installQueueSavePicker(context: BytecodePatchContext, endpointType: String) = with(context) {
    installQueueSaveFeedback(context, endpointType)
    val request = QueueSaveRequestFingerprint.matchSingle().originalClassDef
    val picker = queueSavePickerFingerprint(request.type).matchSingle()
    val factory = queueSavePickerFactoryFingerprint(picker.originalClassDef.type, endpointType).matchSingle()
    val pickerInterface = SAVE_ACTIONS.removeSuffix(";") + "\$Picker;"

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
        invoke-static {v${serializeCall.registerC}, v${putArguments.registerC}}, $EXTENSION_CLASS->onQueueSaveArguments(Lcom/google/protobuf/MessageLite;Landroid/os/Bundle;)V
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
    // Use the constructor tied to this picker to discover its response callback.
    // Leave the native picker request entirely unchanged: its batch variation
    // returns HTTP 400 before the UI can open on the verified client.
    val callbackConstructor = instructions.mapNotNull { it.getReference<MethodReference>() }.single {
        it.name == "<init>" && mutablePicker.type in it.parameterTypes.map { type -> type.toString() }
    }
    val callbackType = callbackConstructor.definingClass
    val callbackPicker = classDefBy(callbackType).fields.single { it.type == mutablePicker.type }

    val response = queueSaveResponseFingerprint(callbackType).matchSingle()
    val responseCast = response.instructionMatches.first()
    val responseType = responseCast.instruction.getReference<TypeReference>()!!.type
    val responseRegister = (responseCast.instruction as OneRegisterInstruction).registerA
    val responsePickerRegister = response.method.findFreeRegister(responseCast.index + 1, responseRegister, response.method.p0Register)
    check(responsePickerRegister <= 15 && responseRegister <= 15 && response.method.p0Register <= 15)
    response.method.addInstructionsAtControlFlowLabel(responseCast.index + 1, """
        iget-object v$responsePickerRegister, p0, $callbackPicker
        invoke-static {v$responsePickerRegister, v$responseRegister}, $SAVE_ACTIONS->onPickerResponse(${pickerInterface}Lcom/google/protobuf/MessageLite;)Lcom/google/protobuf/MessageLite;
        move-result-object v$responseRegister
        check-cast v$responseRegister, $responseType
    """)
    val responseDefault = classDefBy(responseType).fields.single {
        it.type == responseType && AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val parser = generateSequence(classDefBy(responseType)) { it.superclass?.let(::classDefByOrNull) }
        .firstNotNullOf { type -> Fingerprint(definingClass = type.type, name = "parseFrom",
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            returnType = type.type).originalMethodOrNull }
    mutablePicker.methods.add(ImmutableMethod(mutablePicker.type, "patch_parseQueueSaveResponse",
        listOf(ImmutableMethodParameter("[B", null, null)), "Lcom/google/protobuf/MessageLite;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null, MutableMethodImplementation(4))
        .toMutable().apply {
            addInstructions(0, """
                sget-object v0, $responseDefault
                invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
                move-result-object v1
                invoke-static {v0, p1, v1}, $parser
                move-result-object v0
                return-object v0
            """)
        })
}
