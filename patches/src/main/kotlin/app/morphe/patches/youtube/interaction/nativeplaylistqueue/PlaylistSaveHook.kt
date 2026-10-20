/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SAVE_ENDPOINT =
    $$"Lapp/morphe/extension/youtube/patches/queue/QueueSaveActions$NativeEndpoint;"

/** Expand the native save resolvers' input, independently of the command router used. */
internal fun installQueueSaveCommands(context: BytecodePatchContext, endpointType: String) = with(context) {
    // The in-order router is only one of six native paths that invoke resolvers.
    // Discover the actual save command consumers and hook their context-bearing
    // entry, before authentication callbacks or creation can discard that context.
    val resolvers = listOf(
        60666189, // PlaylistEditEndpoint: existing destination.
        85650004, // CreatePlaylistDialogEndpoint: name/privacy and selection.
        124747980 // CreatePlaylistEndpoint: direct submission from a native form.
    ).map { extensionNumber ->
        val commandType = queueSaveEndpointFingerprint(extensionNumber).matchSingle().originalClassDef.type
        val extensionType = classDefBy(commandType).fields.single {
            AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags) &&
                    it.type != commandType
        }.type
        val consumer = queueSaveResolverFingerprint(commandType, endpointType, extensionType).matchSingle()
        queueSaveResolverEntryFingerprint(consumer.originalClassDef.type, endpointType).matchSingle()
    }
    check(resolvers.map { it.originalMethod.definingClass }.distinct().size == 3) {
        "Native playlist queue: save commands must resolve through three distinct native consumers"
    }

    val endpointDefault = classDefBy(endpointType).fields.single {
        it.type == endpointType && AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val parser = generateSequence(classDefBy(endpointType)) { type ->
        type.superclass?.let { classDefByOrNull(it) }
    }.firstNotNullOfOrNull { type ->
        Fingerprint(
            definingClass = type.type,
            name = "parseFrom",
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            returnType = type.type,
            custom = { method, _ -> AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                    AccessFlags.STATIC.isSet(method.accessFlags) }
        ).originalMethodOrNull
    } ?: error("Native playlist queue: native save command parser is missing")
    val endpoint = mutableClassDefBy(endpointType)
    endpoint.interfaces.add(SAVE_ENDPOINT)
    endpoint.methods.add(ImmutableMethod(
        endpointType, "patch_parseQueueSaveEndpoint", listOf(ImmutableMethodParameter("[B", null, null)),
        "Lcom/google/protobuf/MessageLite;", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        null, null, MutableMethodImplementation(4)
    ).toMutable().apply {
        addInstructionsWithLabels(0, """
            sget-object v0, $endpointDefault
            invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
            move-result-object v1
            invoke-static {v0, p1, v1}, $parser
            move-result-object v0
            return-object v0
        """)
    })

    resolvers.forEach { resolver ->
        check(resolver.originalMethod.p0Register + 1 <= 255) {
            "Native playlist queue: save resolver endpoint parameter exceeds the move-result register range"
        }
        // p1/p2 are adjacent single-word references. Reuse p1 for the rewritten
        // endpoint; no local register, receiver, or native authentication state changes.
        // A rejected local action returns to the original caller's cleanup path.
        resolver.method.addInstructionsAtControlFlowLabel(0, """
            invoke-static/range {p1 .. p2}, $EXTENSION_CLASS->onQueueSaveCommand(Lcom/google/protobuf/MessageLite;Ljava/util/Map;)Lcom/google/protobuf/MessageLite;
            move-result-object p1
            if-nez p1, :native_save_resolver
            return-void
            :native_save_resolver
            check-cast p1, $endpointType
        """)
    }
    retainQueueSaveContext(context)
    retainQueueSaveRowContext(context)
}

/** Keep the modern contents in the native container that preserves their selection tag. */
private fun retainQueueSaveContext(context: BytecodePatchContext) = with(context) {
    val sheet = QueueSaveSheetPresentationFingerprint.matchSingle()
    val instructions = sheet.originalMethod.implementation!!.instructions.toList()
    val discardedTagCalls = instructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.toString() != "Lj$/util/Optional;->orElse(Ljava/lang/Object;)Ljava/lang/Object;" ||
            instructions.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT) return@mapIndexedNotNull null
        index to (instruction as FiveRegisterInstruction)
    }
    check(discardedTagCalls.size == 1) { "Native playlist queue: expected one discarded sheet tag" }
    val (discardedTag, tagCall) = discardedTagCalls.single()
    val addresses = instructions.runningFold(0) { address, instruction -> address + instruction.codeUnits }
    val branches = instructions.mapIndexedNotNull { index, instruction ->
        if (index >= discardedTag || instruction.opcode != Opcode.IF_EQZ || index < 2 ||
            instructions[index - 1].opcode != Opcode.MOVE_RESULT ||
            instructions[index - 2].getReference<MethodReference>()?.let {
                it.returnType == "Z" && it.parameterTypes.isEmpty()
            } != true) return@mapIndexedNotNull null
        val target = addresses.indexOf(addresses[index] + (instruction as OffsetInstruction).codeOffset)
        if (target <= discardedTag) null else index to target
    }
    check(branches.size == 1) { "Native playlist queue: expected one context-dropping sheet branch" }
    val (branch, taggedPresentation) = branches.single()
    // The omitted orElse uses the fourth Optional (endpoint tag), copied from p5
    // before this branch. Require that exact reaching definition, rather than
    // assuming an arbitrary low register still holds the caller's tag.
    val tagRegister = tagCall.registerC
    val tagDefinition = instructions.take(branch).lastOrNull {
        it.opcode.setsRegister() && (it as? OneRegisterInstruction)?.registerA == tagRegister
    } as? TwoRegisterInstruction
    check(tagDefinition != null && tagDefinition.opcode == Opcode.MOVE_OBJECT_FROM16 &&
            tagDefinition.registerB == sheet.originalMethod.p0Register + 5) {
        "Native playlist queue: transient sheet did not discard the original endpoint tag"
    }
    val free = sheet.method.findFreeRegister(branch, tagRegister)
    check(free <= 15 && tagRegister <= 15) { "Native playlist queue: sheet context guard needs low registers" }
    sheet.method.addInstructionsAtControlFlowLabel(branch + 1, """
        const/4 v$free, 0x0
        invoke-virtual {v$tagRegister, v$free}, Lj$/util/Optional;->orElse(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v$free
        invoke-static {v$free}, $EXTENSION_CLASS->isQueueSaveSelection(Ljava/lang/Object;)Z
        move-result v$free
        if-nez v$free, :queue_save_tagged_sheet
    """, ExternalLabel("queue_save_tagged_sheet", sheet.method.getInstruction(taggedPresentation)))
}
