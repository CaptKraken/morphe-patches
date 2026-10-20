/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.cloneMutable
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SAVE_ACTIONS = "Lapp/morphe/extension/youtube/patches/queue/QueueSaveActions;"

/** Keep native row factories and reactive adapters, supplying their omitted sheet owner. */
internal fun retainQueueSaveRowContext(context: BytecodePatchContext) = with(context) {
    val rows = QueueSaveSheetRowsFingerprint.matchSingle()
    val ownerField = rows.originalClassDef.fields.single { it.type == "Ljava/lang/Object;" }
    val originalRows = rows.method
    val rowInstructions = originalRows.implementation!!.instructions.toList()
    val factories = rowInstructions.mapNotNull { it.getReference<MethodReference>() }.filter {
        it.parameterTypes.size == 9 && it.parameterTypes[0] == "Ljava/util/List;" &&
                it.parameterTypes[1] == "Landroid/support/v7/widget/RecyclerView;" && it.returnType.startsWith("L")
    }.distinct()
    check(factories.size == 2 && factories.map { it.definingClass }.distinct().size == 1) {
        "Native playlist queue: expected the two native sheet row factories"
    }
    val factoryMethods = factories.map { reference ->
        // Copy the current native implementation, including any earlier patch edits.
        mutableClassDefBy(reference.definingClass).methods.single { it.toString() == reference.toString() }
    }
    val core = factoryMethods.single { method -> method.implementation!!.instructions.none {
        it.getReference<MethodReference>()?.toString() in factories.map { factory -> factory.toString() }
    } }
    val normalizer = factoryMethods.single { it != core }

    // The older adapter already supplies a native context provider. Reuse its
    // factory and the fragment's existing provider-list assignment contract.
    val legacyConstructor = rowInstructions.mapNotNull { it.getReference<MethodReference>() }.single {
        it.name == "<init>" && it.parameterTypes.size == 6 &&
                it.parameterTypes.takeLast(2) == listOf("Ljava/lang/Object;", "Lj$/util/Optional;")
    }
    val providerFactory = classDefBy(legacyConstructor.definingClass).methods.flatMap {
        it.implementation?.instructions?.mapNotNull { instruction -> instruction.getReference<MethodReference>() }
            ?: emptyList()
    }.filter {
        it.parameterTypes.size == 3 && it.parameterTypes[0] == "Ljava/lang/Object;" &&
                it.parameterTypes[2] == "Ljava/util/Map;" && it.returnType.startsWith("L")
    }.distinct().single()
    val providerBinding = Fingerprint(definingClass = rows.originalClassDef.type,
        custom = { method, _ -> method.implementation?.instructions?.any {
            it.getReference<MethodReference>()?.toString() == providerFactory.toString()
        } == true }).matchSingle().originalMethod.implementation!!.instructions.toList()
    val providerIndex = providerBinding.indexOfFirst {
        it.getReference<MethodReference>()?.toString() == providerFactory.toString()
    }
    val providersField = providerBinding.drop(providerIndex + 1).first { it.opcode == Opcode.IPUT_OBJECT }
        .getReference<FieldReference>()!!
    val copyProviders = classDefBy(providersField.type).methods.single {
        AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == providersField.type &&
                it.parameterTypes == listOf("Ljava/util/Collection;")
    }

    fun withOwner(method: Method, name: String) = ImmutableMethod(
        method.definingClass, name, method.parameters + ImmutableMethodParameter("Ljava/lang/Object;", null, null),
        method.returnType, method.accessFlags, method.annotations, method.hiddenApiRestrictions,
        method.implementation!!.let {
            // One appended reference and one register leave EVERY original parameter
            // in its original slot. Native branches/locals need no register rewriting.
            ImmutableMethodImplementation(it.registerCount + 1, it.instructions, it.tryBlocks, it.debugItems)
        }
    ).toMutable().also { mutableClassDefBy(method.definingClass).methods.add(it) }
    val scopedCore = withOwner(core, "patch_buildQueueSaveRows")
    val scopedNormalizer = withOwner(normalizer, "patch_decodeQueueSaveRows")
    val coreInstructions = scopedCore.implementation!!.instructions.toList()
    val build = coreInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.definingClass == providersField.definingClass && reference.parameterTypes.isEmpty() &&
            reference.returnType.startsWith("L")) index to (instruction as FiveRegisterInstruction) else null
    }.single()
    val builder = build.second.registerC
    val owner = scopedCore.findFreeRegister(build.first, builder)
    val temporary = scopedCore.findFreeRegister(build.first, builder, owner)
    check(owner <= 15 && temporary <= 15 && builder <= 15) {
        "Native playlist queue: row context injection requires low registers"
    }
    scopedCore.addInstructionsWithLabels(build.first, """
        move-object/from16 v$owner, p9
        invoke-static {v$owner}, $EXTENSION_CLASS->isQueueSaveSelection(Ljava/lang/Object;)Z
        move-result v$temporary
        if-eqz v$temporary, :native_row_context
        const/4 v$temporary, 0x0
        invoke-static {v$owner, v$temporary, v$temporary}, $providerFactory
        move-result-object v$owner
        iget-object v$temporary, v$builder, $providersField
        invoke-static {v$temporary, v$owner}, $SAVE_ACTIONS->withContextProvider(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
        move-result-object v$temporary
        invoke-static {v$temporary}, $copyProviders
        move-result-object v$temporary
        iput-object v$temporary, v$builder, $providersField
        :native_row_context
        nop
    """)
    val normalizeCall = scopedNormalizer.implementation!!.instructions.withIndex().single {
        it.value.getReference<MethodReference>()?.toString() == core.toString()
    }
    val normalizeRange = normalizeCall.value as RegisterRangeInstruction
    check(normalizeRange.startRegister + normalizeRange.registerCount == normalizer.implementation!!.registerCount) {
        "Native playlist queue: row normalizer must forward its original contiguous parameters"
    }
    scopedNormalizer.replaceInstruction(normalizeCall.index,
        "invoke-static/range {p0 .. p9}, $scopedCore")

    // Keep every original register and branch. A fresh contiguous block holds the
    // nine native arguments plus owner; cloneMutable preserves the original receiver.
    val arguments = originalRows.implementation!!.registerCount
    val updatedRows = originalRows.cloneMutable(additionalRegisters = 11)
    val sheet = mutableClassDefBy(originalRows.definingClass)
    check(sheet.methods.remove(originalRows)) { "Native playlist queue: sheet row method was replaced unexpectedly" }
    sheet.methods.add(updatedRows)
    updatedRows.implementation!!.instructions.withIndex().filter {
        it.value.getReference<MethodReference>()?.toString() in factories.map { factory -> factory.toString() }
    }.toList().asReversed().forEach { (index, instruction) ->
        val range = instruction as RegisterRangeInstruction
        check(range.registerCount == 9) { "Native playlist queue: row factory parameter width changed" }
        val scratch = updatedRows.findFreeRegister(index, *(range.startRegister until range.startRegister + 9).toList().toIntArray())
        check(scratch <= 15) { "Native playlist queue: sheet owner requires a low temporary register" }
        val target = if (instruction.getReference<MethodReference>()?.toString() == core.toString()) scopedCore else scopedNormalizer
        updatedRows.replaceInstruction(index, "invoke-static/range {v$arguments .. v${arguments + 9}}, $target")
        updatedRows.addInstructionsAtControlFlowLabel(index, (0 until 9).joinToString("\n") {
            "move-object/16 v${arguments + it}, v${range.startRegister + it}"
        } + """
            move-object/from16 v$scratch, p0
            iget-object v$scratch, v$scratch, $ownerField
            move-object/16 v${arguments + 9}, v$scratch
        """)
    }
}
