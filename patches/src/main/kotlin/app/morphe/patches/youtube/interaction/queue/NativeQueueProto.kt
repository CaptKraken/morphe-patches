/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal data class QueueConstantCall(val arguments: List<Any?>, var storedField: FieldReference? = null)

/** Constants and result stores in generated initializers, read without loading Android classes. */
internal fun Method.constantCalls(name: String): List<QueueConstantCall> {
    val registers = mutableMapOf<Int, Any?>()
    val calls = mutableListOf<QueueConstantCall>()
    val code = implementation!!.instructions.toList()
    val offsets = code.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }
    val byOffset = offsets.withIndex().associate { it.value to it.index }
    val visited = mutableSetOf<Int>()
    var index = 0
    var result: Any? = null
    while (index < code.size) {
        if (!visited.add(index)) throw PatchException("Loop in native protobuf initializer: $this")
        val instruction = code[index]
        fun branch(offset: Int) {
            index = byOffset.getValue(offsets[index] + offset)
        }
        val one = instruction as? OneRegisterInstruction
        val two = instruction as? TwoRegisterInstruction
        val reference = instruction.getReference<Reference>()
        when {
            instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) ->
                registers[one!!.registerA] = (reference as StringReference).string
            instruction.opcode == Opcode.CONST_CLASS -> registers[one!!.registerA] = reference
            instruction.opcode.name.startsWith("const") && instruction is WideLiteralInstruction ->
                registers[one!!.registerA] = instruction.wideLiteral.toInt()
            instruction.opcode == Opcode.SGET_OBJECT -> registers[one!!.registerA] = reference
            instruction.opcode == Opcode.SPUT_OBJECT ->
                (registers[one!!.registerA] as? QueueConstantCall)?.storedField = reference as FieldReference
            instruction.opcode == Opcode.NEW_ARRAY ->
                registers[two!!.registerA] = arrayOfNulls<Any>(registers[two.registerB] as Int)
            instruction.opcode == Opcode.APUT_OBJECT -> {
                val put = instruction as ThreeRegisterInstruction
                val array = registers[put.registerB] as? Array<*>
                    ?: throw PatchException("Non-constant protobuf metadata array: $this")
                @Suppress("UNCHECKED_CAST")
                (array as Array<Any?>)[registers[put.registerC] as Int] = registers[put.registerA]
            }
            instruction.opcode.name.startsWith("move-result") -> registers[one!!.registerA] = result
            instruction.opcode.name.startsWith("move") && two != null ->
                registers[two.registerA] = registers[two.registerB]
            instruction.opcode == Opcode.CHECK_CAST -> Unit
            // An unsupported write invalidates its previous constant. Keeping the old value
            // could bind an unrelated extension or decode the wrong schema without failing.
            instruction.opcode.setsRegister() && one != null -> registers.remove(one.registerA)
        }
        if (reference is MethodReference) {
            // BUILD_MESSAGE_INFO is ordinal 2 in Protobuf Lite's MethodToInvoke enum.
            result = if (this.name == "dynamicMethod" && reference.name == "ordinal") 2 else null
            if (reference.name == name) {
                val arguments = queueInvokeRegisters(instruction).map { register ->
                    val value = registers[register]
                    if (value is Array<*>) value.copyOf() else value
                }
                val call = QueueConstantCall(arguments)
                calls += call
                result = call
            }
        }
        when (instruction.opcode) {
            Opcode.IF_EQ, Opcode.IF_NE, Opcode.IF_EQZ, Opcode.IF_NEZ -> {
                val left = registers[one!!.registerA]
                val right = if (two == null) 0 else registers[two.registerB]
                if (left !is Int || right !is Int) {
                    throw PatchException("Non-constant branch in native protobuf initializer: $this")
                }
                val equal = left == right
                if (equal == (instruction.opcode == Opcode.IF_EQ || instruction.opcode == Opcode.IF_EQZ)) {
                    branch((instruction as OffsetInstruction).codeOffset)
                    continue
                }
            }
            Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH -> {
                val value = registers[one!!.registerA] as Int
                val payloadIndex = byOffset.getValue(offsets[index] + (instruction as OffsetInstruction).codeOffset)
                val payload = code[payloadIndex] as SwitchPayload
                val element = payload.switchElements.singleOrNull { it.key == value }
                if (element != null) {
                    branch(element.offset)
                    continue
                }
            }
            Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32 -> {
                branch((instruction as OffsetInstruction).codeOffset)
                continue
            }
            Opcode.RETURN_VOID, Opcode.RETURN_OBJECT, Opcode.RETURN -> break
            else -> Unit
        }
        index++
    }
    return calls
}

/**
 * Protobuf Lite's RawMessageInfo describes fields by wire number and supplies their actual
 * DEX names in an object array. Resolve that schema instead of pinning obfuscated field names.
 * Only singular/repeated fields used by this patch are returned; oneofs are skipped correctly.
 */
internal class NativeQueueProto(private val owner: ClassDef) {
    data class Member(val field: FieldReference, val elementType: String?)

    private val members: Map<Int, Member> = run {
        val info = owner.methods.single { it.name == "dynamicMethod" }
            .constantCalls("newMessageInfo").single().arguments
        val encoded = info[1] as String
        val objects = info[2] as Array<*>
        var cursor = 0
        fun next(): Int {
            var value = 0
            var shift = 0
            while (true) {
                val char = encoded.getOrNull(cursor++)?.code
                    ?: throw PatchException("Truncated native protobuf schema: ${owner.type}")
                if (char < 0xd800) return value or (char shl shift)
                value = value or ((char and 0x1fff) shl shift)
                shift += 13
                if (shift > 26) throw PatchException("Invalid native protobuf schema: ${owner.type}")
            }
        }
        val proto2 = next() and 1 != 0
        val count = next()
        if (count == 0) return@run emptyMap()
        val oneofs = next()
        val presence = next()
        repeat(6) { next() } // min, max, table size, maps, repeated, initialization checks.
        var objectIndex = oneofs * 2 + presence
        buildMap {
            repeat(count) {
                val number = next()
                val flags = next()
                val kind = flags and 0xff
                if (kind >= 51) {
                    next() // Oneof index. Names are in the leading oneof pairs.
                    if (kind - 51 in listOf(9, 17) || (kind - 51 == 12 && (proto2 || flags and 0x800 != 0))) objectIndex++
                } else {
                    val name = objects[objectIndex++] as String
                    val field = owner.fields.single { it.name == name }
                    val element = if (kind in listOf(27, 49)) {
                        (objects[objectIndex++] as TypeReference).type
                    } else null
                    if (kind in listOf(12, 30, 44) && (proto2 || flags and 0x800 != 0)) objectIndex++
                    if (kind == 50) {
                        objectIndex++ // Map default entry.
                        if (flags and 0x800 != 0) objectIndex++ // Closed enum verifier.
                    }
                    if (flags and 0x1000 != 0) next() // Presence bit.
                    put(number, Member(field, element))
                }
            }
        }
    }

    fun field(number: Int): FieldReference = members[number]?.field
        ?: throw PatchException("Native queue protobuf field $number missing in ${owner.type}")

    fun elementType(number: Int): String = members[number]?.elementType
        ?: throw PatchException("Native queue protobuf list $number missing in ${owner.type}")
}
