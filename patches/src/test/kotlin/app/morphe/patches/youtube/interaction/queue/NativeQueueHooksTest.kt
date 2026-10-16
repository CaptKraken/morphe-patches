/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeQueueHooksTest {
    @Test fun `mode hooks preserve forwarding bodies when parameter registers need cloning`() {
        verifyForwarders(2)
    }

    @Test fun `mode hooks retain earlier patches and use an available local register`() {
        verifyForwarders(3)
    }

    private fun verifyForwarders(registers: Int) {
        for ((parameter, callback) in listOf("I" to "onRepeatRequested", "Z" to "onShuffleRequested")) {
            val type = "Ltest/Navigator;"
            val method = ImmutableMethod(type, "forward", listOf(ImmutableMethodParameter(parameter, null, null)),
                "V", AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(registers)).toMutable()
            method.addInstructions(0, """
                invoke-static {p0, p1}, Ltest/OtherPatch;->observe(Ljava/lang/Object;$parameter)V
                iget-object p0, p0, $type->delegate:Ltest/Delegate;
                invoke-interface {p0, p1}, Ltest/Delegate;->forward($parameter)V
                return-void
            """)
            val original = method.implementation!!.instructions.toList()
            val owner = ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(),
                null, null, emptyList(), listOf(method)).toMutable()
            interceptQueuePlaybackMode(owner, owner.methods.single(), parameter, callback)
            val implementation = owner.methods.single().implementation!!
            val instructions = implementation.instructions.toList()
            val entry = instructions.first() as FiveRegisterInstruction
            val receiver = implementation.registerCount - 2
            assertEquals(receiver, entry.registerC)
            assertEquals(receiver + 1, entry.registerD)
            assertTrue((entry as ReferenceInstruction).reference.toString().endsWith(
                "NativeQueuePatch;->$callback(Ljava/lang/Object;$parameter)Z"))
            assertTrue((instructions[1] as OneRegisterInstruction).registerA < receiver,
                "The result must not overwrite the receiver or argument")
            assertEquals(Opcode.IF_EQZ, instructions[2].opcode)
            assertEquals(instructions[2].codeUnits + instructions[3].codeUnits,
                (instructions[2] as OffsetInstruction).codeOffset,
                "Unclaimed requests must branch past the early return to the preserved body")
            assertEquals(Opcode.RETURN_VOID, instructions[3].opcode)
            // Parameter cloning may prepend copies, but cannot replace any existing instructions.
            val preserved = instructions.takeLast(original.size)
            assertEquals(original.map { it.opcode }, preserved.map { it.opcode })
            assertEquals(original.filterIsInstance<ReferenceInstruction>().map { it.reference.toString() },
                preserved.filterIsInstance<ReferenceInstruction>().map { it.reference.toString() })
            val priorHook = preserved.first() as FiveRegisterInstruction
            assertEquals(registers - 2, priorHook.registerC)
            assertEquals(registers - 1, priorHook.registerD)
        }
    }
}
