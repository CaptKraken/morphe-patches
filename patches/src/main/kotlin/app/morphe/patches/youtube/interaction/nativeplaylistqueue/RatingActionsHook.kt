/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.matchSingle
import app.morphe.patches.youtube.shared.WatchNextResponseParserFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/** Observe the parsed response before view filters, including prefetched responses. */
internal fun installRatingCapture(context: BytecodePatchContext) = with(context) {
    // Hide related videos and Hide video action buttons also use this parser.
    WatchNextResponseParserFingerprint.clearMatch()
    val match = WatchNextResponseParserFingerprint.matchSingle()
    val responseCast = match.instructionMatches.single { it.instruction.opcode == Opcode.CHECK_CAST }
    val responseRegister = match.method.getInstruction<OneRegisterInstruction>(responseCast.index).registerA
    match.method.addInstruction(responseCast.index + 1,
        "invoke-static/range {v$responseRegister .. v$responseRegister}, $EXTENSION_CLASS->onWatchNextResponse(Lcom/google/protobuf/MessageLite;)V")
}
