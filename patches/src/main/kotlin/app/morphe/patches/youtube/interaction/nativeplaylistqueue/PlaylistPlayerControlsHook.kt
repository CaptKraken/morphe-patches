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
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/** Keep native listeners and visibility, including controls that bypass the navigator for history. */
internal fun installPlaylistPlayerControls(
    context: BytecodePatchContext,
    navigatorType: String,
    enumType: String,
    wrapperType: String
) = with(context) {
    val availability = listOf("NEXT", "PREVIOUS").map { intent ->
        val enumField = playlistNavigationIntentFingerprint(enumType, intent).matchSingle()
            .instructionMatches.last().instruction.getReference<FieldReference>()!!
        val request = playlistNavigationSingletonFingerprint(wrapperType, enumField).matchSingle()
            .instructionMatches.last().instruction.getReference<FieldReference>()!!
        request to playlistControlAvailabilityFingerprint(navigatorType, request).matchSingle()
    }
    val controlsType = availability.first().second.originalClassDef.type
    check(availability.all { it.second.originalClassDef.type == controlsType }) {
        "Native playlist queue: Next/Previous must share their native controls owner"
    }
    val controls = mutableClassDefBy(controlsType)
    val controlsInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$PlayerControls;"
    val navigatorField = controls.fields.single { it.type == navigatorType }
    controls.interfaces.add(controlsInterface)
    fun bridge(name: String, returnType: String, body: String) {
        controls.methods.add(ImmutableMethod(controlsType, name, emptyList(), returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(2)).toMutable().apply { addInstructionsWithLabels(0, body) })
    }
    bridge("patch_getNavigator", "Ljava/lang/Object;", """
        iget-object v0, p0, $navigatorField
        return-object v0
    """)
    // Recompute both cached enablement values, even when native history delegates are present.
    bridge("patch_refreshNavigation", "V", availability.joinToString("\n") { (_, match) ->
        "invoke-direct {p0}, ${match.originalMethod}"
    } + "\nreturn-void")
    val constructor = controls.methods.single { it.name == "<init>" && navigatorType in it.parameterTypes }
    val returns = constructor.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
    check(returns.size == 1) { "Native playlist queue: expected one initialized controls constructor exit" }
    constructor.addInstruction(returns.single().index,
        "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onPlayerControlsCreated($controlsInterface)V")
    availability.forEachIndexed { direction, (request, match) ->
        val method = match.method
        val compare = match.instructionMatches.last().instruction as TwoRegisterInstruction
        val cachedMatch = match.instructionMatches[match.instructionMatches.lastIndex - 1]
        val cached = cachedMatch.instruction as TwoRegisterInstruction
        check(compare.registerA == cached.registerA || compare.registerB == cached.registerA) {
            "Native playlist queue: control availability must compare the cached and computed values"
        }
        val originalRegister = if (compare.registerA == cached.registerA) compare.registerB else compare.registerA
        val insertIndex = cachedMatch.index
        val freeRegister = method.findFreeRegister(insertIndex)
        method.addInstructionsAtControlFlowLabel(insertIndex, """
            const/4 v$freeRegister, ${if (direction == 0) 1 else 0}
            invoke-static {p0, v$freeRegister, v$originalRegister}, $EXTENSION_CLASS->playerControlAvailability(${controlsInterface}ZZ)Z
            move-result v$originalRegister
        """)
        var press = playlistControlPressFingerprint(controlsType, request).matchSingle().method
        if (press.implementation!!.registerCount <= 1) press = press.cloneParameters()
        // The delegate read joins both paths of the optional native logging callback.
        // Intercept here so logging runs, but a local queue cannot fall into history/restart.
        val delegateField = match.instructionMatches.first().instruction.getReference<FieldReference>()!!
        val pressIndex = press.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.IGET_OBJECT && it.getReference<FieldReference>() == delegateField
        }
        check(pressIndex >= 0) { "Native playlist queue: player control delegate was not found" }
        val resultRegister = press.findFreeRegister(pressIndex)
        val callback = if (direction == 0) "onNextPressed" else "onPreviousPressed"
        press.addInstructionsAtControlFlowLabel(pressIndex, """
            invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->$callback($controlsInterface)Z
            move-result v$resultRegister
            if-eqz v$resultRegister, :native_press
            return-void
            :native_press
            nop
        """)
    }
}
