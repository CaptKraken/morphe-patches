/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeQueueDiscoveryTest {
    private val owner = "Ltest/Model;"
    private val text = "Ljava/lang/String;"

    @Test fun `constructor roles follow parameters despite reversed stores and unrelated constants`() {
        val constructor = ImmutableMethod(owner, "<init>",
            listOf(text, text, "I", "I").map { ImmutableMethodParameter(it, null, null) }, "V",
            AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(7)).toMutable()
        constructor.addInstructions(0, """
            const/4 v0, 0x0
            iput v0, p0, $owner->unrelated:I
            iput p4, p0, $owner->to:I
            iput-object p2, p0, $owner->playlist:$text
            iput p3, p0, $owner->from:I
            iput-object p1, p0, $owner->video:$text
            return-void
        """)
        assertEquals(0, queueConstructorParameter(constructor, ImmutableFieldReference(owner, "video", text)))
        assertEquals(1, queueConstructorParameter(constructor, ImmutableFieldReference(owner, "playlist", text)))
        assertEquals("from", queueConstructorField(constructor, 2).name)
        assertEquals("to", queueConstructorField(constructor, 3).name)
    }

    @Test fun `identity assignment follows register moves instead of the order of String reads`() {
        val method = ImmutableMethod(owner, "refresh", listOf(ImmutableMethodParameter("Ltest/Response;", null, null)),
            "V", AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(4)).toMutable()
        method.addInstructions(0, """
            iget-object v0, p1, Ltest/Response;->playlist:$text
            iget-object v0, p1, Ltest/Response;->video:$text
            move-object v1, v0
            iget-object v0, p1, Ltest/Response;->unrelated:$text
            iput-object v1, p0, $owner->selected:$text
            return-void
        """)
        assertEquals("video", queueSourceField(method, 4, 1).name)
        method.addInstructions(4, "const/4 v1, 0x0")
        assertFailsWith<PatchException> { queueSourceField(method, 5, 1) }
    }

    @Test fun `constructor parameter roles account for wide arguments`() {
        val constructor = ImmutableMethod(owner, "<init>",
            listOf("J", text).map { ImmutableMethodParameter(it, null, null) }, "V",
            AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(4)).toMutable()
        constructor.addInstructions(0, """
            iput-object p3, p0, $owner->video:$text
            return-void
        """)
        assertEquals(1, queueConstructorParameter(constructor, ImmutableFieldReference(owner, "video", text)))
    }

    @Test fun `range constructor calls retain every argument register`() {
        val call = ImmutableInstruction3rc(Opcode.INVOKE_DIRECT_RANGE, 4, 8,
            ImmutableMethodReference(owner, "<init>", List(7) { text }, "V"))
        assertEquals((4..11).toList(), queueInvokeRegisters(call))
    }
}
