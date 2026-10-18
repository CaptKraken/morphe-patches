/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.loop

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val EXTENSION_CLASS = "Lapp/morphe/extension/youtube/patches/LoopVideoPatch;"
private const val EXTENSION_SLEEP_TIMER_INTERFACE =
    $$"Lapp/morphe/extension/youtube/patches/LoopVideoPatch$SleepTimerController;"

/** Share the existing timer bridge without selecting the Loop video feature. */
internal val sleepTimerHookPatch = bytecodePatch(
    description = "Exposes the native end-of-video sleep timer state."
) {
    dependsOn(sharedExtensionPatch)

    execute {
        SleepTimerCancelMethodFingerprint.method.apply {
            val stateFieldGetIndex = indexOfFirstInstructionOrThrow(Opcode.IGET_OBJECT)
            val stateFieldRef = getInstruction<ReferenceInstruction>(stateFieldGetIndex).reference as FieldReference

            SleepTimerVideoEndedEventFingerprint.method.apply {
                val sgetInstructionIndex = indexOfFirstInstructionOrThrow(Opcode.SGET_OBJECT)
                val endOfVideoEnumFieldRef = getInstruction<ReferenceInstruction>(sgetInstructionIndex).reference as FieldReference

                SleepTimerCancelMethodFingerprint.classDef.apply {
                    interfaces.add(EXTENSION_SLEEP_TIMER_INTERFACE)

                    methods.add(
                        ImmutableMethod(
                            type,
                            "patch_isSetToEndOfVideo",
                            listOf(),
                            "Z",
                            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                            null,
                            null,
                            MutableMethodImplementation(3),
                        ).toMutable().apply {
                            addInstructionsWithLabels(
                                0,
                                """
                                    iget-object v0, p0, $stateFieldRef
                                    sget-object v1, $endOfVideoEnumFieldRef
                                    if-ne v0, v1, :false
                                    const/4 v0, 1
                                    return v0
                                    :false
                                    const/4 v0, 0
                                    return v0
                                """
                            )
                        }
                    )
                }
            }
        }

        SleepTimerConstructorFingerprint.matchAll().forEach { match ->
            match.method.apply {
                addInstruction(
                    match.instructionMatches.first().index + 1,
                    "invoke-static { p0 }, $EXTENSION_CLASS->" +
                            "setSleepTimerController($EXTENSION_SLEEP_TIMER_INTERFACE)V"
                )
            }
        }
    }
}
