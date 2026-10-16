/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// These are protobuf extension numbers, not resource IDs or obfuscated DEX symbols.
internal object QueuePanelExtensionFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(literal(50631000), methodCall(name = "newSingularGeneratedExtension")),
)

internal object QueueRatingExtensionFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(literal(124608045), methodCall(name = "newSingularGeneratedExtension")),
)

internal object QueueLikeEntityFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(string("LikeStatusEntityModel{")),
)

internal object QueueEntityKeyFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(string("/device/orientation"), string("/app/mdx")),
)

internal object QueueOldAccountStoreFingerprint : Fingerprint(
    filters = listOf(string("com/google/android/libraries/youtube/account/identity/DefaultIdentityStoreOld")),
)

internal object QueueAccountStoreFingerprint : Fingerprint(
    filters = listOf(string("com/google/android/libraries/youtube/account/identity/DefaultIdentityStoreLockFix")),
)

internal object QueueControllerFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(string("engagement-panel-playlist")),
)

internal object QueueHeaderFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.LAYOUT, "compact_playlist_panel_header")),
)

internal object QueueRowFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "playlist_panel_video_item"),
        resourceLiteral(ResourceType.ID, "swipe_layout"),
    ),
)

internal object QueueCommitFingerprint : Fingerprint(
    filters = listOf(string("commitIntentToNavigate for %sreturned NULL")),
)

internal object QueueNavigatorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(literal(45377877L)),
)

internal object QueueShuffleFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(methodCall(smali = "Ljava/util/Collections;->shuffle(Ljava/util/List;)V")),
)

internal object QueueEditableRowFingerprint : Fingerprint(
    classFingerprint = QueueRowFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(literal(45368623L)),
)

internal object QueueModelFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(string("EngagementPanelPlaylistPanelModel{videoId=")),
)

internal fun queueNavigationEnumValueFingerprint(enumType: String, label: String) = object : Fingerprint(
    definingClass = enumType,
    name = "<clinit>",
    filters = listOf(
        string(label),
        methodCall(definingClass = enumType, name = "<init>"),
        fieldAccess(type = enumType, opcode = Opcode.SPUT_OBJECT, location = MatchAfterImmediately()),
    ),
) {}

internal fun queueNavigationIntentFingerprint(intentType: String, enumValue: FieldReference) = object : Fingerprint(
    definingClass = intentType,
    name = "<clinit>",
    filters = listOf(
        fieldAccess(reference = enumValue, opcode = Opcode.SGET_OBJECT),
        methodCall(definingClass = intentType, name = "<init>", parameters = listOf(enumValue.type),
            location = MatchAfterImmediately()),
        fieldAccess(type = intentType, opcode = Opcode.SPUT_OBJECT, location = MatchAfterImmediately()),
    ),
) {}

/** Match the native delegate/fallback merge and the availability flag it compares. */
internal fun queueTransportAvailabilityFingerprint(intent: FieldReference) = object : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(reference = intent, opcode = Opcode.SGET_OBJECT),
        methodCall(parameters = listOf(intent.type), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(definingClass = "this", type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQ, location = MatchAfterImmediately()),
    ),
    custom = { method, _ -> AccessFlags.PRIVATE.isSet(method.accessFlags) },
) {}

internal fun queueTransportHandlerFingerprint(owner: String, intent: FieldReference) = object : Fingerprint(
    definingClass = owner,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(fieldAccess(reference = intent, opcode = Opcode.SGET_OBJECT)),
    custom = { method, _ -> !AccessFlags.PRIVATE.isSet(method.accessFlags) },
) {}

internal fun queueNextOverrideFingerprint(next: MethodReference) = object : Fingerprint(
    name = next.name,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(methodCall(reference = next)),
    custom = { _, owner -> owner.superclass == next.definingClass },
) {}

internal fun queueTransportUpdateFingerprint(previous: MethodReference, next: MethodReference) = object : Fingerprint(
    definingClass = next.definingClass,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(methodCall(reference = previous), methodCall(reference = next)),
) {}

internal fun queueTransportRegistrationFingerprint(handlerType: String) = object : Fingerprint(
    returnType = "V",
    parameters = listOf(handlerType),
    filters = listOf(methodCall(smali = "Ljava/util/List;->add(Ljava/lang/Object;)Z")),
) {}

/** A listener's role follows its target view, regardless of listener installation order. */
internal fun queueHeaderListenerFingerprint(view: FieldReference) = object : Fingerprint(
    definingClass = view.definingClass,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(reference = view, opcode = Opcode.IGET_OBJECT),
        fieldAccess(definingClass = "this", type = "Landroid/view/View\$OnClickListener;",
            opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(3)),
        methodCall(name = "setOnClickListener", parameters = listOf("Landroid/view/View\$OnClickListener;"),
            returnType = "V", location = MatchAfterImmediately()),
    ),
) {}

/** The panel's hide-controls flag must never be enabled as a playback capability. */
internal fun queueHiddenHeaderControlsFingerprint(headerType: String, panelType: String) = object : Fingerprint(
    definingClass = headerType,
    returnType = "V",
    parameters = listOf(panelType, "L", "I"),
    filters = listOf(
        fieldAccess(definingClass = panelType, type = "Z", opcode = Opcode.IGET_BOOLEAN),
        fieldAccess(definingClass = "this", type = "Z", opcode = Opcode.IPUT_BOOLEAN),
    ),
) {}

internal fun queueHeaderBindFingerprint(headerType: String, panelType: String) = object : Fingerprint(
    definingClass = headerType,
    returnType = "V",
    parameters = listOf(panelType, "L", "I"),
    filters = listOf(fieldAccess(definingClass = "this", type = panelType, opcode = Opcode.IPUT_OBJECT)),
) {}

internal fun queuePanelRefreshFingerprint(controllerType: String, responseType: String,
                                         panel: FieldReference) = object : Fingerprint(
    definingClass = controllerType,
    returnType = "V",
    parameters = listOf(responseType),
    filters = listOf(fieldAccess(reference = panel, opcode = Opcode.IGET_OBJECT)),
) {}

internal fun queueRowBindFingerprint(presenterType: String, entryId: FieldReference) = object : Fingerprint(
    definingClass = presenterType,
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(reference = entryId, opcode = Opcode.IGET_OBJECT),
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT),
    ),
) {}

internal fun queueRowStyleFingerprint(adapterType: String, style: FieldReference) = object : Fingerprint(
    definingClass = adapterType,
    returnType = "Ljava/util/List;",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(fieldAccess(reference = style, opcode = Opcode.IGET)),
) {}

internal fun queuePanelVisibilityFingerprint(controllerType: String) = object : Fingerprint(
    definingClass = controllerType,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(parameters = emptyList(), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        literal(105291),
    ),
) {}

internal fun queueNavigatorGateFingerprint(navigatorType: String, responseType: String) = object : Fingerprint(
    definingClass = navigatorType,
    returnType = "V",
    parameters = listOf(responseType),
    filters = listOf(
        literal(45377877L),
        methodCall(parameters = listOf("J", "Z"), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
) {}

internal fun queueConstructorAssignmentFingerprint(member: FieldReference) = object : Fingerprint(
    definingClass = member.definingClass,
    name = "<init>",
    filters = listOf(fieldAccess(reference = member, opcode = Opcode.IPUT_OBJECT)),
) {}

internal fun queueModelCreationFingerprint(modelType: String) = object : Fingerprint(
    returnType = modelType,
    parameters = emptyList(),
    filters = listOf(methodCall(definingClass = modelType, name = "<init>")),
) {}

internal fun queueResponseIdentityFingerprint(controllerType: String, responseType: String,
                                            builderField: FieldReference) = object : Fingerprint(
    definingClass = controllerType,
    returnType = "V",
    parameters = listOf(responseType),
    filters = listOf(fieldAccess(reference = builderField, opcode = Opcode.IPUT_OBJECT)),
) {}

internal fun queueVoteEventConstructorFingerprint(voteType: String) = object : Fingerprint(
    name = "<init>",
    parameters = listOf("Ljava/lang/String;", voteType, "Z"),
    filters = listOf(methodCall(smali = "Ljava/lang/Object;-><init>()V")),
) {}

internal fun queueRatingBindFingerprint(buttonType: String, eventType: String) = object : Fingerprint(
    returnType = "V",
    parameters = listOf(buttonType),
    filters = listOf(methodCall(opcode = Opcode.INVOKE_SUPER)),
    custom = { _, owner -> owner.fields.any { it.type == eventType } },
) {}

internal fun queueFormattedTextFingerprint(textType: String) = object : Fingerprint(
    returnType = "Landroid/text/Spanned;",
    parameters = listOf(textType),
) {}

internal fun queueLegacyRatingFingerprint(voteType: String) = object : Fingerprint(
    returnType = "V",
    parameters = listOf(voteType, "L"),
    filters = listOf(methodCall(definingClass = "Landroid/content/res/Resources;", name = "getQuantityString")),
) {}
