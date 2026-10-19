/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/** Retain native button validation and dispatch every unrelated command unchanged. */
internal fun installPlaylistRemoval(context: BytecodePatchContext) = with(context) {
    val dispatch = NativeButtonDispatchFingerprint.matchSingle()
    val serviceCall = dispatch.instructionMatches.single()
    val registers = serviceCall.instruction as FiveRegisterInstruction
    val endpointType = serviceCall.instruction.getReference<MethodReference>()!!.parameterTypes.first()
    // Reuse the endpoint result register, keeping every live native local intact.
    // Null consumes our local command; otherwise restore the native endpoint type.
    dispatch.method.addInstructionsAtControlFlowLabel(serviceCall.index, """
        invoke-static {v${registers.registerD}, v${registers.registerE}}, $EXTENSION_CLASS->onRemoveCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)Lcom/google/protobuf/MessageLite;
        move-result-object v${registers.registerD}
        if-nez v${registers.registerD}, :native_dispatch
        return-void
        :native_dispatch
        check-cast v${registers.registerD}, $endpointType
    """)
}

/** Preserve native swipe drawing when an attached playing row becomes removable. */
internal fun installPlaylistSwipeBackground(context: BytecodePatchContext, listenerType: String) = with(context) {
    val attached = PlaylistSwipeAttachedFingerprint.matchSingle()
    val foregroundStore = attached.instructionMatches.first()
    val backgroundRead = attached.instructionMatches[1].instruction as FiveRegisterInstruction
    val backgroundStore = attached.instructionMatches.last()
    val foregroundRegisters = foregroundStore.instruction as TwoRegisterInstruction
    val backgroundRegisters = backgroundStore.instruction as TwoRegisterInstruction
    check(foregroundRegisters.registerA == backgroundRead.registerC &&
            foregroundRegisters.registerB == backgroundRegisters.registerB) {
        "Native playlist queue: swipe drawable must belong to the attached foreground"
    }
    val foreground = foregroundStore.instruction.getReference<FieldReference>()!!
    val cachedBackground = backgroundStore.instruction.getReference<FieldReference>()!!
    val swipe = mutableClassDefBy(attached.originalClassDef.type)
    val backgroundInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$SwipeBackground;"
    swipe.interfaces.add(backgroundInterface)
    swipe.methods.add(ImmutableMethod(swipe.type, "patch_refreshQueueSwipeBackground", emptyList(),
        "V", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(2)).toMutable().apply {
        // Before attachment there is no foreground yet; the native attachment
        // callback will initialize both fields normally. Never replace its drawable.
        addInstructionsWithLabels(0, """
            iget-object v0, p0, $foreground
            if-eqz v0, :done
            invoke-virtual {v0}, Landroid/view/View;->getBackground()Landroid/graphics/drawable/Drawable;
            move-result-object v0
            iput-object v0, p0, $cachedBackground
            :done
            return-void
        """)
    })
    val update = PlaylistRowBackgroundFingerprint.matchSingle()
    val holder = mutableClassDefBy(update.originalClassDef.type)
    val listener = holder.fields.single { it.type == listenerType }
    val swipeField = holder.fields.single { it.type == swipe.type }
    val clickInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$RowClick;"
    val bridge = ImmutableMethod(holder.type, "patch_refreshQueueSwipeBackground", emptyList(),
        "V", AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(3)).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-object v0, p0, $listener
            iget-object v1, p0, $swipeField
            invoke-static {v0, v1}, $EXTENSION_CLASS->onSwipeBackgroundChanged($clickInterface$backgroundInterface)V
            return-void
        """)
    }
    holder.methods.add(bridge)
    // Read the receiver from the foreground load, while it is still live. The
    // native method later reuses p0 for a color, so its final return is unsafe.
    val receiver = (update.instructionMatches.first().instruction as TwoRegisterInstruction).registerB
    update.method.addInstruction(update.instructionMatches[1].index + 1,
        "invoke-direct/range {v$receiver .. v$receiver}, $bridge")
}
