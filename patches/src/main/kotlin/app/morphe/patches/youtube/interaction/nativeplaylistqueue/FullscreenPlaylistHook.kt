/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.layout.buttons.action.QuickActionsElementSyntheticFingerprint
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val FULLSCREEN_EXTENSION =
    "Lapp/morphe/extension/youtube/patches/queue/QueueFullscreenActions;"
private const val FULLSCREEN_CONTROLLER =
    $$"Lapp/morphe/extension/youtube/patches/queue/QueueFullscreenActions$Controller;"

internal fun installFullscreenPlaylist(context: BytecodePatchContext) = with(context) {
    // QuickActionsMargin can already have modified this shared view callback.
    QuickActionsElementSyntheticFingerprint.clearMatch()
    val binding = QuickActionsElementSyntheticFingerprint.matchSingle()
    val container = binding.originalMethod.implementation!!.instructions
        .drop(binding.instructionMatches.last().index + 1).mapNotNull {
            if (it.opcode == Opcode.IPUT_OBJECT) it.getReference<FieldReference>() else null
        }.first { it.type == "Landroid/view/ViewGroup;" }
    val controllerType = container.definingClass
    val callback = quickActionsModelCallbackFingerprint(controllerType).matchSingle()
    val setter = callback.instructionMatches.last().getInstruction<Instruction>()
        .getReference<MethodReference>()!!
    val modelType = setter.parameterTypes.single().toString()
    val reset = quickActionsResetFingerprint(controllerType, setter).matchSingle()
    val rendered = reset.instructionMatches.last().getInstruction<Instruction>().getReference<FieldReference>()!!
    val size = quickActionsSizeFingerprint(controllerType).matchSingle()
    val dimensions = size.instructionMatches[1].getInstruction<Instruction>().getReference<FieldReference>()!!
    val emit = size.instructionMatches[2].getInstruction<Instruction>().getReference<MethodReference>()!!
    // The native BehaviorSubject owns the last measured dimensions. Re-emitting them
    // runs YouTube's original measuring/binding callback, including its lifecycle guards.
    val currentDimensions = Fingerprint(
        definingClass = dimensions.type,
        parameters = emptyList(),
        returnType = "Ljava/lang/Object;",
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL)
    ).matchSingle().originalMethod
    val modelDefault = classDefBy(modelType).fields.single {
        it.type == modelType && AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val parser = generateSequence(classDefBy(modelType)) { type ->
        type.superclass?.let { classDefByOrNull(it) }
    }.firstNotNullOfOrNull { type ->
        Fingerprint(
            definingClass = type.type,
            name = "parseFrom",
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            returnType = type.type,
            custom = { method, _ -> AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                    AccessFlags.STATIC.isSet(method.accessFlags) }
        ).originalMethodOrNull
    } ?: throw PatchException("Native playlist queue: native fullscreen model parser is missing")
    val controller = mutableClassDefBy(controllerType)
    controller.interfaces.add(FULLSCREEN_CONTROLLER)
    fun bridge(name: String, parameter: String, result: String, body: String) {
        controller.methods.add(ImmutableMethod(
            controllerType, name, listOf(ImmutableMethodParameter(parameter, null, null)), result,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(5)
        ).toMutable().apply { addInstructionsWithLabels(0, body) })
    }
    bridge("patch_parseQuickActions", "[B", "Ljava/lang/Object;", """
        sget-object v0, $modelDefault
        invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
        move-result-object v1
        invoke-static {v0, p1, v1}, $parser
        move-result-object v0
        return-object v0
    """)
    bridge("patch_refreshQuickActions", "Ljava/lang/Object;", "V", """
        const/4 v0, 0x0
        iput-boolean v0, p0, $rendered
        check-cast p1, $modelType
        invoke-virtual {p0, p1}, $setter
        iget-object v0, p0, $dimensions
        invoke-virtual {v0}, $currentDimensions
        move-result-object v1
        if-eqz v1, :done
        invoke-virtual {v0, v1}, $emit
        :done
        return-void
    """)
    // The setter's original null handling, visibility policy and other action bindings
    // all remain in place. Only our local queue can supply the additional button.
    Fingerprint(definingClass = controllerType, name = setter.name,
        parameters = listOf(modelType), returnType = "V").matchSingle().method.addInstructionsWithLabels(0, """
        invoke-static/range {p0 .. p1}, $FULLSCREEN_EXTENSION->onModelLoaded(${FULLSCREEN_CONTROLLER}Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object p1
        check-cast p1, $modelType
    """)
}
