/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Keep native repeat handling; turn only the owned queue's shuffle into a list command. */
internal fun installPlaylistRepeat(
    context: BytecodePatchContext,
    navigatorType: String
) = with(context) {
    val binding = PlaylistRepeatBindingFingerprint.matchSingle()
    val headerType = binding.originalClassDef.type
    val headerInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$RepeatHeader;"
    fun bridge(owner: String, name: String, parameters: List<String>, returnType: String, locals: Int, body: String) {
        mutableClassDefBy(owner).methods.add(ImmutableMethod(owner, name,
            parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(locals + parameters.size + 1)).toMutable().apply {
            addInstructionsWithLabels(0, body)
        })
    }
    val header = mutableClassDefBy(headerType)
    val navigator = header.fields.single { it.type == navigatorType }
    val panel = header.fields.single { it.type == binding.originalMethod.parameterTypes.first() }
    val repeatView = header.fields.single {
        it.type == "Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;"
    }
    val shuffleMatch = PlaylistShuffleViewFingerprint.matchSingle()
    val shuffleView = shuffleMatch.instructionMatches.last().instruction.getReference<FieldReference>()!!
    check(shuffleView.definingClass == headerType) {
        "Native playlist queue: shuffle and repeat must share the native header"
    }
    val capabilities = PlaylistRepeatCapabilitiesFingerprint.matchSingle()
    check(capabilities.originalMethod == binding.originalMethod) {
        "Native playlist queue: repeat capabilities and state must share their native binder"
    }
    val repeatAll = capabilities.instructionMatches[0].instruction.getReference<FieldReference>()!!
    val repeatOne = capabilities.instructionMatches[2].instruction.getReference<FieldReference>()!!
    check(repeatAll != repeatOne && repeatAll.definingClass == headerType && repeatOne.definingClass == headerType) {
        "Native playlist queue: repeat capabilities must be distinct fields on the bound header"
    }
    header.interfaces.add(headerInterface)
    bridge(headerType, "patch_getNavigator", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $navigator
        return-object v0
    """)
    bridge(headerType, "patch_getPanel", emptyList(), "Ljava/lang/Object;", 1, """
        iget-object v0, p0, $panel
        return-object v0
    """)
    bridge(headerType, "patch_enableRepeat", emptyList(), "V", 2, """
        const/4 v0, 0x1
        iput-boolean v0, p0, $repeatAll
        iput-boolean v0, p0, $repeatOne
        iget-object v1, p0, $repeatView
        invoke-virtual {v1, v0}, Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;->setEnabled(Z)V
        return-void
    """)
    bridge(headerType, "patch_setShuffleAvailable", listOf("Z"), "V", 1, """
        iget-object v0, p0, $shuffleView
        invoke-virtual {v0, p1}, Landroid/widget/ImageView;->setEnabled(Z)V
        const/4 p1, 0x0
        invoke-virtual {v0, p1}, Landroid/widget/ImageView;->setSelected(Z)V
        return-void
    """)
    // Preserve the original listener and account-playlist command branch. Only a
    // header bound to our exact local panel consumes this fallback toggle.
    val shufflePress = playlistShufflePressFingerprint(navigator, shuffleView).matchSingle()
    val pressIndex = shufflePress.instructionMatches.first().index
    val headerRegister = (shufflePress.instructionMatches.first().instruction as TwoRegisterInstruction).registerB
    val press = shufflePress.method
    val resultRegister = press.findFreeRegister(pressIndex)
    press.addInstructionsAtControlFlowLabel(pressIndex, """
        invoke-static/range {v$headerRegister .. v$headerRegister}, $EXTENSION_CLASS->onQueueShuffle($headerInterface)Z
        move-result v$resultRegister
        if-eqz v$resultRegister, :native_shuffle
        return-void
        :native_shuffle
        nop
    """)
    // The binder reuses p0 on some exits. Preserve its owner before observing all returns.
    binding.method.cloneParameters().apply {
        findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
            addInstructionsAtControlFlowLabel(index,
                "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onRepeatHeaderBound($headerInterface)V")
        }
    }
}
