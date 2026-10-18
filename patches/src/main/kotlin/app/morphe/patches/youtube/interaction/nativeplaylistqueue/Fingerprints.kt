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
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

internal fun acceptedPlaylistModelFingerprint(
    controllerType: String,
    modelField: FieldReference,
    responseVideoField: FieldReference
) = Fingerprint(
    definingClass = controllerType,
    returnType = "V",
    parameters = listOf(modelField.type),
    filters = listOf(
        fieldAccess(reference = responseVideoField, opcode = Opcode.IGET_OBJECT),
        methodCall("Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z",
            location = MatchAfterImmediately()),
        // The response's optional video ID is checked against the active video's ID.
        fieldAccess(definingClass = controllerType, type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(2)),
        methodCall("Ljava/lang/String;->length()I", location = MatchAfterWithin(1)),
        methodCall("Ljava/lang/String;->equals(Ljava/lang/Object;)Z", location = MatchAfterWithin(2)),
        fieldAccess(reference = modelField, opcode = Opcode.IPUT_OBJECT)
    )
)

/** The active video transition can complete without any playlist model being accepted. */
internal fun playlistVideoTransitionFingerprint(activeVideoField: FieldReference) = Fingerprint(
    definingClass = activeVideoField.definingClass,
    returnType = "V",
    parameters = List(3) { "Ljava/lang/String;" },
    filters = listOf(fieldAccess(reference = activeVideoField, opcode = Opcode.IPUT_OBJECT))
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

/** Native playlist item factory; the education label belongs to its reorder/delete controller. */
internal object PlaylistItemsConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.STRING, "playlist_reorder_delete_edu"))
)

internal fun playlistItemFactoryFingerprint(videoType: String) = Fingerprint(
    classFingerprint = PlaylistItemsConstructorFingerprint,
    parameters = listOf("Ljava/util/List;"),
    returnType = "Ljava/util/List;",
    filters = listOf(fieldAccess(type = videoType, opcode = Opcode.IGET_OBJECT))
)

internal fun playlistContentsFingerprint(panelType: String, factory: MethodReference) = Fingerprint(
    definingClass = factory.definingClass,
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = panelType, opcode = Opcode.IGET_OBJECT),
        methodCall(reference = factory, location = MatchAfterImmediately())
    )
)

internal fun playlistClickEndpointFingerprint(endpoint: FieldReference, decorate: MethodReference) = Fingerprint(
    definingClass = endpoint.definingClass,
    name = "onClick",
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    filters = listOf(
        fieldAccess(reference = endpoint, opcode = Opcode.IGET_OBJECT),
        methodCall(reference = decorate, location = MatchAfterImmediately())
    )
)

internal fun playlistEndpointBindingFingerprint(endpoint: FieldReference) = Fingerprint(
    definingClass = endpoint.definingClass,
    returnType = "V",
    filters = listOf(fieldAccess(reference = endpoint, opcode = Opcode.IPUT_OBJECT)),
    custom = { method, _ -> method.name != "onClick" }
)

internal fun playlistNativeClickFingerprint(listenerType: String) = Fingerprint(
    definingClass = listenerType,
    name = "onClick",
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    filters = listOf(string("com.google.android.libraries.youtube.logging.interaction_logger"))
)
