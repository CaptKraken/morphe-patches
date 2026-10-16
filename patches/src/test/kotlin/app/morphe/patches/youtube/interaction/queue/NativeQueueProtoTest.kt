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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31i
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeQueueProtoTest {
    private val owner = "Ltest/RenamedMessage;"
    private fun schema(title: String = "renamedTitle", flags: Int = 1): NativeQueueProto {
        // Includes presence bits, a message list, a closed enum and a message oneof.
        val info = listOf(flags, 5, 1, 1, 1, 51779701, 5, 0, 1, 0,
            1, 0x1008, 0, 2, 27, 3, 0x180c, 1, 4, 60, 0, 51779701, 0x1008, 2)
            .joinToString("") { number ->
                buildString {
                    var remaining = number
                    while (remaining >= 0xd800) {
                        append(((remaining and 0x1fff) or 0xe000).toChar())
                        remaining = remaining ushr 13
                    }
                    append(remaining.toChar())
                }
            }
        val objects = listOf("union", "unionCase", "presence", title, "renamedList",
            ImmutableTypeReference("Ltest/RenamedChild;"), "renamedEnum",
            ImmutableFieldReference("Ltest/Verifier;", "changed", "Ltest/EnumVerifier;"),
            ImmutableTypeReference("Ltest/OneofMessage;"), "afterOneof")
        val instructions = mutableListOf(
            ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(info)),
            ImmutableInstruction31i(Opcode.CONST, 1, objects.size),
            ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 1, ImmutableTypeReference("[Ljava/lang/Object;")),
        )
        val code = instructions.toMutableList<com.android.tools.smali.dexlib2.iface.instruction.Instruction>()
        objects.forEachIndexed { index, value ->
            code += ImmutableInstruction31i(Opcode.CONST, 3, index)
            code += when (value) {
                is String -> ImmutableInstruction21c(Opcode.CONST_STRING, 2, ImmutableStringReference(value))
                is ImmutableTypeReference -> ImmutableInstruction21c(Opcode.CONST_CLASS, 2, value)
                is ImmutableFieldReference -> ImmutableInstruction21c(Opcode.SGET_OBJECT, 2, value)
                else -> error("Unknown test schema object")
            }
            code += ImmutableInstruction23x(Opcode.APUT_OBJECT, 2, 1, 3)
        }
        code += ImmutableInstruction35c(Opcode.INVOKE_STATIC, 3, 4, 0, 1, 0, 0,
            ImmutableMethodReference(owner, "newMessageInfo",
                listOf("Lcom/google/protobuf/MessageLite;", "Ljava/lang/String;", "[Ljava/lang/Object;"),
                "Ljava/lang/Object;"))
        val method = ImmutableMethod(owner, "dynamicMethod", emptyList(), "Ljava/lang/Object;", 0,
            null, null, ImmutableMethodImplementation(5, code, null, null))
        val fields = listOf(title to "Ljava/lang/String;", "renamedList" to "Ljava/util/List;", "renamedEnum" to "I", "afterOneof" to "Ljava/lang/String;")
            .map { (name, type) -> ImmutableField(owner, name, type, 0, null, null, null) }
        return NativeQueueProto(ImmutableClassDef(owner, 0, "Ljava/lang/Object;", emptyList(), null,
            null, fields, listOf(method)))
    }

    @Test fun `wire fields resolve through renamed metadata rather than fixed DEX names`() {
        assertEquals("renamedTitle", schema().field(1).name)
        assertEquals("aCompletelyDifferentName", schema("aCompletelyDifferentName").field(1).name)
        assertEquals("renamedEnum", schema().field(3).name)
    }

    @Test fun `list element types survive presence enum and oneof metadata`() {
        val schema = schema()
        assertEquals("afterOneof", schema.field(51779701).name)
        assertEquals("renamedList", schema.field(2).name)
        assertEquals("Ltest/RenamedChild;", schema.elementType(2))
        assertFailsWith<PatchException> { schema.field(4) } // Not an independently stored field.
        assertFailsWith<PatchException> { schema.elementType(1) }
        assertFailsWith<PatchException> { schema.field(99) }
    }

    @Test fun `closed enum verifiers also consume an object in editions schemas`() {
        val schema = schema(flags = 4)
        assertEquals("renamedEnum", schema.field(3).name)
        assertEquals("afterOneof", schema.field(51779701).name)
        assertEquals("Ltest/RenamedChild;", schema.elementType(2))
        assertFailsWith<PatchException> { schema.field(4) }
    }

    @Test fun `schema constants follow the message info branch instead of unrelated return paths`() {
        val code = listOf(
            ImmutableInstruction31i(Opcode.CONST, 1, 1),
            ImmutableInstruction31i(Opcode.CONST, 2, 2),
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 7, 0, 0, 0, 0,
                ImmutableMethodReference("Ljava/lang/Enum;", "ordinal", emptyList(), "I")),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction22t(Opcode.IF_EQ, 0, 2, 6),
            ImmutableInstruction31i(Opcode.CONST, 1, 7),
            ImmutableInstruction11x(Opcode.RETURN, 1),
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 1, 0, 0, 0, 0,
                ImmutableMethodReference(owner, "probe", listOf("I"), "V")),
            ImmutableInstruction11x(Opcode.RETURN, 1),
        )
        val method = ImmutableMethod(owner, "dynamicMethod", emptyList(), "I", 0,
            null, null, ImmutableMethodImplementation(8, code, null, null))
        assertEquals(listOf(listOf(1)), method.constantCalls("probe").map { it.arguments })
    }

    @Test fun `extension result follows moves to its store and ignores unrelated static writes`() {
        val method = ImmutableMethod(owner, "<clinit>", emptyList(), "V", 8,
            null, null, MutableMethodImplementation(3)).toMutable()
        method.addInstructions(0, """
            const/4 v0, 0x1
            invoke-static {v0}, Ltest/Proto;->extension(I)Ljava/lang/Object;
            move-result-object v0
            const/4 v1, 0x0
            sput-object v1, $owner->unrelated:Ljava/lang/Object;
            move-object v2, v0
            sput-object v2, $owner->actual:Ljava/lang/Object;
            return-void
        """)
        val call = method.constantCalls("extension").single()
        assertEquals(listOf(1), call.arguments)
        assertEquals("actual", call.storedField!!.name)
    }

    @Test fun `an unsupported write cannot reuse a stale constant or extension result`() {
        val method = ImmutableMethod(owner, "<clinit>", emptyList(), "V", 8,
            null, null, MutableMethodImplementation(2)).toMutable()
        method.addInstructions(0, """
            const/4 v0, 0x1
            iget v0, v1, Ltest/Input;->unknown:I
            invoke-static {v0}, Ltest/Proto;->extension(I)Ljava/lang/Object;
            move-result-object v0
            iget-object v0, v1, Ltest/Input;->replacement:Ljava/lang/Object;
            sput-object v0, $owner->unrelated:Ljava/lang/Object;
            return-void
        """)
        val call = method.constantCalls("extension").single()
        assertEquals(listOf(null), call.arguments)
        assertEquals(null, call.storedField)
    }

}
