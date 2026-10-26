/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val LOADING_OWNER = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$PlaylistLoadingOwner;"
private const val LOADING_VIEW = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$PlaylistLoadingView;"

/** Reuse the playlist's own progress/content states without invoking its clear or fetch actions. */
internal fun installPlaylistLoading(context: BytecodePatchContext) = with(context) {
    val controllerType = PlaylistControllerConstructorFingerprint.originalClassDef.type
    val start = playlistLoadingStartFingerprint(controllerType).match()
    val frame = start.instructionMatches[0].instruction.getReference<FieldReference>()!!
    val showProgress = start.instructionMatches[1].instruction.getReference<MethodReference>()!!
    val list = classDefBy(controllerType).fields.single { it.type == frame.definingClass }
    val content = playlistLoadingContentFingerprint(frame).match()
    val showContentMatch = content.instructionMatches.last()
    val showContent = showContentMatch.instruction.getReference<MethodReference>()!!
    check(showContent != showProgress) { "Playlist loading and content must be distinct native states" }
    val frameRegister = content.instructionMatches[1].getInstruction<TwoRegisterInstruction>().registerA
    check(showContentMatch.getInstruction<FiveRegisterInstruction>().registerC == frameRegister) {
        "Playlist content state must belong to its list's loading frame"
    }

    mutableClassDefBy(controllerType).apply {
        interfaces.add(LOADING_OWNER)
        addBridgeMethod(
            "patch_getPlaylistLoadingView",
            emptyList(),
            LOADING_VIEW,
            1,
            """
                iget-object v0, p0, $list
                if-eqz v0, :done
                iget-object v0, v0, $frame

                :done
                return-object v0
            """
        )
    }
    mutableClassDefBy(PLAYLIST_LOADING_FRAME).apply {
        interfaces.add(LOADING_VIEW)
        addBridgeMethod(
            "patch_setPlaylistLoading",
            listOf("Z"),
            "V",
            0,
            """
                if-eqz p1, :content
                invoke-virtual {p0}, $showProgress
                return-void

                :content
                invoke-virtual {p0}, $showContent
                return-void
            """
        )
    }

    // Reopening or rebinding a panel can restore content during an outstanding
    // whole-list operation. Reapply progress after the native content transition.
    content.method.addInstruction(
        showContentMatch.index + 1,
        "invoke-static/range {v$frameRegister .. v$frameRegister}, " +
                "$EXTENSION_CLASS->onPlaylistLoadingViewBound($LOADING_VIEW)V"
    )
}
