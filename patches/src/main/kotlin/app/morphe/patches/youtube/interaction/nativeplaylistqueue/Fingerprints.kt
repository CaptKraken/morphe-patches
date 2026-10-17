/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object PlaylistModelDescriptionFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string("EngagementPanelPlaylistPanelModel{videoId="))
)

internal object PlaylistControllerConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(string("engagement-panel-playlist"))
)

internal fun acceptedPlaylistModelFingerprint(controllerType: String, modelField: FieldReference) = Fingerprint(
    definingClass = controllerType,
    returnType = "V",
    parameters = listOf(modelField.type),
    filters = listOf(fieldAccess(reference = modelField, opcode = Opcode.IPUT_OBJECT))
)

/** Finds the native model renderer for restoration; no visibility branch is overridden. */
internal fun playlistPanelRenderFingerprint(modelField: FieldReference, panelField: FieldReference) = Fingerprint(
    definingClass = modelField.definingClass,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(reference = modelField, opcode = Opcode.IGET_OBJECT),
        fieldAccess(reference = panelField, opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(4)),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = "Z",
            location = MatchAfterWithin(6)),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately())
    )
)
