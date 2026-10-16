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
import app.morphe.patcher.instanceOf
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

internal class QueueParseMessageFingerprint(messageType: String) : Fingerprint(
    definingClass = messageType,
    name = "parseFrom",
    parameters = listOf(messageType, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
    returnType = messageType,
)

internal object QueueGeneratedRegistryFingerprint : Fingerprint(
    definingClass = "Lcom/google/protobuf/ExtensionRegistryLite;",
    name = "getGeneratedRegistry",
    parameters = emptyList(),
    returnType = "Lcom/google/protobuf/ExtensionRegistryLite;",
)

internal class QueueHasExtensionFingerprint(messageType: String, extensionType: String) : Fingerprint(
    definingClass = messageType,
    parameters = listOf(extensionType),
    returnType = "Z",
)

internal class QueueGetExtensionFingerprint(messageType: String, extensionType: String) : Fingerprint(
    definingClass = messageType,
    parameters = listOf(extensionType),
    returnType = "Ljava/lang/Object;",
)

internal object QueueLikeStatusFingerprint : Fingerprint(
    classFingerprint = QueueLikeEntityFingerprint,
    name = "getLikeStatus",
    parameters = emptyList(),
    returnType = "L",
    filters = listOf(fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT)),
)

internal class QueueCurrentAccountFingerprint(providerType: String, accountType: String) : Fingerprint(
    definingClass = providerType,
    parameters = emptyList(),
    returnType = accountType,
)

internal object QueueEntityKeyNumberFingerprint : Fingerprint(
    classFingerprint = QueueEntityKeyFingerprint,
    parameters = listOf("Ljava/lang/String;"),
    returnType = "I",
)

internal object QueueEntityVideoIdFingerprint : Fingerprint(
    classFingerprint = QueueEntityKeyFingerprint,
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
)

internal object QueueEntityKeyDecodeFingerprint : Fingerprint(
    classFingerprint = QueueEntityKeyFingerprint,
    parameters = listOf("Ljava/lang/String;"),
    returnType = "L",
    filters = listOf(methodCall(name = "parseFrom")),
)

internal class QueueVideoKeyEncoderFingerprint(format: FieldReference) : Fingerprint(
    classFingerprint = QueueEntityKeyFingerprint,
    filters = listOf(
        literal(1),
        fieldAccess(reference = format, opcode = Opcode.IPUT, location = MatchAfterImmediately()),
    ),
)

internal class QueueRatingKeyFingerprint(encoder: MethodReference) : Fingerprint(
    classFingerprint = QueueEntityKeyFingerprint,
    parameters = listOf("I", "Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
    filters = listOf(methodCall(reference = encoder)),
)

internal class QueueRatingCleanupFingerprint(presenterType: String) : Fingerprint(
    definingClass = presenterType,
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(methodCall(parameters = listOf("Ljava/lang/Object;"), returnType = "V")),
)

internal class QueueSubscribeRatingEventsFingerprint(eventsType: String) : Fingerprint(
    definingClass = eventsType,
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "V",
    filters = listOf(methodCall(parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Class;"))),
)

internal class QueuePlayerMetadataFingerprint(playerType: String) : Fingerprint(
    definingClass = playerType,
    name = "setChannelInformation",
    parameters = listOf("L"),
    returnType = "V",
)

internal class QueueNavigationContractFingerprint(
    intentType: String,
    descriptorType: String,
    commitName: String,
) : Fingerprint(
    name = commitName,
    parameters = listOf(intentType),
    returnType = descriptorType,
)

internal class QueueNavigatorConstructorFingerprint(navigatorType: String, backendType: String) : Fingerprint(
    definingClass = navigatorType,
    name = "<init>",
    parameters = List(10) { "L" } + "Lj$/util/Optional;",
    filters = listOf(instanceOf(backendType)),
)

internal class QueueSwitchBackendFingerprint(navigatorType: String, backendType: String) : Fingerprint(
    definingClass = navigatorType,
    parameters = listOf(backendType, backendType),
    returnType = "V",
    filters = listOf(fieldAccess(definingClass = "this", type = backendType, opcode = Opcode.IPUT_OBJECT)),
)

internal class QueueNavigationAvailabilityFingerprint(
    navigatorType: String,
    intentType: String,
) : Fingerprint(
    definingClass = navigatorType,
    parameters = listOf(intentType),
    returnType = "I",
)

internal class QueueMessageInfoFingerprint(messageType: String) : Fingerprint(
    definingClass = messageType,
    name = "dynamicMethod",
    filters = listOf(methodCall(name = "newMessageInfo")),
)

internal class QueueDescriptorVideoFingerprint(descriptorType: String, video: FieldReference) : Fingerprint(
    definingClass = descriptorType,
    parameters = emptyList(),
    returnType = "Ljava/lang/String;",
    filters = listOf(fieldAccess(reference = video)),
)

internal class QueueTransportConstructorFingerprint(transportType: String) : Fingerprint(
    definingClass = transportType,
    name = "<init>",
    parameters = List(4) { "L" },
    filters = listOf(methodCall(smali = "Ljava/lang/Object;-><init>()V")),
)

internal class QueueLoadDescriptorFingerprint(playerType: String, descriptorType: String) : Fingerprint(
    definingClass = playerType,
    parameters = listOf(descriptorType),
    returnType = "V",
)

internal class QueueTransportContractFingerprint(next: MethodReference) : Fingerprint(
    name = next.name,
    parameters = emptyList(),
    returnType = "V",
)

internal class QueueShuffleConstructorFingerprint(shuffleType: String) : Fingerprint(
    definingClass = shuffleType,
    name = "<init>",
    parameters = List(5) { "L" },
)

internal class QueueStorageConstructorFingerprint(
    storageType: String,
    playbackType: String,
    parserType: String,
    configType: String,
) : Fingerprint(
    definingClass = storageType,
    name = "<init>",
    parameters = listOf(playbackType, parserType, configType),
)

internal class QueueBackendConstructorFingerprint(backendType: String) : Fingerprint(
    definingClass = backendType,
    name = "<init>",
    parameters = listOf("L", "L"),
)

internal class QueuePopulateFingerprint(populateType: String, responseType: String) : Fingerprint(
    definingClass = populateType,
    parameters = listOf(responseType),
    returnType = "V",
)

internal class QueuePopulateConstructorFingerprint(
    populateType: String,
    parserType: String,
    configType: String,
) : Fingerprint(
    definingClass = populateType,
    name = "<init>",
    parameters = listOf("L", parserType, configType),
)

internal class QueueStorageSwitchFingerprint(
    managerType: String,
    storageType: String,
    dispatcherType: String,
) : Fingerprint(
    definingClass = managerType,
    parameters = listOf(storageType),
    returnType = "V",
    filters = listOf(methodCall(definingClass = dispatcherType)),
)

internal class QueuePlaybackDispatchFingerprint(dispatcherType: String) : Fingerprint(
    definingClass = dispatcherType,
    parameters = listOf("L", "L", "Z"),
    returnType = "V",
)

internal class QueueRepeatModeFingerprint(backendType: String) : Fingerprint(
    definingClass = backendType,
    parameters = listOf("I"),
    returnType = "V",
)

internal class QueueRepeatForwardFingerprint(navigatorType: String, repeat: MethodReference) : Fingerprint(
    definingClass = navigatorType,
    parameters = listOf("I"),
    returnType = "V",
    filters = listOf(methodCall(name = repeat.name, parameters = listOf("I"), returnType = "V")),
)

internal class QueueShuffleForwardFingerprint(navigatorType: String, shuffle: MethodReference) : Fingerprint(
    definingClass = navigatorType,
    parameters = listOf("Z"),
    returnType = "V",
    filters = listOf(methodCall(name = shuffle.name, parameters = listOf("Z"), returnType = "V")),
)

internal class QueueHeaderUpdateFingerprint(headerType: String) : Fingerprint(
    definingClass = headerType,
    parameters = listOf("I", "Z"),
    returnType = "V",
    filters = listOf(fieldAccess(type = "Landroid/widget/ImageView;", opcode = Opcode.IGET_OBJECT)),
)

internal class QueueAdapterRowsFingerprint(adapterType: String) : Fingerprint(
    definingClass = adapterType,
    parameters = listOf("Ljava/util/List;"),
    returnType = "Ljava/util/List;",
    filters = listOf(fieldAccess(definingClass = "this", type = "I", opcode = Opcode.IGET)),
)

internal class QueueAdapterGesturesFingerprint(adapterType: String) : Fingerprint(
    definingClass = adapterType,
    parameters = listOf("Ljava/lang/Class;", "Ljava/lang/Object;", "I"),
    returnType = "[Ljava/lang/Class;",
)

internal class QueueMoveEventConstructorFingerprint(eventType: String) : Fingerprint(
    definingClass = eventType,
    name = "<init>",
    parameters = listOf("L", "L", "I", "I"),
)

internal class QueueRowViewFingerprint(presenterType: String) : Fingerprint(
    definingClass = presenterType,
    parameters = emptyList(),
    returnType = "Landroid/view/View;",
    filters = listOf(fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT)),
)

internal class QueueSetPanelModelFingerprint(controllerType: String, modelType: String) : Fingerprint(
    definingClass = controllerType,
    parameters = listOf(modelType),
    returnType = "V",
    filters = listOf(fieldAccess(definingClass = "this", type = modelType, opcode = Opcode.IPUT_OBJECT)),
)

internal class QueuePlaybackContextFingerprint(controllerType: String) : Fingerprint(
    definingClass = controllerType,
    parameters = List(3) { "Ljava/lang/String;" },
    returnType = "V",
    filters = listOf(fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT)),
)

internal class QueueDescriptorUpdateFingerprint(controllerType: String, descriptorType: String) : Fingerprint(
    definingClass = controllerType,
    parameters = listOf(descriptorType),
    returnType = "V",
)
