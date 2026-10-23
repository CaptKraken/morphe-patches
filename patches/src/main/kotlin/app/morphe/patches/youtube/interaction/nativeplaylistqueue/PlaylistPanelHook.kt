/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFieldFromToString
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

// The native model factory initializes queueMode to 1 before accepting a watch response.
// Keep that neutral mode: rows use ordinary watch endpoints, without attaching
// a native playlist navigator through navigationSets.
private const val DEFAULT_QUEUE_MODE = 1

/** Bridge the accepted watch model; the playback engine is attached separately at runtime. */
internal fun installPlaylistPanel(context: BytecodePatchContext) = with(context) {
    val description = PlaylistModelDescriptionFingerprint.matchSingle().originalMethod
    val modelType = description.definingClass
    val responseVideoField = description.findFieldFromToString("{videoId=")
    val playlistField = description.findFieldFromToString(", playlistId=")
    val panelField = description.findFieldFromToString(", playlistPanel=")
    val navigationField = description.findFieldFromToString(", navigationSets=")
    val panelType = panelField.type
    val controllerType = PlaylistControllerConstructorFingerprint.matchSingle().originalClassDef.type
    val controller = mutableClassDefBy(controllerType)
    val modelField = controller.fields.single { it.type == modelType }
    val collapsedView = controller.fields.single { it.type == "Landroid/view/View;" }
    val accept = acceptedPlaylistModelFingerprint(controllerType, modelField, responseVideoField).matchSingle()
    val activeVideoMatch = accept.instructionMatches[2]
    val activeVideoField = activeVideoMatch.getInstruction<TwoRegisterInstruction>()
        .getReference<FieldReference>()!!
    check(activeVideoMatch.getInstruction<TwoRegisterInstruction>().registerB == accept.originalMethod.p0Register) {
        "Native playlist queue: active video must belong to the playlist controller"
    }
    val render = playlistPanelRenderFingerprint(modelField, panelField).matchSingle()
    val transition = playlistVideoTransitionFingerprint(activeVideoField).matchSingle()
    val panelDefault = classDefBy(panelType).fields.single {
        it.type == panelType && AccessFlags.STATIC.isSet(it.accessFlags)
    }

    // Discover the host parser through the panel's inheritance chain. Its stable public
    // parseFrom API avoids calling the obfuscated Parser interface from extension Java.
    val parser = generateSequence(classDefBy(panelType)) { type ->
        type.superclass?.let { classDefByOrNull(it) }
    }.firstNotNullOfOrNull { type ->
        Fingerprint(
            definingClass = type.type,
            name = "parseFrom",
            returnType = type.type,
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            custom = { method, _ -> AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                    AccessFlags.STATIC.isSet(method.accessFlags) }
        ).originalMethodOrNull
    } ?: throw PatchException("Native playlist queue: public native panel parser is missing")

    val constructor = classDefBy(modelType).methods.single {
        it.name == "<init>" && it.parameterTypes.isNotEmpty()
    }
    // Ordinary videos can have no playlist model at all. Use the native builder's
    // defaults, including required tracking parameters, rather than inventing them.
    val modelFactory = classDefBy(modelType).methods.single {
        AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags) &&
                it.parameterTypes.isEmpty() && it.returnType.startsWith("L") && it.returnType != modelType
    }
    val buildModel = classDefBy(modelFactory.returnType).methods.single {
        AccessFlags.PUBLIC.isSet(it.accessFlags) && !AccessFlags.STATIC.isSet(it.accessFlags) &&
                it.parameterTypes.isEmpty() && it.returnType == modelType
    }
    check(constructor.parameterTypes.size == 7 && constructor.parameterTypes.none { it in listOf("J", "D") }) {
        "Native playlist queue: unexpected accepted-model constructor"
    }
    // Constructor parameter positions are derived from actual field stores, not field order.
    val assignments = constructor.implementation!!.instructions.mapNotNull { instruction ->
        if (instruction.opcode !in listOf(Opcode.IPUT, Opcode.IPUT_OBJECT)) return@mapNotNull null
        val store = instruction as TwoRegisterInstruction
        if (store.registerB != constructor.p0Register) return@mapNotNull null
        (store.registerA - constructor.p0Register - 1) to instruction.getReference<FieldReference>()!!
    }.toMap()
    val constructorFields = constructor.parameterTypes.mapIndexed { index, type ->
        val field = assignments[index]
            ?: throw PatchException("Native playlist queue: model parameter $index has no direct field assignment")
        check(field.type == type && AccessFlags.PUBLIC.isSet(classDefBy(modelType).fields.single {
            it.toString() == field.toString()
        }.accessFlags)) { "Native playlist queue: model parameter field must remain readable" }
        field
    }
    check(constructorFields.count { it == panelField } == 1)

    fun addBridge(name: String, parameters: List<String>, returnType: String, locals: Int, body: String) {
        controller.methods.add(ImmutableMethod(
            controllerType, name,
            parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(locals + parameters.size + 1)
        ).toMutable().apply { addInstructionsWithLabels(0, body) })
    }

    controller.interfaces.add(CONTROLLER_INTERFACE)
    // A watch response can omit its video ID. Use the native stale-response guard's
    // active video instead of treating an optional response field as playback state.
    addBridge("patch_getVideoId", emptyList(), "Ljava/lang/String;", 1, """
        iget-object v0, p0, $activeVideoField
        if-nez v0, :done
        const-string v0, ""
        :done
        return-object v0
    """)
    addBridge("patch_getPlaylistId", emptyList(), "Ljava/lang/String;", 1, """
        iget-object v0, p0, $modelField
        if-eqz v0, :empty
        iget-object v0, v0, $playlistField
        if-nez v0, :done
        :empty
        const-string v0, ""
        :done
        return-object v0
    """)
    addBridge("patch_getModel", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $modelField
        return-object v0
    """)
    addBridge("patch_getPanel", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $modelField
        if-eqz v0, :done
        iget-object v0, v0, $panelField
        :done
        return-object v0
    """)
    addBridge("patch_isAttached", emptyList(), "Z", 1, """
        iget-object v0, p0, $collapsedView
        invoke-virtual {v0}, Landroid/view/View;->isAttachedToWindow()Z
        move-result v0
        return v0
    """)
    val copyArguments = constructorFields.mapIndexed { index, field ->
        when {
            field == responseVideoField -> "iget-object v${index + 5}, p0, $activeVideoField"
            field == panelField -> "move-object v${index + 5}, v3"
            field == navigationField -> "const/4 v${index + 5}, 0x0"
            field.type == "I" -> "const/4 v${index + 5}, $DEFAULT_QUEUE_MODE"
            else -> "iget-object v${index + 5}, v0, $field"
        }
    }.joinToString("\n")
    addBridge("patch_updatePanel", listOf("[B"), "Ljava/lang/Object;", 13, """
        iget-object v0, p0, $modelField
        if-nez v0, :model_ready
        invoke-static {}, $modelFactory
        move-result-object v0
        invoke-virtual {v0}, $buildModel
        move-result-object v0
        :model_ready
        sget-object v1, $panelDefault
        invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
        move-result-object v2
        move-object/from16 v3, p1
        invoke-static {v1, v3, v2}, $parser
        move-result-object v3
        check-cast v3, $panelType
        new-instance v4, $modelType
        $copyArguments
        invoke-direct/range {v4 .. v11}, $constructor
        move-object/from16 v12, p0
        invoke-direct {v12, v4}, ${accept.originalMethod}
        iget-object v0, v12, $modelField
        if-ne v0, v4, :rejected
        return-object v4
        :rejected
        const/4 v0, 0x0
        return-object v0
    """)
    addBridge("patch_restorePanel", List(2) { "Ljava/lang/Object;" }, "V", 1, """
        iget-object v0, p0, $modelField
        if-ne v0, p1, :done
        check-cast p2, $modelType
        iput-object p2, p0, $modelField
        invoke-virtual/range {p0 .. p0}, ${render.originalMethod}
        :done
        return-void
    """)
    installPlaylistRowPlayback(context, modelField, panelField)

    // Preserve every native rejection before this assignment. In particular, a stale model
    // must not claim the current controller or overwrite our local insertion state.
    accept.method.addInstruction(accept.instructionMatches.last().index + 1,
        "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onControllerUpdated($CONTROLLER_INTERFACE)V")

    // Observe completed transitions, including native early returns that leave no model.
    // Do not publish between the native assignments of its video/playlist identity fields.
    // The native transition reuses p0 as a local on some exits. Preserve the
    // original receiver before adding callbacks at those returns.
    transition.method.cloneParameters().apply {
        findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
            addInstructionsAtControlFlowLabel(index,
                "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onControllerUpdated($CONTROLLER_INTERFACE)V")
        }
    }
}
