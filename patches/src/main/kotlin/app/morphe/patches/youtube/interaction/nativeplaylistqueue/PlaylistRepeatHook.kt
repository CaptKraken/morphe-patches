/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.cloneParameters
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Reuse native repeat observers/listeners while the local queue owns the sequence. */
internal fun installPlaylistRepeat(
    context: BytecodePatchContext,
    controllerType: String,
    controlsField: FieldReference,
    navigatorType: String
) = with(context) {
    val binding = PlaylistRepeatBindingFingerprint.matchSingle()
    val headerType = binding.originalClassDef.type
    val mode = binding.instructionMatches[1].instruction.getReference<FieldReference>()!!
    val shuffle = binding.instructionMatches[2].instruction.getReference<FieldReference>()!!
    check(mode.definingClass == shuffle.definingClass) {
        "Native playlist queue: repeat and shuffle must share their native observer state"
    }
    val stateType = mode.definingClass
    val stateField = classDefBy(controlsField.type).fields.single { it.type == stateType }
    check(AccessFlags.PUBLIC.isSet(stateField.accessFlags)) {
        "Native playlist queue: bound repeat state must be readable"
    }
    val stateInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$RepeatState;"
    val headerInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$RepeatHeader;"
    fun bridge(owner: String, name: String, parameters: List<String>, returnType: String, locals: Int, body: String) {
        mutableClassDefBy(owner).methods.add(ImmutableMethod(owner, name,
            parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(locals + parameters.size + 1)).toMutable().apply {
            addInstructionsWithLabels(0, body)
        })
    }
    bridge(controllerType, "patch_getRepeatState", emptyList(), stateInterface, 1, """
        iget-object v0, p0, $controlsField
        if-eqz v0, :missing
        iget-object v0, v0, $stateField
        return-object v0
        :missing
        const/4 v0, 0x0
        return-object v0
    """)
    mutableClassDefBy(stateType).interfaces.add(stateInterface)
    bridge(stateType, "patch_getRepeatMode", emptyList(), "I", 1, """
        iget v0, p0, $mode
        return v0
    """)
    val observer = playlistRepeatObserverFingerprint(mode, shuffle).matchSingle()
    val nativeMode = observer.instructionMatches.first().instruction as TwoRegisterInstruction
    val nativeState = observer.instructionMatches[2].instruction as OneRegisterInstruction
    val eventMode = observer.instructionMatches.first().instruction.getReference<FieldReference>()!!
    val eventShuffle = observer.instructionMatches[6].instruction.getReference<FieldReference>()!!
    val eventType = eventMode.definingClass
    val eventConstructor = classDefBy(eventType).methods.single {
        it.name == "<init>" && it.parameterTypes == listOf("Z", "Z", "I", "Z")
    }
    val eventStores = eventConstructor.implementation!!.instructions.mapNotNull { instruction ->
        if (instruction.opcode !in listOf(Opcode.IPUT, Opcode.IPUT_BOOLEAN)) return@mapNotNull null
        val store = instruction as TwoRegisterInstruction
        if (store.registerB != eventConstructor.p0Register) return@mapNotNull null
        (store.registerA - eventConstructor.p0Register - 1) to instruction.getReference<FieldReference>()!!
    }.toMap()
    check(eventStores[2] == eventMode && eventStores[3] == eventShuffle) {
        "Native playlist queue: unexpected native repeat notification constructor"
    }
    val callbackType = observer.originalClassDef.type
    val callbackConstructor = classDefBy(callbackType).methods.single {
        it.name == "<init>" && it.parameterTypes == listOf("Ljava/lang/Object;", "I")
    }
    val subscription = playlistRepeatSubscriptionFingerprint(stateType, callbackConstructor).matchSingle()
    val callbackCall = subscription.instructionMatches.last()
    val callbackRegisters = callbackCall.instruction as FiveRegisterInstruction
    val selectorInstruction = subscription.instructionMatches[subscription.instructionMatches.lastIndex - 1].instruction
    check(callbackRegisters.registerD == subscription.originalMethod.p0Register &&
            (selectorInstruction as OneRegisterInstruction).registerA == callbackRegisters.registerE) {
        "Native playlist queue: repeat notification must bind its own native state"
    }
    val selector = (selectorInstruction as NarrowLiteralInstruction).narrowLiteral
    val source = subscription.instructionMatches.first().instruction.getReference<FieldReference>()!!
    bridge(stateType, "patch_getRepeatContext", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $source
        return-object v0
    """)
    val stateConstructor = playlistRepeatStateConstructorFingerprint(stateType, source.type).matchSingle()
    val initializedState = stateConstructor.instructionMatches.first().instruction as TwoRegisterInstruction
    check(initializedState.registerB == stateConstructor.originalMethod.p0Register) {
        "Native playlist queue: repeat observer registration must use its initialized receiver"
    }
    stateConstructor.method.addInstructionsAtControlFlowLabel(stateConstructor.instructionMatches.last().index,
        "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onRepeatStateCreated($stateInterface)V")
    // Invoke the same native notification consumer used by the live subscription.
    // It reads only mode/shuffle; the event's previous/next flags are not observed here.
    // This preserves weak observers, reactive notifications, icons and native feedback.
    bridge(stateType, "patch_notifyRepeatMode", listOf("I"), "V", 6, """
        new-instance v0, $callbackType
        const v1, $selector
        invoke-direct {v0, p0, v1}, $callbackConstructor
        new-instance v1, $eventType
        const/4 v2, 0x0
        const/4 v3, 0x0
        move v4, p1
        iget-boolean v5, p0, $shuffle
        invoke-direct/range {v1 .. v5}, $eventConstructor
        invoke-virtual {v0, v1}, ${observer.originalMethod}
        return-void
    """)
    observer.method.addInstructionsAtControlFlowLabel(observer.instructionMatches[3].index, """
        invoke-static {v${nativeState.registerA}, v${nativeMode.registerA}}, $EXTENSION_CLASS->resolveRepeatMode(${stateInterface}I)I
        move-result v${nativeMode.registerA}
    """)
    val repeatSetter = classDefBy(navigatorType).methods.single {
        it.parameterTypes == listOf("I") && it.returnType == "V"
    }
    // Intercept control intent, not the setters themselves: native playback also
    // calls the same setters to reset its backend during context changes.
    val gearType = PlayerLoopMenuConstructorFingerprint.matchSingle().originalClassDef.type
    val controlRequests = listOf(
        playlistHeaderRepeatRequestFingerprint(headerType, repeatSetter).matchSingle() to 2,
        playerLoopRequestFingerprint(gearType, repeatSetter).matchSingle() to 1,
        playerLoopCommandFingerprint(repeatSetter).matchSingle() to 2
    )
    controlRequests.forEach { (match, expectedCalls) ->
        val method = match.method
        val indexes = method.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            instruction.getReference<MethodReference>() == repeatSetter
        }.map { it.index }
        check(indexes.size == expectedCalls) {
            "Native playlist queue: unexpected number of native repeat control requests"
        }
        indexes.asReversed().forEach { index ->
            val call = method.implementation!!.instructions[index] as FiveRegisterInstruction
            check(call.registerCount == 2) { "Native playlist queue: unexpected repeat setter arguments" }
            method.addInstructionsAtControlFlowLabel(index, """
                invoke-static {v${call.registerC}, v${call.registerD}}, $EXTENSION_CLASS->onRepeatControlRequested(Ljava/lang/Object;I)I
                move-result v${call.registerD}
            """)
        }
    }
    val header = mutableClassDefBy(headerType)
    val navigator = header.fields.single { it.type == navigatorType }
    val panel = header.fields.single { it.type == binding.originalMethod.parameterTypes.first() }
    val repeatView = header.fields.single {
        it.type == "Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;"
    }
    val capabilities = PlaylistRepeatCapabilitiesFingerprint.matchSingle()
    check(capabilities.originalMethod == binding.originalMethod) {
        "Native playlist queue: repeat capabilities and state must share their native binder"
    }
    val repeatAll = capabilities.instructionMatches[0].instruction.getReference<FieldReference>()!!
    val repeatOne = capabilities.instructionMatches[2].instruction.getReference<FieldReference>()!!
    check(repeatAll != repeatOne && repeatAll.definingClass == headerType && repeatOne.definingClass == headerType) {
        "Native playlist queue: repeat capabilities must be distinct fields on the bound header"
    }
    header.interfaces.add(headerInterface)
    bridge(headerType, "patch_getNavigator", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $navigator
        return-object v0
    """)
    bridge(headerType, "patch_getPanel", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $panel
        return-object v0
    """)
    bridge(headerType, "patch_enableRepeat", emptyList(), "V", 2, """
        const/4 v0, 0x1
        iput-boolean v0, p0, $repeatAll
        iput-boolean v0, p0, $repeatOne
        iget-object v1, p0, $repeatView
        invoke-virtual {v1, v0}, Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;->setEnabled(Z)V
        return-void
    """)
    // The binder reuses p0 on some exits. Preserve its owner before observing all returns.
    binding.method.cloneParameters(header).apply {
        findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
            addInstructionsAtControlFlowLabel(index,
                "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onRepeatHeaderBound($headerInterface)V")
        }
    }
}
