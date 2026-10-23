/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.misc.loadvideo.MiniAppOpenYtContentCommandEndpointFingerprint
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Observe the user close before teardown; keep the original native listener and dismissal. */
internal fun installQueuePlayerClose(
    context: BytecodePatchContext,
    endpointType: String,
    defaultEndpoint: FieldReference,
    dispatch: MethodReference
) = with(context) {
    val dismiss = MiniAppOpenYtContentCommandEndpointFingerprint.matchSingle()
        .instructionMatches.last().instruction.getReference<MethodReference>()!!
    val fling = queuePlayerCloseFlingFingerprint(dismiss).matchSingle().originalMethod
    if (fling.name != "onFling") queuePlayerFlingCallerFingerprint(fling).matchSingle()
    for ((fingerprint, expectedCalls) in listOf(
        queuePlayerCloseClickFingerprint(dismiss) to 1,
        queuePlayerCloseFlingFingerprint(dismiss) to 2,
        queuePlayerCloseTouchFingerprint(dismiss) to 1
    )) {
        val method = fingerprint.matchSingle().method
        val dismissCalls = method.implementation!!.instructions.withIndex().filter {
            it.value.getReference<MethodReference>()?.toString() == dismiss.toString()
        }.map { it.index }
        check(dismissCalls.size == expectedCalls) {
            "Native playlist queue: unexpected user-dismiss branches in $method"
        }
        // Fling has separate accepted-dismiss branches. Preserve their thresholds,
        // branch targets and native continuation, without hooking programmatic close.
        for (index in dismissCalls.asReversed()) {
            method.addInstructionsAtControlFlowLabel(index,
                "invoke-static {}, $EXTENSION_CLASS->onQueuePlayerClosing()V")
        }
    }

    // This dispatcher belongs to the activity, not the disposed playback backend.
    // Reading it remains valid after the watch view detaches. No listener is replaced.
    val controller = mutableClassDefBy(PlaylistControllerConstructorFingerprint.matchSingle().originalClassDef.type)
    val dispatcher = controller.fields.single { it.type == dispatch.definingClass }
    controller.methods.add(ImmutableMethod(controller.type, "patch_showQueueFeedback",
        listOf(ImmutableMethodParameter("[B", null, null)), "V",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(5)).toMutable().apply {
        addInstructions(0, """
            sget-object v0, $defaultEndpoint
            invoke-virtual {v0, p1}, $endpointType->patch_parseQueueCommand([B)Lcom/google/protobuf/MessageLite;
            move-result-object v0
            check-cast v0, $endpointType
            iget-object v1, p0, $dispatcher
            invoke-static {}, Ljava/util/Collections;->emptyMap()Ljava/util/Map;
            move-result-object v2
            invoke-interface {v1, v0, v2}, $dispatch
            return-void
        """)
    })
}
