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
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** The native batch request, not the modern panel's single-video parameter. */
internal object QueueSaveRequestFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(string("playlist/get_add_to_playlist"))
)

internal fun queueSavePickerFingerprint(requestType: String) = Fingerprint(
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
    filters = listOf(string("navigation_endpoint"), methodCall(definingClass = requestType, name = "<init>"))
)

internal fun queueSavePickerFactoryFingerprint(pickerType: String, endpointType: String) = Fingerprint(
    parameters = listOf(endpointType),
    returnType = "V",
    filters = listOf(methodCall(definingClass = pickerType, name = "<init>"), string("navigation_endpoint"))
)

/** Response callback owned by the picker request; the renderer discriminator is stable. */
internal fun queueSaveResponseFingerprint(callbackType: String) = Fingerprint(
    definingClass = callbackType,
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "V",
    filters = listOf(opcode(Opcode.CHECK_CAST), literal(88978004L))
)

/** Native edit callback error diagnostics identify the same request's success listener. */
internal object QueueSaveEditErrorFingerprint : Fingerprint(
    filters = listOf(string("Error editing the playlist"))
)

internal fun queueSaveEditCreationFingerprint(callbackType: String, endpointType: String) = Fingerprint(
    parameters = listOf(endpointType, "Ljava/lang/Object;"),
    returnType = "V",
    filters = listOf(methodCall(definingClass = callbackType, name = "<init>"))
)

internal fun queueSaveEditSuccessFingerprint(callbackType: String) = Fingerprint(
    definingClass = callbackType,
    parameters = listOf("L"),
    returnType = "V",
    filters = listOf(methodCall("Ljava/util/List;->iterator()Ljava/util/Iterator;"))
)

/** Stable diagnostics from the native playback queue, distinct from the panel adapter. */
internal object NativeQueueDescriptorFingerprint : Fingerprint(
    parameters = listOf("L"),
    // 21.16 concatenates this diagnostic; 21.20 formats it in one string.
    filters = listOf(string("getNavigationDescriptor for ", StringComparisonType.STARTS_WITH))
)

internal object NativeQueueSelectionFingerprint : Fingerprint(
    filters = listOf(string("Navigation committed to a video that is not expected by the navigable queue"))
)

internal fun nativeQueueWrapperFingerprint(navigator: String, repeat: String, shuffle: String) = Fingerprint(
    name = "<init>",
    custom = { method, _ -> method.parameterTypes.take(3) == listOf(navigator, repeat, shuffle) }
)

/** The native mutable queue builds its shuffle links with the framework shuffler. */
internal fun nativeQueueShuffleSetterFingerprint(queue: String) = Fingerprint(
    definingClass = queue,
    parameters = listOf("Z"),
    returnType = "V",
    filters = listOf(methodCall("Ljava/util/Collections;->shuffle(Ljava/util/List;)V"))
)

internal fun nativeQueueLoaderFingerprint(service: String, descriptor: String) = Fingerprint(
    definingClass = service,
    parameters = listOf(descriptor, "L"),
    filters = listOf(string("Initializing a PlaybackSequencer in loaderNavigator, but it does not exist"))
)

/** Read the active sequencer through its service holder, independently of loader inlining. */
internal fun nativeQueueSequencerFingerprint(service: String, sequencer: String) = Fingerprint(
    definingClass = service,
    filters = listOf(
        fieldAccess(definingClass = service, opcode = Opcode.IGET_OBJECT),
        fieldAccess(type = "Ljava/lang/Object;", opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately()),
        checkCast(sequencer, location = MatchAfterWithin(6))
    )
)

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

internal fun quickActionsModelCallbackFingerprint(controller: String) = Fingerprint(
    definingClass = controller,
    parameters = listOf("Ljava/lang/Object;", "L"),
    returnType = "L",
    filters = listOf(opcode(Opcode.CHECK_CAST), methodCall(definingClass = controller,
        returnType = "V", location = MatchAfterImmediately()))
)

internal fun quickActionsResetFingerprint(controller: String, setter: MethodReference) = Fingerprint(
    definingClass = controller,
    parameters = listOf("L"),
    returnType = "V",
    filters = listOf(
        methodCall(reference = setter),
        fieldAccess(definingClass = controller, type = "Z", opcode = Opcode.IPUT_BOOLEAN,
            location = MatchAfterWithin(3))
    )
)

internal fun quickActionsSizeFingerprint(controller: String) = Fingerprint(
    name = "onLayoutChange",
    parameters = listOf("Landroid/view/View;") + List(8) { "I" },
    returnType = "V",
    filters = listOf(
        methodCall("Landroid/util/Size;-><init>(II)V"),
        fieldAccess(definingClass = controller, opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(8)),
        methodCall(parameters = listOf("Ljava/lang/Object;"), returnType = "V",
            location = MatchAfterImmediately())
    )
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

/** Native overflow handler creates its endpoint map before handing the row to the menu factory. */
internal fun playlistRowMenuOpenFingerprint(videoType: String) = Fingerprint(
    filters = listOf(string("com.google.android.libraries.youtube.innertube.endpoint.tag")),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions
        instructions != null && videoType in method.parameterTypes &&
                instructions.mapNotNull { it.getReference<MethodReference>() }
                    .any { reference ->
                        reference.returnType.startsWith("L") && reference.parameterTypes.size == 4 &&
                                reference.parameterTypes.all { it == "Ljava/lang/Object;" }
                    } &&
                instructions.mapNotNull { it.getReference<MethodReference>() }
                    .any { "Ljava/util/Map;" in it.parameterTypes && it.returnType == "V" }
    }
)

/** Native sheet dispatch calls its endpoint callback immediately before its terminal dismissal. */
internal fun playlistRowMenuDispatchFingerprint(
    sheetType: String,
    callbackType: String,
    callbackParameters: List<String>
) = Fingerprint(
    definingClass = sheetType,
    returnType = "V",
    custom = { method, _ ->
        val implementation = method.implementation
        val instructions = implementation?.instructions?.toList()
        if (implementation == null || instructions == null ||
            AccessFlags.STATIC.isSet(method.accessFlags)) {
            false
        } else {
            val callbackIndex = instructions.indexOfFirst { instruction ->
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == callbackType && reference.returnType == "V" &&
                            reference.parameterTypes.map { it.toString() } == callbackParameters
                } == true
            }
            if (callbackIndex < 0 || callbackIndex + 2 >= instructions.size) {
                false
            } else {
                val dismissal = instructions[callbackIndex + 1]
                val dismissalReference = dismissal.getReference<MethodReference>()
                val dismissalRegisters = dismissal as? FiveRegisterInstruction
                val parameterWords = method.parameterTypes.sumOf { type ->
                    if (type == "J" || type == "D") 2 else 1
                }
                val thisRegister = implementation.registerCount - parameterWords - 1
                dismissal.opcode == Opcode.INVOKE_VIRTUAL && dismissalReference != null &&
                        dismissalReference.returnType == "V" && dismissalReference.parameterTypes.isEmpty() &&
                        dismissalRegisters?.registerCount == 1 &&
                        dismissalRegisters.registerC == thisRegister &&
                        instructions[callbackIndex + 2].opcode == Opcode.RETURN_VOID
            }
        }
    }
)

/** Rich playlist rows, as opposed to the compact/offline binders. */
internal object PlaylistRichRowConstructorFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.ID, "playlist_panel_video_item"))
)

/** Native selection updates replace the swipe foreground's background. */
internal object PlaylistRowBackgroundFingerprint : Fingerprint(
    classFingerprint = PlaylistRichRowConstructorFingerprint,
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        fieldAccess(type = "Landroid/view/View;", opcode = Opcode.IGET_OBJECT),
        methodCall("Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V",
            location = MatchAfterImmediately()),
        methodCall("Landroid/widget/TextView;->setTextColor(I)V", location = MatchAfterWithin(8))
    )
)

/** SwipeLayout is an unobfuscated native widget; attachment caches its foreground drawable. */
internal object PlaylistSwipeAttachedFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/common/ui/swipelayout/SwipeLayout;",
    name = "onAttachedToWindow",
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        fieldAccess(type = "Landroid/view/View;", opcode = Opcode.IPUT_OBJECT),
        methodCall("Landroid/view/View;->getBackground()Landroid/graphics/drawable/Drawable;",
            location = MatchAfterImmediately()),
        fieldAccess(type = "Landroid/graphics/drawable/Drawable;", opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterWithin(2))
    )
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
        // The controls allocation can sit between the factory and provider reads.
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(1)),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(),
            returnType = "Ljava/lang/Object;", location = MatchAfterWithin(1)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // The factory can allocate controls before invocation and alias the result before casting.
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

internal object PlaylistShuffleViewFingerprint : Fingerprint(
    classFingerprint = PlaylistHeaderConstructorFingerprint,
    name = "<init>",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "shuffle"),
        methodCall("Landroid/view/View;->findViewById(I)Landroid/view/View;", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast("Landroid/widget/ImageView;", location = MatchAfterImmediately()),
        fieldAccess(type = "Landroid/widget/ImageView;", opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterImmediately())
    )
)

/** The fallback header action reads its service and shuffle view before toggling. */
internal fun playlistShufflePressFingerprint(navigator: FieldReference, view: FieldReference) = Fingerprint(
    name = "onClick",
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    filters = listOf(
        fieldAccess(reference = navigator, opcode = Opcode.IGET_OBJECT),
        fieldAccess(reference = view, opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        methodCall("Landroid/widget/ImageView;->isSelected()Z", location = MatchAfterImmediately())
    )
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
internal fun notificationSkipCallbackFingerprint(callbackName: String) = Fingerprint(
    name = callbackName,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = "V",
            location = MatchAfterImmediately())
    ),
    custom = { _, classDef -> classDef.superclass == "Landroid/media/session/MediaSession\$Callback;" }
)

internal fun notificationDelegateFingerprint(callback: MethodReference, delegateType: String? = null) = Fingerprint(
    definingClass = delegateType,
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
internal fun notificationForegroundSkipFingerprint(callbackType: String, request: FieldReference) = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(fieldAccess(reference = request, opcode = Opcode.SGET_OBJECT)),
    custom = { _, classDef -> callbackType in classDef.interfaces }
)

/** Load a new watch target; native JUMP only accepts targets in an existing playlist. */
internal fun notificationPreviousDispatchFingerprint(previous: MethodReference) = Fingerprint(
    definingClass = previous.definingClass,
    name = previous.name,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = previous.definingClass, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately())
    )
)

/** Stable broadcast actions identify the native notification's paired skip controls. */
internal object NotificationSkipActionsFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        string("com.google.android.libraries.youtube.player.action.controller_notification_prev"),
        string("com.google.android.libraries.youtube.player.action.controller_notification_next")
    )
)

/** Both native adapter variants publish Previous/Next through the same model setter. */
internal fun notificationCapabilitiesFingerprint(
    ownerType: String,
    setter: MethodReference,
    adapterType: String? = null
) = Fingerprint(
    definingClass = adapterType,
    parameters = emptyList(),
    filters = listOf(methodCall(reference = setter)),
    custom = { method, classDef -> method.returnType in listOf("V", "I") &&
            classDef.fields.any { it.type == ownerType } }
)

/** The newer adapter dispatches the setter's returned change flags through its native notifier. */
internal fun notificationCapabilitiesDispatchFingerprint(computation: MethodReference) = Fingerprint(
    definingClass = computation.definingClass,
    filters = listOf(
        methodCall(reference = computation),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        methodCall(definingClass = computation.definingClass, parameters = listOf("I"), returnType = "V",
            location = MatchAfterImmediately())
    )
)

/** PCU is the native player-controls update trace in the original notification adapter. */
internal fun notificationPlaybackStateFingerprint(adapterType: String) = Fingerprint(
    definingClass = adapterType,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(string("PCU"))
)

/** The newer adapter implements the same playback-state callback without the trace. */
internal fun notificationPlaybackStateFingerprint(adapterType: String, callback: MethodReference) = Fingerprint(
    definingClass = adapterType,
    name = callback.name,
    returnType = callback.returnType,
    parameters = callback.parameterTypes.map { it.toString() }
)

/** The generated builder's missing-property diagnostic identifies duration finalization. */
internal fun queueSnackbarDurationFingerprint(builderType: String) = Fingerprint(
    definingClass = builderType,
    parameters = emptyList(),
    returnType = "L",
    filters = listOf(string("Property \"duration\" has not been set"))
)

/** Both snackbar factories live together; the watch-screen factory is its native sibling. */
internal object QueueSnackbarFactoryFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(string("feedback_undo"))
)

/** The mini player's close click, rather than programmatic reloads using the same dismiss API. */
internal fun queuePlayerCloseClickFingerprint(dismiss: MethodReference) = Fingerprint(
    name = "onClick",
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    filters = listOf(methodCall(reference = dismiss)),
    custom = { _, classDef -> "Landroid/view/View\$OnClickListener;" in classDef.interfaces }
)

internal fun queueUndoClickFingerprint(listenerType: String, factoryType: String,
                                      dispatcherType: String, callback: MethodReference) = Fingerprint(
    definingClass = listenerType,
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        string("com.google.android.libraries.youtube.innertube.endpoint.tag"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = factoryType, type = dispatcherType),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, definingClass = callback.definingClass,
            name = callback.name, parameters = callback.parameterTypes.map { it.toString() },
            returnType = "V", location = MatchAfterWithin(6))
    )
)
