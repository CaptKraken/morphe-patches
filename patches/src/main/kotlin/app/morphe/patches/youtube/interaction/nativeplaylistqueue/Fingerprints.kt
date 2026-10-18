/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
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

/** The standard button click context retains the native enclosing row identity. */
internal object NativeButtonContextFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    parameters = listOf("L", "Z"),
    filters = listOf(string("anchor_view")),
    custom = { _, classDef -> "Landroid/view/View\$OnClickListener;" in classDef.interfaces }
)

internal object NativeButtonDispatchFingerprint : Fingerprint(
    classFingerprint = NativeButtonContextFingerprint,
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "V",
        parameters = listOf("L", "Ljava/util/Map;")))
)

/** Rich playlist rows, as opposed to the compact/offline binders. */
internal object PlaylistRichRowConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.ID, "playlist_panel_video_item"))
)

/** The native binder configures the drag handle's touch target from resources. */
internal object PlaylistDragBinderFingerprint : Fingerprint(
    classFingerprint = PlaylistRichRowConstructorFingerprint,
    returnType = "V",
    filters = listOf(resourceLiteral(ResourceType.DIMEN, "playlist_drag_handle_touch_padding_start"))
)

internal fun playlistDragStartFingerprint(holderType: String) = Fingerprint(
    definingClass = holderType,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        opcode(Opcode.IF_EQ),
        fieldAccess(type = "Landroid/view/View;", opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(1)),
        methodCall("Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V",
            location = MatchAfterWithin(1))
    )
)

internal fun playlistDragEndFingerprint(holderType: String) = Fingerprint(
    definingClass = holderType,
    returnType = "V",
    parameters = listOf("L", "L", "I", "I"),
    filters = listOf(
        opcode(Opcode.IF_EQ),
        methodCall(definingClass = holderType, opcode = Opcode.INVOKE_DIRECT,
            parameters = emptyList(), returnType = "V", location = MatchAfterWithin(1))
    )
)

internal fun playlistDragUnbindFingerprint(holderType: String) = Fingerprint(
    definingClass = holderType,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall("Landroid/view/View;->setOnTouchListener(Landroid/view/View\$OnTouchListener;)V"),
        methodCall("Landroid/view/View;->setOnLongClickListener(Landroid/view/View\$OnLongClickListener;)V")
    )
)

internal object PlaylistEditEventsFingerprint : Fingerprint(
    classFingerprint = PlaylistItemsConstructorFingerprint,
    parameters = listOf("Ljava/lang/Class;", "Ljava/lang/Object;", "I"),
    returnType = "[Ljava/lang/Class;"
)

internal fun playlistNavigationRequestFingerprint(enumType: String) = Fingerprint(
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(enumType, "L", "L")
)

internal fun playlistNavigationIntentFingerprint(enumType: String, intent: String) = Fingerprint(
    definingClass = enumType,
    name = "<clinit>",
    filters = listOf(
        string(intent),
        fieldAccess(type = enumType, opcode = Opcode.SPUT_OBJECT, location = MatchAfterWithin(3))
    )
)

internal fun playlistNavigationSingletonFingerprint(wrapperType: String, intent: FieldReference) = Fingerprint(
    definingClass = wrapperType,
    name = "<clinit>",
    filters = listOf(
        fieldAccess(reference = intent, opcode = Opcode.SGET_OBJECT),
        methodCall(definingClass = wrapperType, name = "<init>", parameters = listOf(intent.type),
            location = MatchAfterImmediately()),
        fieldAccess(type = wrapperType, opcode = Opcode.SPUT_OBJECT, location = MatchAfterImmediately())
    )
)

/** The same native dispatch family used by Disable playlist autoplay. */
internal fun playlistNavigationDispatchFingerprint(wrapperType: String) = Fingerprint(
    returnType = "V",
    parameters = listOf(wrapperType),
    custom = { method, classDef ->
        method.implementation != null && classDef.methods.any { sibling ->
            sibling.implementation != null && sibling.returnType == "I" &&
                    sibling.parameterTypes.singleOrNull() == wrapperType
        }
    }
)

/** Resolve the playlist controller's own scoped navigator, not a global last-created instance. */
internal fun playlistNavigatorProviderFingerprint(controllerType: String, navigatorType: String) = Fingerprint(
    definingClass = controllerType,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = controllerType, opcode = Opcode.IGET_OBJECT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(),
            returnType = "Ljava/lang/Object;", location = MatchAfterWithin(1)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // The native factory allocates controls before invocation and aliases its result before casting.
        checkCast(navigatorType, location = MatchAfterWithin(1))
    )
)

/** Native player control eligibility, including its alternate history callbacks. */
internal fun playlistControlAvailabilityFingerprint(navigatorType: String, request: FieldReference) = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(), returnType = "Z",
            location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(type = navigatorType, opcode = Opcode.IGET_OBJECT),
        fieldAccess(reference = request, opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(definingClass = navigatorType, parameters = listOf(request.type), returnType = "Z",
            location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQ, location = MatchAfterImmediately())
    )
)

internal fun playlistControlPressFingerprint(controlsType: String, request: FieldReference) = Fingerprint(
    definingClass = controlsType,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(fieldAccess(reference = request, opcode = Opcode.SGET_OBJECT)),
    custom = { method, _ -> AccessFlags.PUBLIC.isSet(method.accessFlags) }
)

internal object PlaylistHeaderConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.LAYOUT, "compact_playlist_panel_header"))
)

/** Both native repeat controls observe this exact mode/shuffle pair. */
internal object PlaylistRepeatBindingFingerprint : Fingerprint(
    classFingerprint = PlaylistHeaderConstructorFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        fieldAccess(type = "I", opcode = Opcode.IGET, location = MatchAfterImmediately()),
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        methodCall(parameters = listOf("I", "Z"), returnType = "V", location = MatchAfterImmediately())
    )
)

internal object PlaylistRepeatCapabilitiesFingerprint : Fingerprint(
    classFingerprint = PlaylistHeaderConstructorFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall("Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;->setEnabled(Z)V")
    )
)

/** Observe the native notification before it compares old state or notifies either control. */
internal fun playlistRepeatObserverFingerprint(mode: FieldReference, shuffle: FieldReference) = Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(type = "I", opcode = Opcode.IGET),
        fieldAccess(type = "Ljava/lang/Object;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        checkCast(mode.definingClass, location = MatchAfterImmediately()),
        fieldAccess(reference = mode, opcode = Opcode.IGET, location = MatchAfterImmediately()),
        fieldAccess(reference = shuffle, opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        fieldAccess(reference = mode, opcode = Opcode.IPUT, location = MatchAfterImmediately()),
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        fieldAccess(reference = shuffle, opcode = Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately())
    )
)

internal fun playlistRepeatSubscriptionFingerprint(stateType: String, callback: MethodReference) = Fingerprint(
    definingClass = stateType,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(definingClass = stateType, opcode = Opcode.IGET_OBJECT),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(), location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_16),
        methodCall(reference = callback, location = MatchAfterImmediately())
    )
)

/** Register after the constructor has initialized its native weak observer set. */
internal fun playlistRepeatStateConstructorFingerprint(stateType: String, contextType: String) = Fingerprint(
    definingClass = stateType,
    name = "<init>",
    parameters = listOf(contextType),
    filters = listOf(
        fieldAccess(type = "Ljava/util/Set;", opcode = Opcode.IPUT_OBJECT),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately())
    )
)


internal object PlayerLoopMenuConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(string("menu_item_single_video_playback_loop"))
)

/** Header repeat and the native Loop cancellation click share this listener. */
internal fun playlistHeaderRepeatRequestFingerprint(headerType: String, setter: MethodReference) = Fingerprint(
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        checkCast(headerType),
        methodCall("Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;->isSelected()Z"),
        methodCall(reference = setter)
    )
)

internal fun playerLoopRequestFingerprint(gearType: String, setter: MethodReference) = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(checkCast(gearType), methodCall(reference = setter))
)

/** Explicit native Loop on/off commands; internal playback resets have another signature. */
internal fun playerLoopCommandFingerprint(setter: MethodReference) = Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(methodCall(reference = setter))
)

/** Resolve the native callback name through Android, rather than its obfuscated override. */
internal object NotificationNextCallbackFingerprint : Fingerprint(
    name = "onSkipToNext",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = "V",
            location = MatchAfterImmediately())
    ),
    custom = { _, classDef -> classDef.superclass == "Landroid/media/session/MediaSession\$Callback;" }
)

internal fun notificationNextDelegateFingerprint(callback: MethodReference) = Fingerprint(
    name = callback.name,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = "V",
            location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately())
    ),
    custom = { _, classDef -> classDef.superclass == callback.definingClass }
)

/** Observe after media logging, before the optional foreground callback takes over. */
internal fun notificationNextDispatchFingerprint(owner: String, next: FieldReference) = Fingerprint(
    definingClass = owner,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = owner, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(definingClass = owner, opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(5)),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(),
            returnType = "Ljava/lang/Object;", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
        fieldAccess(reference = next, opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(parameters = listOf(next.type), returnType = "V", location = MatchAfterImmediately())
    )
)

/** The service navigator recognizes an ended same-video target before starting playback. */
internal fun playlistServiceNavigatorFingerprint(wrapperType: String) = Fingerprint(
    parameters = listOf(wrapperType),
    returnType = "V",
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(), returnType = "Ljava/lang/String;"),
        methodCall("Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z",
            location = MatchAfterWithin(1))
    )
)

/** Native playback descriptor builder accepts an ordinary watch command. */
internal fun playlistPlaybackDescriptorBuilderFingerprint(descriptorType: String) = Fingerprint(
    parameters = emptyList(),
    returnType = descriptorType,
    filters = listOf(string("Only one of localProto, command, videoIdList can be set"))
)

/** Only the ordinary watch-page callback is replaced; casting/no-op delegates keep control. */
internal fun notificationForegroundNextFingerprint(callbackType: String, next: FieldReference) = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(fieldAccess(reference = next, opcode = Opcode.SGET_OBJECT)),
    custom = { _, classDef -> callbackType in classDef.interfaces }
)

/** Load a new watch target; native JUMP only accepts targets in an existing playlist. */
internal fun playlistServiceLoadFingerprint(serviceType: String, descriptorType: String) = Fingerprint(
    definingClass = serviceType,
    parameters = listOf(descriptorType),
    returnType = "V",
    custom = { method, _ -> AccessFlags.PUBLIC.isSet(method.accessFlags) &&
            !AccessFlags.STATIC.isSet(method.accessFlags) }
)
