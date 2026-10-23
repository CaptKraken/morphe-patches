/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Observe the existing drag transaction; retain native animation and accessibility. */
internal fun installPlaylistReordering(context: BytecodePatchContext, videoType: String) = with(context) {
    val holderType = PlaylistDragBinderFingerprint.matchSingle().originalMethod.definingClass
    val start = playlistDragStartFingerprint(holderType).matchSingle()
    val end = playlistDragEndFingerprint(holderType).matchSingle()
    check(start.originalMethod.parameterTypes.take(2) == end.originalMethod.parameterTypes.take(2)) {
        "Native playlist queue: drag callbacks disagree about their holder/adapter types"
    }
    for (callback in listOf(start, end)) {
        val predicate = callback.instructionMatches.first().instruction as TwoRegisterInstruction
        val receiver = callback.originalMethod.implementation!!.registerCount -
                callback.originalMethod.parameterTypes.size - 1
        check(setOf(predicate.registerA, predicate.registerB) == setOf(receiver, receiver + 1)) {
            "Native playlist queue: drag notification must be guarded by holder identity"
        }
    }
    val adapterType = start.originalMethod.parameterTypes[1].toString()
    // Native edit events inspect the adapter's wrapper through this row interface.
    // The native server-edit branch additionally requires playlistSetVideoId; our
    // renderer omits it, so normal account edits need no interception or replacement.
    val rowGetter = PlaylistEditEventsFingerprint.matchSingle().originalMethod.implementation!!.instructions
        .mapNotNull { it.getReference<MethodReference>() }
        .filter { it.returnType == videoType && it.parameterTypes.isEmpty() }
        .distinct().single()
    check(AccessFlags.INTERFACE.isSet(classDefBy(rowGetter.definingClass).accessFlags)) {
        "Native playlist queue: expected a native row-wrapper interface"
    }
    val adapter = mutableClassDefBy(adapterType)
    val itemsInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$ReorderItems;"
    adapter.interfaces.add(itemsInterface)
    adapter.methods.add(ImmutableMethod(adapterType, "patch_getSize", emptyList(), "I",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(2)).toMutable().apply {
        addInstructionsWithLabels(0, """
            invoke-interface {p0}, Ljava/util/List;->size()I
            move-result v0
            return v0
        """)
    })
    adapter.methods.add(ImmutableMethod(adapterType, "patch_getQueueRow",
        listOf(ImmutableMethodParameter("I", null, null)), "Ljava/lang/Object;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(4)).toMutable().apply {
        addInstructionsWithLabels(0, """
            invoke-interface {p0, p1}, Ljava/util/List;->get(I)Ljava/lang/Object;
            move-result-object v0
            instance-of v1, v0, ${rowGetter.definingClass}
            if-eqz v1, :missing
            check-cast v0, ${rowGetter.definingClass}
            invoke-interface {v0}, $rowGetter
            move-result-object v0
            return-object v0
            :missing
            const/4 v0, 0x0
            return-object v0
        """)
    })
    // Both callbacks first reject notifications for another holder. Enter on the
    // guarded branch, before start's visual update reuses p0/p1 as drawable locals.
    start.method.addInstructionsAtControlFlowLabel(start.instructionMatches[1].index,
        "invoke-static/range {p1 .. p3}, $EXTENSION_CLASS->onReorderStarted(Ljava/lang/Object;${itemsInterface}I)V")
    end.method.addInstructionsAtControlFlowLabel(end.instructionMatches[1].index,
        "invoke-static/range {p1 .. p4}, $EXTENSION_CLASS->onReorderEnded(Ljava/lang/Object;${itemsInterface}II)V")
    playlistDragUnbindFingerprint(holderType).matchSingle().method.addInstruction(0,
        "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onReorderHolderUnbound(Ljava/lang/Object;)V")
}
