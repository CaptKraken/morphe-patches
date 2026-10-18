/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Retain native button validation and dispatch every unrelated command unchanged. */
internal fun installPlaylistRemoval(context: BytecodePatchContext) = with(context) {
    val dispatch = NativeButtonDispatchFingerprint.matchSingle()
    val serviceCall = dispatch.instructionMatches.single()
    val registers = serviceCall.instruction as FiveRegisterInstruction
    val endpointType = serviceCall.instruction.getReference<MethodReference>()!!.parameterTypes.first()
    // Reuse the endpoint result register, keeping every live native local intact.
    // Null consumes our local command; otherwise restore the native endpoint type.
    dispatch.method.addInstructionsAtControlFlowLabel(serviceCall.index, """
        invoke-static {v${registers.registerD}, v${registers.registerE}}, $EXTENSION_CLASS->onSwipeCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)Lcom/google/protobuf/MessageLite;
        move-result-object v${registers.registerD}
        if-nez v${registers.registerD}, :native_dispatch
        return-void
        :native_dispatch
        check-cast v${registers.registerD}, $endpointType
    """)
}
