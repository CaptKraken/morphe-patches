/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/** Publish queue capabilities before native notification actions and playback state are built. */
internal fun installPlaylistNotificationControls(context: BytecodePatchContext, ownerType: String) = with(context) {
    val actions = NotificationSkipActionsFingerprint.matchSingle().originalClassDef
    check(actions.fields.count { it.type == ownerType } == 1) {
        "Native playlist queue: notification actions must use the discovered media owner"
    }
    val modelType = actions.fields.single { it.type.startsWith("L") && it.type != ownerType }.type
    val setter = classDefBy(modelType).methods.single {
        it.parameterTypes == listOf("Z", "Z") && it.returnType == "I"
    }
    val adapters = notificationCapabilitiesFingerprint(ownerType, setter).matchAll().take(3).toList()
    check(adapters.size == 2 && adapters.map { it.originalMethod.returnType }.toSet() == setOf("V", "I")) {
        "Native playlist queue: expected both native notification capability adapters"
    }
    val playbackState = notificationPlaybackStateFingerprint(
        adapters.single { it.originalMethod.returnType == "V" }.originalClassDef.type
    ).matchSingle().originalMethod
    val controlsInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$NotificationControls;"
    val mediaInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$MediaControls;"
    for (match in adapters) {
        val adapterType = match.originalClassDef.type
        val adapter = mutableClassDefBy(adapterType)
        val owner = adapter.fields.single { it.type == ownerType }
        val computation = match.originalMethod
        val dispatch = if (computation.returnType == "I") {
            val callers = notificationCapabilitiesDispatchFingerprint(computation).matchAll().take(4).toList()
            check(callers.size in 1..3) { "Native playlist queue: notification change dispatch is missing or ambiguous" }
            callers.map { it.instructionMatches.last().instruction.getReference<MethodReference>()!! }.distinct().single()
        } else null
        adapter.interfaces.add(controlsInterface)
        fun bridge(name: String, returnType: String, body: String) {
            adapter.methods.add(ImmutableMethod(adapterType, name, emptyList(), returnType,
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
                MutableMethodImplementation(2)).toMutable().apply { addInstructionsWithLabels(0, body) })
        }
        bridge("patch_getQueueMediaControls", mediaInterface, """
            iget-object v0, p0, $owner
            return-object v0
        """)
        // Recompute from the original adapter inputs on every refresh. Releasing
        // the queue therefore restores native capabilities without caching flags.
        bridge("patch_refreshQueueNotification", "V", """
            invoke-direct {p0}, $computation
            ${if (dispatch == null) "" else "move-result v0\ninvoke-direct {p0, v0}, $dispatch"}
            return-void
        """)
        val constructor = adapter.methods.single { it.name == "<init>" }
        val returnIndex = constructor.implementation!!.instructions.withIndex()
            .single { it.value.opcode == Opcode.RETURN_VOID }.index
        constructor.addInstructionsAtControlFlowLabel(returnIndex,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onNotificationControlsCreated($controlsInterface)V")

        // NEW clears the model's skip flags after the sequencer commits selection.
        // In background playback there need not be another watch-screen update.
        // Recompute only after the original state handler finishes clearing its
        // model and delegates; preserve its receiver because native code reuses p0.
        val stateMethod = notificationPlaybackStateFingerprint(adapterType, playbackState).matchSingle()
            .method.cloneParameters()
        stateMethod.findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
            stateMethod.addInstructionsAtControlFlowLabel(index,
                "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onNotificationPlaybackChanged($controlsInterface)V")
        }

        // Both original computations reuse p0 before the setter. Keep the receiver
        // and resolve the call again after cloning instead of retaining its index.
        match.method.cloneParameters()
        val update = notificationCapabilitiesFingerprint(ownerType, setter, adapterType).matchSingle()
        val method = update.method
        val index = update.instructionMatches.single().index
        val call = method.getInstruction<FiveRegisterInstruction>(index)
        val previous = call.registerD
        val next = call.registerE
        val capabilities = method.findFreeRegister(index)
        method.addInstructionsAtControlFlowLabel(index, """
            invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->notificationNavigationAvailability($controlsInterface)I
            move-result v$capabilities
            if-ltz v$capabilities, :native_capabilities
            and-int/lit8 v$previous, v$capabilities, 0x1
            ushr-int/lit8 v$next, v$capabilities, 0x1
            :native_capabilities
            nop
        """)
    }
}
