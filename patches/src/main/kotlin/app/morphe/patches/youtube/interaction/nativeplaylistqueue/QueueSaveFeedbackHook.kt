/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SAVE_ACTIONS = "Lapp/morphe/extension/youtube/patches/queue/QueueSaveActions;"
private const val FEEDBACK = "Lapp/morphe/extension/youtube/patches/queue/QueueSaveFeedback;"
private val CALLBACK = FEEDBACK.removeSuffix(";") + "\$Callback;"
private val SELECTION = FEEDBACK.removeSuffix(";") + "\$Selection;"

/** Add a native response action only for the callback created by an owned queue save. */
internal fun installQueueSaveFeedback(context: BytecodePatchContext, endpointType: String) = with(context) {
    val callbackType = QueueSaveEditErrorFingerprint.matchSingle().originalClassDef.type
    val creation = queueSaveEditCreationFingerprint(callbackType, endpointType).matchSingle()
    val success = queueSaveEditSuccessFingerprint(callbackType).matchSingle()
    val responseType = success.originalMethod.parameterTypes.single().toString()
    val callback = mutableClassDefBy(callbackType)
    callback.interfaces.add(CALLBACK)
    val selection = ImmutableField(callbackType, "patch_queueSaveSelection", SELECTION,
        AccessFlags.PRIVATE.value, null, null, null).toMutable()
    callback.fields.add(selection)
    fun bridge(name: String, parameters: List<String>, result: String, locals: Int, body: String) {
        callback.methods.add(ImmutableMethod(callbackType, name,
            parameters.map { ImmutableMethodParameter(it, null, null) }, result,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(locals + parameters.size + 1)).toMutable().apply {
            addInstructions(0, body)
        })
    }
    bridge("patch_getQueueSaveSelection", emptyList(), SELECTION, 1, """
        iget-object v0, p0, $selection
        return-object v0
    """)
    bridge("patch_setQueueSaveSelection", listOf(SELECTION), "V", 0, """
        iput-object p1, p0, $selection
        return-void
    """)
    val defaultResponse = classDefBy(responseType).fields.single {
        it.type == responseType && AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val parser = generateSequence(classDefBy(responseType)) { it.superclass?.let(::classDefByOrNull) }
        .firstNotNullOf { type -> Fingerprint(definingClass = type.type, name = "parseFrom",
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            returnType = type.type).originalMethodOrNull }
    bridge("patch_parseQueueSaveResult", listOf("[B"), "Lcom/google/protobuf/MessageLite;", 2, """
        sget-object v0, $defaultResponse
        invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
        move-result-object v1
        invoke-static {v0, p1, v1}, $parser
        move-result-object v0
        return-object v0
    """)
    val construct = creation.instructionMatches.single()
    val reference = construct.instruction.getReference<MethodReference>()!!
    check(reference.definingClass == callbackType && reference.name == "<init>")
    val receiver = when (val instruction = construct.instruction) {
        is RegisterRangeInstruction -> instruction.startRegister
        is FiveRegisterInstruction -> instruction.registerC
        else -> error("Native playlist queue: unsupported save callback constructor encoding")
    }
    val endpoint = creation.originalMethod.p0Register + 1
    check(receiver <= 15 && endpoint <= 15) { "Native playlist queue: save context requires low registers" }
    // The constructor has completed. The original endpoint parameter is still
    // live here, before the resolver proceeds to either native request branch.
    creation.method.addInstructionsAtControlFlowLabel(construct.index + 1, """
        invoke-static {v$receiver, v$endpoint}, $SAVE_ACTIONS->onEditCreated(${CALLBACK}Lcom/google/protobuf/MessageLite;)V
    """)
    success.method.addInstructionsAtControlFlowLabel(0, """
        invoke-static/range {p0 .. p1}, $SAVE_ACTIONS->onEditResult(${CALLBACK}Lcom/google/protobuf/MessageLite;)Lcom/google/protobuf/MessageLite;
        move-result-object p1
        check-cast p1, $responseType
    """)
}

/** Use the native builder and scheduler for owned Save and queue-clear confirmations. */
internal fun installQueueSaveConfirmationDuration(context: BytecodePatchContext, factory: Method) = with(context) {
    val finalization = queueSnackbarDurationFingerprint(factory.returnType).matchSingle()
    val durationSetter = finalization.originalMethod.implementation!!.instructions.mapNotNull {
        it.getReference<MethodReference>()?.takeIf { reference ->
            reference.definingClass == factory.returnType && reference.parameterTypes == listOf("I") &&
                    reference.returnType == "V"
        }
    }.distinct().single()
    check(AccessFlags.PUBLIC.isSet(classDefBy(factory.returnType).methods.single {
        it.toString() == durationSetter.toString()
    }.accessFlags)) { "Native playlist queue: snackbar duration setter must be callable" }
    // Keep the input renderer even when the native factory reuses its parameters.
    val method = mutableClassDefBy(factory.definingClass).methods.single { it.toString() == factory.toString() }
        .cloneParameters()
    val returns = method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
    check(returns.size == 1) { "Native playlist queue: expected one initialized snackbar builder exit" }
    val index = returns.single().index
    val builder = (returns.single().value as OneRegisterInstruction).registerA
    val duration = method.findFreeRegister(index)
    check(builder <= 15 && duration <= 15) { "Native playlist queue: snackbar duration requires low registers" }
    method.addInstructionsAtControlFlowLabel(index, """
        invoke-static/range {p1 .. p1}, $FEEDBACK->confirmationDuration(Lcom/google/protobuf/MessageLite;)I
        move-result v$duration
        if-lez v$duration, :native_duration
        invoke-virtual {v$builder, v$duration}, $durationSetter
        :native_duration
        nop
    """)
}
