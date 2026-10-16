/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.shared.misc.auth.AccountIdentityToStringFingerprint
import app.morphe.patches.youtube.layout.buttons.action.QuickActionsElementSyntheticFingerprint
import app.morphe.patches.youtube.layout.flyout.FlyoutMenuItemMessageFingerprint
import app.morphe.patches.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import app.morphe.patches.youtube.video.information.PlayerInitFingerprint
import app.morphe.patches.youtube.video.information.getChannelNameFingerprint
import app.morphe.patches.youtube.video.information.getVideoTitleFingerprint
import app.morphe.patches.youtube.video.videoid.VideoIdFingerprint
import app.morphe.util.findFieldFromToString
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** Resolve the complete host contract before changing any of its methods or field access. */
internal class NativeQueueReferences(private val context: BytecodePatchContext) {
    private val Fingerprint.sourceMethod get() = with(context) { matchSingle().originalMethod }
    private val Fingerprint.sourceClass get() = with(context) {
        matchAll().map { it.originalClassDef }.distinctBy { it.type }.requireSingle("fingerprint owner")
    }
    private val Fingerprint.matches get() = with(context) { instructionMatches }

    private fun <T> Iterable<T>.requireSingle(role: String): T {
        val matches = toList()
        return matches.singleOrNull() ?: throw PatchException(
            "Native queue: expected one $role, found ${matches.size}"
        )
    }

    val fullscreenControlsBind = QuickActionsElementSyntheticFingerprint.sourceMethod

    private fun field(type: String, valueType: String) = context.classDefBy(type).fields.filter {
        it.type == valueType && !AccessFlags.STATIC.isSet(it.accessFlags)
    }.requireSingle("$valueType field in $type")
    private fun Method.fieldReferences(opcode: Opcode, type: String? = null) = implementation!!.instructions
        .filter { it.opcode == opcode }.map { it.getReference<FieldReference>()!! }
        .filter { type == null || it.type == type }.distinct()
    private fun schema(type: String) = NativeQueueProto(context.classDefBy(type))
    private data class MessageExtension(val containerType: String, val messageType: String, val field: FieldReference)

    private fun extension(fingerprint: Fingerprint, number: Int): MessageExtension {
        val initializer = fingerprint.sourceMethod
        val call = initializer.constantCalls("newSingularGeneratedExtension")
            .filter { it.arguments[4] == number }.requireSingle("protobuf extension $number")
        val arguments = call.arguments
        val extensionField = call.storedField
            ?: throw PatchException("Native queue: protobuf extension $number is not stored")
        return MessageExtension(
            (arguments[0] as FieldReference).type,
            (arguments[6] as TypeReference).type,
            extensionField
        )
    }

    private val panelExtension = extension(QueuePanelExtensionFingerprint, 50631000)
    val panelType = panelExtension.messageType
    private val panelSchema = schema(panelType)
    val panelRows = panelSchema.field(2)
    val rowWrapperType = panelSchema.elementType(2)
    val wrappedRow = schema(rowWrapperType).field(51779701)
    val rowType = wrappedRow.type
    private val rowSchema = schema(rowType)
    val rowActions = rowSchema.field(32)
    val entryId = rowSchema.field(26)
    val defaultPanel = context.classDefBy(panelType).fields.filter {
        it.type == panelType && AccessFlags.STATIC.isSet(it.accessFlags)
    }.requireSingle("default playlist panel")
    val extendableMessageType = context.classDefBy(panelType).superclass!!
    val messageType = context.classDefBy(extendableMessageType).superclass!!
    val parseFrom = QueueParseMessageFingerprint(messageType).sourceMethod
    val generatedRegistry = QueueGeneratedRegistryFingerprint.sourceMethod

    private val ratingExtensionInfo = extension(QueueRatingExtensionFingerprint, 124608045)
    val buttonWrapperType = ratingExtensionInfo.containerType
    val ratingButtonType = ratingExtensionInfo.messageType
    val ratingExtension = ratingExtensionInfo.field
    private val extensionParameterType = context.classDefBy(ratingExtension.type).superclass!!
    val hasExtension = QueueHasExtensionFingerprint(extendableMessageType, extensionParameterType).sourceMethod
    val getExtension = QueueGetExtensionFingerprint(extendableMessageType, extensionParameterType).sourceMethod
    val ratingTarget = schema(ratingButtonType).field(17)
    val ratingTargetType = ratingTarget.type
    val ratingVideo = schema(ratingTargetType).field(1)

    val entityType = QueueLikeEntityFingerprint.sourceClass.type
    val entityStatus = QueueLikeStatusFingerprint.sourceMethod
    val voteType = entityStatus.returnType
    val voteNumber = field(voteType, "I")
    val ratingEntity = QueueLikeStatusFingerprint.matches.single().instruction.getReference<FieldReference>()!!
    private val entitySchema = schema(ratingEntity.type)
    val ratingEntityKey = entitySchema.field(1)
    val ratingEntityStatus = entitySchema.field(2)

    private val accountType = AccountIdentityToStringFingerprint.sourceClass.interfaces
        .filter { it !in listOf("Landroid/os/Parcelable;", "Ljava/io/Serializable;") }
        .requireSingle("account identity interface")
    val currentAccounts = listOf(QueueOldAccountStoreFingerprint, QueueAccountStoreFingerprint).map {
        QueueCurrentAccountFingerprint(it.sourceClass.type, accountType).sourceMethod
    }

    val keyNumber = QueueEntityKeyNumberFingerprint.sourceMethod
    val keyVideo = QueueEntityVideoIdFingerprint.sourceMethod
    // Entity-id format 1 identifies a video; format 2 uses the same encoder for a different
    // key domain. Follow the wrapper calling the encoder which sets that protobuf enum to 1.
    private val keyPayload = QueueEntityKeyDecodeFingerprint.sourceMethod.returnType
    private val keyFormat = schema(keyPayload).field(5)
    private val videoKeyEncoder = QueueVideoKeyEncoderFingerprint(keyFormat).sourceMethod
    val makeRatingKey = QueueRatingKeyFingerprint(videoKeyEncoder).sourceMethod

    val voteEventConstructor = queueVoteEventConstructorFingerprint(voteType).sourceMethod
    val voteEventType = voteEventConstructor.definingClass
    val ratingBind = queueRatingBindFingerprint(ratingButtonType, voteEventType).sourceMethod
    val ratingPresenterType = ratingBind.definingClass
    // Its native cleanup unregisters from the same event bus. Match the inverse add/remove
    // operations through the bus implementation rather than pinning its short method names.
    private val ratingCleanup = QueueRatingCleanupFingerprint(ratingPresenterType).sourceMethod
    private val unregisterEvents = ratingCleanup.implementation!!.instructions
        .mapNotNull { it.getReference<MethodReference>() }.filter {
            it.parameterTypes == listOf("Ljava/lang/Object;") && it.returnType == "V"
        }.requireSingle("rating event unsubscribe")
    val ratingEvents = field(ratingPresenterType, unregisterEvents.definingClass)
    val subscribeRatingEvents = QueueSubscribeRatingEventsFingerprint(ratingEvents.type).sourceMethod

    // This method is added by the shared video-information dependency, so match its
    // mutable class rather than searching the unmodified APK for an extension hook.
    val playerMetadata = with(context) {
        QueuePlayerMetadataFingerprint(PlayerInitFingerprint.sourceClass.type)
            .match(mutableClassDefBy(PlayerInitFingerprint.sourceClass.type)).originalMethod
    }
    val playerResponseType = playerMetadata.parameterTypes.single().toString()
    val playerVideo = VideoIdFingerprint.matches.first().instruction.getReference<MethodReference>()!!
    val playerTitle = getVideoTitleFingerprint(playerResponseType).matches.first()
        .instruction.getReference<MethodReference>()!!
    val playerChannel = getChannelNameFingerprint(playerResponseType).matches.last()
        .instruction.getReference<MethodReference>()!!
    val historyType = FlyoutMenuItemMessageFingerprint.matches[1].instruction.getReference<TypeReference>()!!.type
    private val historySchema = schema(historyType)
    val historyTitle = historySchema.field(3)
    val historyChannel = historySchema.field(10)
    val historyViews = historySchema.field(5)
    val historyAge = historySchema.field(23)
    val textType = historyTitle.type
    val formattedText = queueFormattedTextFingerprint(textType).sourceMethod

    // The legacy count renderer uses the same rating target schema as native toggle buttons.
    val legacyRating = queueLegacyRatingFingerprint(voteType).sourceMethod
    val extensionBuilderType = legacyRating.parameterTypes[1].toString()
    val builderType = context.classDefBy(extensionBuilderType).superclass!!
    val builderInstance = context.classDefBy(builderType).fields.filter { it.name == "instance" }
        .requireSingle("protobuf builder instance")
    val legacyRendererType = legacyRating.implementation!!.instructions
        .mapNotNull { it.getReference<FieldReference>() }.map { it.definingClass }.distinct().filter { type ->
            context.classDefBy(type).fields.any { it.type == ratingTargetType }
        }.requireSingle("legacy rating renderer")
    val legacyTarget = field(legacyRendererType, ratingTargetType)

    val controllerType = QueueControllerFingerprint.sourceClass.type
    val headerType = QueueHeaderFingerprint.sourceClass.type
    val modelType = QueueModelFingerprint.sourceClass.type
    val modelPanel = field(modelType, panelType)
    val modelVideo = QueueModelFingerprint.sourceMethod.findFieldFromToString("EngagementPanelPlaylistPanelModel{videoId=")
    val modelPlaylist = QueueModelFingerprint.sourceMethod.findFieldFromToString(", playlistId=")
    val controllerModel = field(controllerType, modelType)
    val collapsedView = field(controllerType, "Landroid/view/View;")
    val nativeCommit = QueueCommitFingerprint.sourceMethod
    val backendType = nativeCommit.definingClass
    val intentType = nativeCommit.parameterTypes.single().toString()
    val descriptorType = nativeCommit.returnType
    val navigatorRefresh = QueueNavigatorFingerprint.sourceMethod
    val navigatorType = navigatorRefresh.definingClass
    val responseType = navigatorRefresh.parameterTypes.single().toString()
    val backend = field(navigatorType, with(context) {
        classDefBy(backendType).interfaces.filter { type ->
            QueueNavigationContractFingerprint(intentType, descriptorType, nativeCommit.name)
                .matchOrNull(classDefBy(type)) != null
        }.requireSingle("navigation interface")
    })
    val nativeMode = field(navigatorType, "Z")
    private val navigatorConstructor = QueueNavigatorConstructorFingerprint(navigatorType, backendType).sourceMethod
    val queueManager = field(navigatorType, navigatorConstructor.parameterTypes[4].toString())
    val managerType = queueManager.type
    val switchBackend = QueueSwitchBackendFingerprint(navigatorType, backend.type).sourceMethod
    val queueCommit = with(context) {
        QueueNavigationContractFingerprint(intentType, descriptorType, nativeCommit.name)
            .match(classDefBy(navigatorType)).originalMethod
    }
    val queueAvailability = QueueNavigationAvailabilityFingerprint(navigatorType, intentType).sourceMethod
    val responsePanel = field(responseType, panelType)
    val watchEndpoint = field(responseType, rowSchema.field(7).type)
    val endpointType = watchEndpoint.type
    val descriptorEndpoint = field(descriptorType, endpointType)
    private val descriptorPayload = context.classDefBy(descriptorType).fields.filter {
        it.type != endpointType && !AccessFlags.STATIC.isSet(it.accessFlags) &&
            context.classDefByOrNull(it.type)?.let { payload ->
                with(context) { QueueMessageInfoFingerprint(payload.type).matchOrNull(payload) != null }
            } == true
    }.requireSingle("descriptor payload")
    private val descriptorVideoField = schema(descriptorPayload.type).field(1)
    val descriptorVideo = QueueDescriptorVideoFingerprint(descriptorType, descriptorVideoField).sourceMethod
    val panelRefresh = queuePanelRefreshFingerprint(controllerType, responseType, responsePanel).sourceMethod
    private val modelCreationFingerprint = queueModelCreationFingerprint(modelType)
    private val modelCreation = modelCreationFingerprint.sourceMethod
    private fun responseIdentity(modelField: FieldReference): FieldReference {
        // Follow the labeled model field through its constructor and builder back to the
        // watch response. An unrelated String read or reordered assignment cannot swap IDs.
        val constructor = queueConstructorAssignmentFingerprint(modelField).sourceMethod
        val parameter = queueConstructorParameter(constructor, modelField)
        val creation = modelCreationFingerprint.matches.single()
        val signature = creation.instruction.getReference<MethodReference>()!!
        val argument = 1 + signature.parameterTypes.take(parameter).sumOf { if (it in listOf("J", "D")) 2 else 1 }
        val builderField = queueSourceField(modelCreation, creation.index,
            queueInvokeRegisters(creation.instruction)[argument])
        val identityFingerprint = queueResponseIdentityFingerprint(controllerType, responseType, builderField)
        val refresh = identityFingerprint.sourceMethod
        check(refresh.toString() == panelRefresh.toString()) { "Native queue identity builder differs from panel refresh" }
        val store = identityFingerprint.matches.single()
        val source = queueSourceField(refresh, store.index, (store.instruction as TwoRegisterInstruction).registerA)
        check(source.definingClass == responseType && source.type == "Ljava/lang/String;") {
            "Native queue identity must come from the committed watch response"
        }
        return source
    }
    val responseVideo = responseIdentity(modelVideo)
    val responsePlaylist = responseIdentity(modelPlaylist)
    val bindHeader = queueHeaderBindFingerprint(headerType, panelType).sourceMethod
    val responseNavigation = field(responseType, bindHeader.parameterTypes[1].toString())

    private fun intent(kind: String): FieldReference {
        val enumType = NavigationIntentEnumFingerprint.sourceClass.type
        val enumFingerprint = queueNavigationEnumValueFingerprint(enumType, kind)
        enumFingerprint.sourceMethod
        val enumMatches = enumFingerprint.matches
        val enumArguments = queueInvokeRegisters(enumMatches[1].instruction)
        check(enumArguments[1] == (enumMatches[0].instruction as OneRegisterInstruction).registerA &&
            enumArguments[0] == (enumMatches[2].instruction as OneRegisterInstruction).registerA) {
            "Native queue: navigation enum must store the instance constructed with $kind"
        }
        val enumField = enumMatches[2].instruction.getReference<FieldReference>()!!
        val intentFingerprint = queueNavigationIntentFingerprint(intentType, enumField)
        intentFingerprint.sourceMethod
        val intentMatches = intentFingerprint.matches
        val intentArguments = queueInvokeRegisters(intentMatches[1].instruction)
        check(intentArguments[1] == (intentMatches[0].instruction as OneRegisterInstruction).registerA &&
            intentArguments[0] == (intentMatches[2].instruction as OneRegisterInstruction).registerA) {
            "Native queue: intent must wrap the $kind enum instance"
        }
        return intentMatches[2].instruction.getReference<FieldReference>()!!
    }
    val nextIntent = intent("NEXT")
    val previousIntent = intent("PREVIOUS")
    private val nextAvailability = queueTransportAvailabilityFingerprint(nextIntent)
    private val previousAvailability = queueTransportAvailabilityFingerprint(previousIntent)
    val refreshNext = nextAvailability.sourceMethod
    val refreshPrevious = previousAvailability.sourceMethod
    val transportType = refreshNext.definingClass.also {
        check(it == refreshPrevious.definingClass) { "Native transport availability owners differ" }
    }
    private val transportConstructor = QueueTransportConstructorFingerprint(transportType).sourceMethod
    val transportPlayer = field(transportType, transportConstructor.parameterTypes[1].toString())
    val transportEvents = field(transportType, transportConstructor.parameterTypes[3].toString())
    val loadDescriptor = QueueLoadDescriptorFingerprint(transportPlayer.type, descriptorType).sourceMethod
    val next = queueTransportHandlerFingerprint(transportType, nextIntent).sourceMethod
    val previous = queueTransportHandlerFingerprint(transportType, previousIntent).sourceMethod
    val nextEvent = next.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }.filter { it.definingClass == transportEvents.type }
        .requireSingle("Next analytics event")
    val previousEvent = previous.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }.filter { it.definingClass == transportEvents.type }
        .requireSingle("Previous analytics event")
    val manualHandlers = listOf(next to 1, previous to 0, queueNextOverrideFingerprint(next).sourceMethod to 1)
    val transportUpdate = queueTransportUpdateFingerprint(refreshPrevious, refreshNext).sourceMethod
    private val transportInterface = with(context) {
        classDefBy(transportType).interfaces.filter { type ->
            QueueTransportContractFingerprint(next).matchOrNull(classDefBy(type)) != null
        }.requireSingle("player transport interface")
    }
    val registerTransport = queueTransportRegistrationFingerprint(transportInterface).sourceMethod

    val shuffle = QueueShuffleFingerprint.sourceMethod
    val shuffleType = shuffle.definingClass
    val shuffleConstructor = QueueShuffleConstructorFingerprint(shuffleType).sourceMethod
    val storageType = shuffleConstructor.parameterTypes[0].toString()
    val parserType = shuffleConstructor.parameterTypes[2].toString()
    val playbackType = shuffleConstructor.parameterTypes[1].toString()
    val trackingType = shuffleConstructor.parameterTypes[3].toString()
    val configType = shuffleConstructor.parameterTypes[4].toString()
    val storageConstructor = QueueStorageConstructorFingerprint(
        storageType, playbackType, parserType, configType
    ).sourceMethod
    val endpointParser = field(managerType, parserType)
    val queueConfig = field(managerType, configType)
    val playback = field(controllerType, playbackType)
    val tracking = field(controllerType, trackingType)
    val backendConstructor = QueueBackendConstructorFingerprint(backendType).sourceMethod
    val dispatcher = field(managerType, backendConstructor.parameterTypes[1].toString())
    val nativeQueue = field(backendType, backendConstructor.parameterTypes[0].toString())
    val shuffleMappings = context.classDefBy(shuffleType).fields.filter { it.type == "[I" }
        .toList().also { check(it.size == 2) { "Native shuffle must have successor and predecessor arrays" } }
    // The inherited populate implementation owns the storage interface; both sibling
    // manager methods take it, but only the actual switch performs a playback dispatch.
    private val populateType = context.classDefBy(context.classDefBy(shuffleType).superclass!!).superclass!!
    val populate = QueuePopulateFingerprint(populateType, responseType).sourceMethod
    private val populateConstructor = QueuePopulateConstructorFingerprint(populateType, parserType, configType).sourceMethod
    val storage = field(managerType, populateConstructor.parameterTypes[0].toString())
    val storageSwitch = QueueStorageSwitchFingerprint(managerType, storage.type, dispatcher.type).sourceMethod
    val preservePlayback = QueuePlaybackDispatchFingerprint(dispatcher.type).sourceMethod
    val repeat = QueueRepeatModeFingerprint(backendType).sourceMethod
    val repeatForward = QueueRepeatForwardFingerprint(navigatorType, repeat).sourceMethod
    val shuffleForward = QueueShuffleForwardFingerprint(navigatorType, shuffle).sourceMethod

    val headerPanel = field(headerType, panelType)
    val headerRoot = field(headerType, "Landroid/view/View;")
    val repeatButton = field(headerType, "Lcom/google/android/apps/youtube/app/watch/panel/ui/PlaylistLoopButtonView;")
    private val headerUpdateFingerprint = QueueHeaderUpdateFingerprint(headerType)
    val updateHeader = headerUpdateFingerprint.sourceMethod
    val shuffleButton = headerUpdateFingerprint.matches.single().instruction.getReference<FieldReference>()!!
    private fun headerListener(view: FieldReference): FieldReference {
        val fingerprint = queueHeaderListenerFingerprint(view)
        fingerprint.sourceMethod
        val matches = fingerprint.matches
        val receiver = matches[0].instruction as TwoRegisterInstruction
        val listener = matches[1].instruction as TwoRegisterInstruction
        val call = queueInvokeRegisters(matches[2].instruction)
        check(call == listOf(receiver.registerA, listener.registerA)) { "Native header listener targets a different view" }
        return listener.getReference<FieldReference>()!!
    }
    val repeatClick = headerListener(repeatButton)
    val shuffleClick = headerListener(shuffleButton)
    private val hiddenControlsFingerprint = queueHiddenHeaderControlsFingerprint(headerType, panelType)
    private val hiddenControls = hiddenControlsFingerprint.sourceMethod.let {
        check(it.toString() == bindHeader.toString()) { "Native hide-controls flag differs from header bind" }
        hiddenControlsFingerprint.matches.last().instruction.getReference<FieldReference>()!!
    }
    // All three capabilities are enabled together. Their write order is irrelevant;
    // exclude the hide-controls flag by its panel-field source instead of by position.
    val headerCapabilities = bindHeader.fieldReferences(Opcode.IPUT_BOOLEAN).filter { it.toString() != hiddenControls.toString() }
        .also { check(it.size == 3) { "Native header must expose three playback capabilities" } }

    val adapterType = context.classDefBy(controllerType).fields.map { it.type }.filter { type ->
        context.classDefByOrNull(type)?.fields?.any { it.type == panelType } == true &&
            with(context) { QueueAdapterRowsFingerprint(type).matchOrNull(classDefBy(type)) != null }
    }.requireSingle("playlist adapter")
    val adapterPanel = field(adapterType, panelType)
    val gestures = QueueAdapterGesturesFingerprint(adapterType).sourceMethod
    private val gestureCasts = gestures.implementation!!.instructions
        .mapNotNull { it.getReference<TypeReference>() }.map { it.type }.distinct()
    val moveEventType = gestureCasts.filter { type ->
        context.classDefByOrNull(type)?.let { event ->
            with(context) { QueueMoveEventConstructorFingerprint(type).matchOrNull(event) != null }
        } == true
    }.requireSingle("playlist move event")
    val eventItems = context.classDefBy(moveEventType).fields.filter { field ->
        context.classDefBy(adapterType).fields.any { it.type == field.type } && field.type != "I"
    }.requireSingle("move event source")
    val adapterItems = gestures.fieldReferences(Opcode.IGET_OBJECT, eventItems.type)
        .filter { it.definingClass == adapterType }.requireSingle("editable adapter items")
    private val moveConstructor = QueueMoveEventConstructorFingerprint(moveEventType).sourceMethod
    val moveFrom = queueConstructorField(moveConstructor, 2)
    val moveTo = queueConstructorField(moveConstructor, 3)
    val removalType = gestureCasts.filter { type ->
        context.classDefByOrNull(type)?.fields?.count() == 1 && context.classDefBy(type).fields.single().type == "Ljava/lang/Object;"
    }.requireSingle("hide enclosing row event")
    val removed = field(removalType, "Ljava/lang/Object;")
    val rowFactory = QueueAdapterRowsFingerprint(adapterType).sourceMethod
    val rowStyle = rowFactory.fieldReferences(Opcode.IGET, "I").filter { it.definingClass == adapterType }
        .requireSingle("native row style")
    val rowPresenterType = QueueRowFingerprint.sourceClass.type
    private val rowBindFingerprint = queueRowBindFingerprint(rowPresenterType, entryId)
    val rowBind = rowBindFingerprint.sourceMethod
    val rowModelType = rowBind.implementation!!.instructions
        .mapNotNull { it.getReference<TypeReference>() }.map { it.type }.filter { type ->
            context.classDefByOrNull(type)?.fields?.count() == 1 && context.classDefBy(type).fields.single().type == rowType
        }.requireSingle("native row wrapper")
    val wrapper = field(rowModelType, rowType)
    val rowEntry = rowBindFingerprint.matches.last().let { store ->
        val read = rowBindFingerprint.matches.first().instruction as TwoRegisterInstruction
        check((store.instruction as TwoRegisterInstruction).registerA == read.registerA) {
            "Native row entry must retain the ID read from its row model"
        }
        store.instruction.getReference<FieldReference>()!!
    }
    private val rowViewFingerprint = QueueRowViewFingerprint(rowPresenterType)
    val rowView = rowViewFingerprint.sourceMethod.let {
        rowViewFingerprint.matches.single().instruction.getReference<FieldReference>()!!
    }
    val editableRow = QueueEditableRowFingerprint.sourceMethod

    val setModel = QueueSetPanelModelFingerprint(controllerType, modelType).sourceMethod
    val contextUpdate = QueuePlaybackContextFingerprint(controllerType).sourceMethod
    val identityFields = contextUpdate.fieldReferences(Opcode.IPUT_OBJECT, "Ljava/lang/String;")
        .filter { it.definingClass == controllerType }.also { check(it.size == 3) }
    val descriptorUpdate = QueueDescriptorUpdateFingerprint(controllerType, descriptorType).sourceMethod
    val visibility = queuePanelVisibilityFingerprint(controllerType).sourceMethod
}

/** Trace a constructor/model assignment through register moves; never guess from field order. */
internal fun queueSourceField(method: Method, before: Int, register: Int): FieldReference {
    var source = register
    val code = method.implementation!!.instructions.toList()
    for (index in before - 1 downTo 0) {
        val instruction = code[index]
        if (!instruction.opcode.setsRegister() || (instruction as? OneRegisterInstruction)?.registerA != source) continue
        when (instruction.opcode) {
            Opcode.IGET_OBJECT -> return instruction.getReference<FieldReference>()!!
            Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16 ->
                source = (instruction as TwoRegisterInstruction).registerB
            Opcode.CHECK_CAST -> Unit
            else -> throw PatchException("Native queue: assignment source overwritten in $method")
        }
    }
    throw PatchException("Native queue: assignment source is not a field in $method")
}

internal fun queueInvokeRegisters(instruction: Instruction): List<Int> = when (instruction) {
    is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD, instruction.registerE,
        instruction.registerF, instruction.registerG).take(instruction.registerCount)
    is RegisterRangeInstruction ->
        (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
    else -> throw PatchException("Native queue: expected a method invocation")
}

internal fun queueConstructorParameter(constructor: Method, field: FieldReference): Int {
    val firstRegister = constructor.p0Register
    val store = constructor.implementation!!.instructions.filterIsInstance<TwoRegisterInstruction>().single {
        it.getReference<FieldReference>().toString() == field.toString() &&
            it.opcode in listOf(Opcode.IPUT_OBJECT, Opcode.IPUT, Opcode.IPUT_BOOLEAN)
    }
    check(store.registerB == firstRegister) { "Native queue constructor field belongs to another receiver" }
    var register = firstRegister + 1
    for ((index, type) in constructor.parameterTypes.withIndex()) {
        if (register == store.registerA) return index
        register += if (type in listOf("J", "D")) 2 else 1
    }
    throw PatchException("Native queue: field is not assigned from a constructor parameter: $field")
}

internal fun queueConstructorField(constructor: Method, parameter: Int): FieldReference {
    val receiver = constructor.p0Register
    val register = receiver + 1 + constructor.parameterTypes.take(parameter).sumOf { if (it in listOf("J", "D")) 2 else 1 }
    return constructor.implementation!!.instructions.filterIsInstance<TwoRegisterInstruction>()
        .filter { it.opcode in listOf(Opcode.IPUT_OBJECT, Opcode.IPUT, Opcode.IPUT_BOOLEAN) &&
            it.registerA == register && it.registerB == receiver }
        .map { it.getReference<FieldReference>()!! }
        .distinctBy { it.toString() }.single()
}
