/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getMutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Typed host bridges and hooks; discovery has finished before this installer runs. */
internal class NativeQueueHooks(
    private val context: BytecodePatchContext,
    private val native: NativeQueueReferences,
) {
    fun install() {
        // Observe the shared helper before it updates PlayerType.current. This preserves the
        // distinction between an actual close and an already closed/startup player state.
        context.mutableClassDefBy("Lapp/morphe/extension/youtube/patches/PlayerTypeHookPatch;")
            .methods.single { it.name == "setPlayerType" }.addInstructions(0,
                "invoke-static {p0}, $NATIVE_QUEUE_EXTENSION->onPlayerType(Ljava/lang/Enum;)V")
        installRatings()
        installMetadata()
        installResponseAccess()
        installTransport()
        installStorage()
        installHeader()
        installPanelEdits()
    }

    private fun target(reference: MethodReference) = with(context) { reference.getMutableMethod() }

    private fun field(reference: FieldReference, writable: Boolean = false): String {
        val member = context.mutableClassDefBy(reference.definingClass).fields.single {
            it.toString() == reference.toString()
        }
        member.accessFlags = (member.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value).inv()) or
            AccessFlags.PUBLIC.value
        if (writable) member.accessFlags = member.accessFlags and AccessFlags.FINAL.value.inv()
        return member.toString()
    }

    private fun bridge(name: String, body: String) {
        val owner = context.mutableClassDefBy(NATIVE_QUEUE_BRIDGE)
        val target = owner.methods.single { it.name == name }
        owner.methods.remove(target)
        owner.methods.add(ImmutableMethod(
            NATIVE_QUEUE_BRIDGE, name, target.parameters, target.returnType, target.accessFlags,
            target.annotations, target.hiddenApiRestrictions, MutableMethodImplementation(12)
        ).toMutable().apply { addInstructions(0, body) })
    }

    private fun getter(name: String, member: FieldReference) {
        val reference = field(member)
        bridge(name, """
            check-cast p0, ${member.definingClass}
            iget-object v0, p0, $reference
            return-object v0
        """)
    }

    private fun setter(name: String, member: FieldReference) {
        val reference = field(member, true)
        bridge(name, """
            check-cast p0, ${member.definingClass}
            check-cast p1, ${member.type}
            iput-object p1, p0, $reference
            return-void
        """)
    }

    private fun installRatings() {
        // Both native global account-provider implementations are present in this APK.
        // Observe only their current identity, not per-request account overrides or tokens.
        for (account in native.currentAccounts) {
            val currentAccount = target(account)
            val returns = currentAccount.findInstructionIndicesReversedOrThrow(Opcode.RETURN_OBJECT)
            for (index in returns) {
                val register = currentAccount.getInstruction<OneRegisterInstruction>(index).registerA
                currentAccount.addInstructions(index,
                    "invoke-static/range {v$register .. v$register}, $NATIVE_QUEUE_EXTENSION->onRatingAccount(Ljava/lang/Object;)V")
            }
        }

        // Native LikeStatusEntity keys are account scoped by the presenter's entity store.
        val makeRatingKey = target(native.makeRatingKey)
        bridge("ratingEntityKey", """
            const/16 v0, 0x3e
            invoke-static {v0, p0}, $makeRatingKey
            move-result-object v0
            return-object v0
        """)
        val voteNumber = field(native.voteNumber)
        bridge("ratingNumber", """
            check-cast p0, ${native.voteType}
            if-eqz p0, :unknown
            iget v0, p0, $voteNumber
            return v0
            :unknown
            const/4 v0, -0x1
            return v0
        """)
        val legacyBuilder = field(native.builderInstance)
        val legacyTarget = field(native.legacyTarget)
        val ratingVideo = field(native.ratingVideo)
        bridge("legacyRatingVideo", """
            check-cast p0, ${native.extensionBuilderType}
            iget-object v0, p0, $legacyBuilder
            instance-of v1, v0, ${native.legacyRendererType}
            if-eqz v1, :empty
            check-cast v0, ${native.legacyRendererType}
            iget-object v0, v0, $legacyTarget
            if-eqz v0, :empty
            iget-object v0, v0, $ratingVideo
            return-object v0
            :empty
            const-string v0, ""
            return-object v0
        """)
        val ratingEntity = field(native.ratingEntity)
        val ratingEntityKey = field(native.ratingEntityKey)
        val ratingEntityStatus = field(native.ratingEntityStatus)
        val keyType = target(native.keyNumber)
        val keyVideo = target(native.keyVideo)
        bridge("entityRatingVideo", """
            check-cast p0, ${native.entityType}
            iget-object v0, p0, $ratingEntity
            iget-object v0, v0, $ratingEntityKey
            invoke-static {v0}, $keyType
            move-result v1
            const/16 v2, 0x3e
            if-ne v1, v2, :empty
            invoke-static {v0}, $keyVideo
            move-result-object v0
            return-object v0
            :empty
            const-string v0, ""
            return-object v0
        """)
        bridge("entityRatingNumber", """
            check-cast p0, ${native.entityType}
            iget-object v0, p0, $ratingEntity
            iget v0, v0, $ratingEntityStatus
            return v0
        """)
        target(native.entityStatus).addInstructions(0,
            "invoke-static/range {p0 .. p0}, $NATIVE_QUEUE_EXTENSION->onEntityRating(Ljava/lang/Object;)V")
        target(native.legacyRating).addInstructions(0,
            "invoke-static/range {p1 .. p2}, $NATIVE_QUEUE_EXTENSION->onLegacyRating(Ljava/lang/Object;Ljava/lang/Object;)V")
        target(native.voteEventConstructor).addInstructions(1,
            "invoke-static/range {p1 .. p2}, $NATIVE_QUEUE_EXTENSION->onVoteEvent(Ljava/lang/String;Ljava/lang/Object;)V")

        // Register only the native button models that belong to our parsed panels. The host
        // still creates, binds and recycles the presenter and performs its normal entity subscriptions.
        val panelRows = field(native.panelRows)
        val wrappedRow = field(native.wrappedRow)
        val rowActions = field(native.rowActions)
        val ratingExtension = field(native.ratingExtension)
        val hasExtension = target(native.hasExtension)
        val getExtension = target(native.getExtension)
        bridge("panelRatingButtons", """
            check-cast p0, ${native.panelType}
            new-instance v0, Ljava/util/ArrayList;
            invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
            iget-object v1, p0, $panelRows
            invoke-interface {v1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
            move-result-object v1
            :row
            invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
            move-result v2
            if-eqz v2, :done
            invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v2
            check-cast v2, ${native.rowWrapperType}
            iget-object v2, v2, $wrappedRow
            if-eqz v2, :row
            iget-object v2, v2, $rowActions
            invoke-interface {v2}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
            move-result-object v2
            :button
            invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
            move-result v3
            if-eqz v3, :row
            invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v3
            check-cast v3, ${native.buttonWrapperType}
            sget-object v4, $ratingExtension
            invoke-virtual {v3, v4}, $hasExtension
            move-result v5
            if-eqz v5, :button
            invoke-virtual {v3, v4}, $getExtension
            move-result-object v3
            invoke-interface {v0, v3}, Ljava/util/List;->add(Ljava/lang/Object;)Z
            goto :button
            :done
            return-object v0
        """)
        val ratingEvents = field(native.ratingEvents)
        val subscribeRatingEvents = target(native.subscribeRatingEvents)
        bridge("registerRatingEvents", """
            check-cast p0, ${native.ratingPresenterType}
            iget-object v0, p0, $ratingEvents
            invoke-virtual {v0, p0}, $subscribeRatingEvents
            return-void
        """)
        val ratingBind = target(native.ratingBind)
        check((ratingBind.implementation!!.instructions.first() as? ReferenceInstruction)?.reference
            .toString() == native.ratingSuperBind.toString()) { "Native rating presenter initialization changed" }
        ratingBind.addInstructions(1,
            "invoke-static/range {p0 .. p1}, $NATIVE_QUEUE_EXTENSION->onRatingPresenter(Ljava/lang/Object;Ljava/lang/Object;)V")
    }

    private fun installMetadata() {
        // The generic VideoInformation title hook is absent on this target. Capture the
        // title and ID from the same native response used for channel information instead.
        for ((name, reference) in listOf("playerVideo" to native.playerVideo,
            "playerTitle" to native.playerTitle, "playerChannel" to native.playerChannel)) {
            bridge(name, """
                check-cast p0, ${native.playerResponseType}
                if-eqz p0, :empty
                invoke-interface {p0}, $reference
                move-result-object v0
                return-object v0
                :empty
                const-string v0, ""
                return-object v0
            """)
        }
        target(native.playerMetadata).addInstructions(0,
            "invoke-static/range {p1 .. p1}, $NATIVE_QUEUE_EXTENSION->onPlayerMetadata(Ljava/lang/Object;)V")

        // The legacy History renderer carries structured title text; no label heuristics
        // or network request is needed for this menu path.
        val formattedText = target(native.formattedText)
        for ((name, member) in listOf("typedMenuTitle" to native.historyTitle,
            "typedMenuChannel" to native.historyChannel, "typedMenuViews" to native.historyViews,
            "typedMenuAge" to native.historyAge)) {
            val reference = field(member)
            bridge(name, """
            instance-of v0, p0, ${native.historyType}
            if-eqz v0, :empty
            check-cast p0, ${native.historyType}
            iget-object v0, p0, $reference
            if-eqz v0, :empty
            invoke-static {v0}, $formattedText
            move-result-object v0
            if-eqz v0, :empty
            invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;
            move-result-object v0
            return-object v0
            :empty
            const-string v0, ""
            return-object v0
            """)
        }
    }

    private fun installResponseAccess() {
        // Decode using the host runtime and its extension registry. The protected parser is
        // called from a subclass, rather than relying on reflection or illegal DEX access.
        target(native.parseFrom)
        target(native.generatedRegistry)
        val defaultPanel = field(native.defaultPanel)
        context.mutableClassDefBy(native.panelType).methods.add(ImmutableMethod(
            native.panelType, "patch_parseLocalQueue", listOf(ImmutableMethodParameter("[B", null, null)),
            "Ljava/lang/Object;", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null, null, MutableMethodImplementation(4)
        ).toMutable().apply {
            addInstructions(0, """
                sget-object v0, $defaultPanel
                invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
                move-result-object v1
                invoke-static {v0, p0, v1}, ${native.parseFrom}
                move-result-object v0
                return-object v0
            """)
        })
        bridge("parsePanel", """
            invoke-static {p0}, ${native.panelType}->patch_parseLocalQueue([B)Ljava/lang/Object;
            move-result-object v0
            return-object v0
        """)

        getter("responseVideo", native.responseVideo)
        val watchEndpoint = field(native.watchEndpoint)
        val descriptorEndpoint = field(native.descriptorEndpoint)
        val descriptorVideo = target(native.descriptorVideo)
        bridge("descriptorVideo", """
            check-cast p0, ${native.descriptorType}
            invoke-virtual {p0}, $descriptorVideo
            move-result-object v0
            return-object v0
        """)
        for ((name, owner, reference) in listOf(
            Triple("responseEndpoint", native.responseType, watchEndpoint),
            Triple("descriptorEndpoint", native.descriptorType, descriptorEndpoint)
        )) bridge(name, """
            check-cast p0, $owner
            iget-object v0, p0, $reference
            if-eqz v0, :empty
            invoke-virtual {v0}, ${native.endpointType}->toByteArray()[B
            move-result-object v0
            return-object v0
            :empty
            const/4 v0, 0x0
            return-object v0
        """)
        getter("responsePlaylist", native.responsePlaylist)
        getter("responsePanel", native.responsePanel)
        setter("setResponsePanel", native.responsePanel)
        getter("responseNavigation", native.responseNavigation)
        setter("setResponseNavigation", native.responseNavigation)
        getter("modelPanel", native.modelPanel)
        getter("modelVideo", native.modelVideo)
        getter("modelPlaylist", native.modelPlaylist)
        setter("setModelPanel", native.modelPanel)
        getter("adapterPanel", native.adapterPanel)
        val controllerModel = field(native.controllerModel)
        val modelPanel = field(native.modelPanel)
        bridge("controllerPanel", """
            check-cast p0, ${native.controllerType}
            iget-object v0, p0, $controllerModel
            if-eqz v0, :done
            iget-object v0, v0, $modelPanel
            :done
            return-object v0
        """)
        val collapsedView = field(native.collapsedView)
        bridge("openPanel", """
            check-cast p0, ${native.controllerType}
            iget-object v0, p0, $collapsedView
            invoke-virtual {v0}, Landroid/view/View;->performClick()Z
            return-void
        """)
    }

    private fun installTransport() {
        // The overlay has watch-response-specific Next/Previous delegates (apfj/apfk).
        // Those can remain attached even after the playback queue is replaced. Scope the
        // override to handlers actually registered with the overlay. The controls field
        // is normally an composite, rather than the overlay itself.
        val transportPlayer = field(native.transportPlayer)
        val transportEvents = field(native.transportEvents)
        val nextIntent = field(native.nextIntent)
        val previousIntent = field(native.previousIntent)
        val queueAvailability = target(native.queueAvailability)
        val queueCommit = target(native.queueCommit)
        val loadDescriptor = target(native.loadDescriptor)
        val nextEvent = target(native.nextEvent)
        val previousEvent = target(native.previousEvent)
        bridge("isPlayerTransport", """
            instance-of v0, p0, ${native.transportType}
            return v0
        """)
        bridge("canNavigateQueue", """
            check-cast p0, ${native.navigatorType}
            if-eqz p1, :previous
            sget-object v0, $nextIntent
            goto :check
            :previous
            sget-object v0, $previousIntent
            :check
            invoke-virtual {p0, v0}, $queueAvailability
            move-result v0
            const/4 v1, 0x2
            if-eq v0, v1, :available
            const/4 v0, 0x0
            return v0
            :available
            const/4 v0, 0x1
            return v0
        """)
        bridge("navigateQueue", """
            check-cast p0, ${native.transportType}
            check-cast p1, ${native.navigatorType}
            iget-object v0, p0, $transportEvents
            if-eqz p2, :previous
            if-eqz v0, :next_intent
            invoke-interface {v0}, $nextEvent
            :next_intent
            sget-object v0, $nextIntent
            goto :commit
            :previous
            if-eqz v0, :previous_intent
            invoke-interface {v0}, $previousEvent
            :previous_intent
            sget-object v0, $previousIntent
            :commit
            invoke-virtual {p1, v0}, $queueCommit
            move-result-object v1
            if-eqz v1, :missing
            iget-object v0, p0, $transportPlayer
            invoke-interface {v0, v1}, $loadDescriptor
            const/4 v0, 0x1
            return v0
            :missing
            const/4 v0, 0x0
            return v0
        """)

        val refreshNext = target(native.refreshNext)
        val refreshPrevious = target(native.refreshPrevious)
        for ((target, enabledFlag, callback) in listOf(
            Triple(refreshNext, field(native.nextEnabled), "nextEnabled"),
            Triple(refreshPrevious, field(native.previousEnabled), "previousEnabled")
        )) {
            val flagIndex = target.implementation!!.instructions.indexOfFirst {
                (it as? ReferenceInstruction)?.reference.toString() == enabledFlag
            }
            check(flagIndex >= 0) { "Overlay transport availability gate missing" }
            val oldFlag = target.getInstruction<TwoRegisterInstruction>(flagIndex).registerA
            val compare = target.getInstruction<TwoRegisterInstruction>(flagIndex + 1)
            val result = if (compare.registerA == oldFlag) compare.registerB else compare.registerA
            // Insert after the branch merge, so both native delegate and fallback use this.
            target.addInstructions(flagIndex + 1, """
                invoke-static {v$result, p0}, $NATIVE_QUEUE_EXTENSION->$callback(ZLjava/lang/Object;)Z
                move-result v$result
            """)
        }
        context.mutableClassDefBy(native.transportType).methods.add(ImmutableMethod(
            native.transportType, "patch_refreshLocalQueueControls", emptyList(), "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null, null, MutableMethodImplementation(1)
        ).toMutable().apply {
            addInstructions(0, """
                invoke-direct {p0}, $refreshNext
                invoke-direct {p0}, $refreshPrevious
                return-void
            """)
        })
        bridge("refreshTransport", """
            check-cast p0, ${native.transportType}
            invoke-virtual {p0}, ${native.transportType}->patch_refreshLocalQueueControls()V
            return-void
        """)
        target(native.registerTransport).addInstructions(0,
            "invoke-static/range {p1 .. p1}, $NATIVE_QUEUE_EXTENSION->onTransportReady(Ljava/lang/Object;)V")
        target(native.transportUpdate).addInstructions(0, """
            invoke-static {p0}, $NATIVE_QUEUE_EXTENSION->onTransportUpdate(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            nop
        """)
        // The subclass overrides Next and can open a recommendation without calling its parent.
        // Intercept before that override as well as the base Next/Previous handlers.
        for ((handler, forward) in native.manualHandlers) {
            target(handler).addInstructions(0, """
                const/4 v0, $forward
                invoke-static {p0, v0}, $NATIVE_QUEUE_EXTENSION->onManualNavigation(Ljava/lang/Object;Z)Z
                move-result v0
                if-eqz v0, :original
                return-void
                :original
                nop
            """)
        }
    }

    private fun installStorage() {
        val panelRefresh = target(native.panelRefresh)
        val navigatorRefresh = target(native.navigatorRefresh)
        val backend = field(native.backend, true)
        val nativeMode = field(native.nativeMode, true)
        val queueManager = field(native.queueManager)
        val storage = field(native.storage)
        val endpointParser = field(native.endpointParser)
        val queueConfig = field(native.queueConfig)
        val playback = field(native.playback)
        val tracking = field(native.tracking)
        val dispatcher = field(native.dispatcher)
        val storageConstructor = target(native.storageConstructor)
        val storageSwitch = target(native.storageSwitch)
        val shuffleConstructor = target(native.shuffleConstructor)
        val backendConstructor = target(native.backendConstructor)
        val populate = target(native.populate)
        // A new navigator alone still shares the manager's previous backing queue. Install
        // an isolated native playlist store so local rows load even when Premium is disabled.
        bridge("installStorage", """
            check-cast p0, ${native.navigatorType}
            check-cast p1, ${native.controllerType}
            check-cast p2, ${native.responseType}
            iget-object v0, p0, $queueManager
            iget-object v1, v0, $storage
            iget-object v2, p1, $playback
            iget-object v3, v0, $endpointParser
            iget-object v4, v0, $queueConfig
            new-instance v5, ${native.storageType}
            invoke-direct {v5, v2, v3, v4}, $storageConstructor
            move-object v3, v5
            iget-object v4, p1, $playback
            iget-object v5, v0, $endpointParser
            iget-object v6, p1, $tracking
            iget-object v7, v0, $queueConfig
            new-instance v2, ${native.shuffleType}
            invoke-direct/range {v2 .. v7}, $shuffleConstructor
            invoke-virtual {v2, p2}, $populate
            invoke-virtual {v0, v3}, $storageSwitch
            new-instance v3, ${native.backendType}
            iget-object v4, v0, $dispatcher
            invoke-direct {v3, v2, v4}, $backendConstructor
            invoke-virtual {p0, v3}, ${native.navigatorType}->patch_restoreLocalQueueBackend(${native.backend.type})V
            return-object v1
        """)
        bridge("restoreStorage", """
            check-cast p0, ${native.navigatorType}
            check-cast p1, ${native.storage.type}
            iget-object v0, p0, $queueManager
            invoke-virtual {v0, p1}, $storageSwitch
            return-void
        """)
        val switchBackend = target(native.switchBackend)
        context.mutableClassDefBy(native.navigatorType).methods.add(ImmutableMethod(
            native.navigatorType, "patch_restoreLocalQueueBackend", listOf(ImmutableMethodParameter(native.backend.type, null, null)),
            "V", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null, null, MutableMethodImplementation(4)
        ).toMutable().apply {
            addInstructions(0, """
                iget-object v0, p0, $backend
                invoke-direct {p0, v0, p1}, $switchBackend
                instance-of v0, p1, ${native.backendType}
                iput-boolean v0, p0, $nativeMode
                return-void
            """)
        })
        bridge("captureBackend", """
            check-cast p0, ${native.navigatorType}
            iget-object v0, p0, $backend
            const/4 v1, 0x0
            iput-boolean v1, p0, $nativeMode
            return-object v0
        """)
        bridge("restoreBackend", """
            check-cast p0, ${native.navigatorType}
            check-cast p1, ${native.backend.type}
            invoke-virtual {p0, p1}, ${native.navigatorType}->patch_restoreLocalQueueBackend(${native.backend.type})V
            return-void
        """)
        // Reuse the host's random successor/predecessor mapping. Invalidate it only when
        // row order changes or shuffle is newly enabled, never for selection/title updates.
        val nativeQueue = field(native.nativeQueue)
        val shuffledNext = field(native.shuffledNext, true)
        val shuffledPrevious = field(native.shuffledPrevious, true)
        val shuffle = target(native.shuffle)
        val repeat = target(native.repeat)
        bridge("setPlaybackModes", """
            check-cast p0, ${native.navigatorType}
            iget-object v0, p0, $backend
            check-cast v0, ${native.backendType}
            iget-object v1, v0, $nativeQueue
            check-cast v1, ${native.shuffleType}
            if-eqz p3, :apply
            const/4 v2, 0x0
            invoke-virtual {v1, v2}, $shuffle
            iput-object v2, v1, $shuffledNext
            iput-object v2, v1, $shuffledPrevious
            :apply
            invoke-virtual {v1, p2}, $shuffle
            invoke-virtual {v0, p1}, $repeat
            return-void
        """)
        for ((member, parameter, callback) in listOf(
            Triple(native.repeatForward, "I", "onRepeatRequested"),
            Triple(native.shuffleForward, "Z", "onShuffleRequested")
        )) {
            // Give these forwarding methods a spare result register; v0 originally aliases p0.
            val repeating = parameter == "I"
            val delegate = field(if (repeating) native.repeatDelegate else native.shuffleDelegate)
            val forward = if (repeating) native.repeatCall else native.shuffleCall
            val target = target(member)
            val name = target.name
            val owner = context.mutableClassDefBy(native.navigatorType)
            owner.methods.remove(target)
            owner.methods.add(ImmutableMethod(
                native.navigatorType, name, target.parameters, target.returnType, target.accessFlags,
                target.annotations, target.hiddenApiRestrictions, MutableMethodImplementation(3)
            ).toMutable().apply {
                addInstructions(0, """
                    invoke-static {p0, p1}, $NATIVE_QUEUE_EXTENSION->$callback(Ljava/lang/Object;$parameter)Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                    :original
                    iget-object v0, p0, $delegate
                    invoke-interface {v0, p1}, $forward
                    return-void
                """)
            })
        }
    }

    private fun installHeader() {
        val panelRefresh = target(native.panelRefresh)
        val navigatorRefresh = target(native.navigatorRefresh)
        // Retain the actual native views, click listeners, repeat-one drawable and analytics.
        getter("headerPanel", native.headerPanel)
        val headerRoot = field(native.headerRoot)
        val repeatButton = field(native.repeatButton)
        val shuffleButton = field(native.shuffleButton)
        val repeatClick = field(native.repeatClick)
        val shuffleClick = field(native.shuffleClick)
        val repeatAll = field(native.repeatAll, true)
        val repeatOne = field(native.repeatOne, true)
        val shuffleAllowed = field(native.shuffleAllowed, true)
        val updateHeader = target(native.updateHeader)
        bridge("enableHeaderControls", """
            check-cast p0, ${native.headerType}
            const/4 v0, 0x0
            iget-object v1, p0, $headerRoot
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            iget-object v1, p0, $repeatButton
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            iget-object v2, p0, $shuffleButton
            invoke-virtual {v2, v0}, Landroid/view/View;->setVisibility(I)V
            const/4 v0, 0x1
            iput-boolean v0, p0, $repeatAll
            iput-boolean v0, p0, $repeatOne
            iput-boolean v0, p0, $shuffleAllowed
            invoke-virtual {v1, v0}, Landroid/view/View;->setEnabled(Z)V
            invoke-virtual {v2, v0}, Landroid/view/View;->setEnabled(Z)V
            iget-object v0, p0, $repeatClick
            invoke-virtual {v1, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
            iget-object v0, p0, $shuffleClick
            invoke-virtual {v2, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
            invoke-virtual {p0, p1, p2}, $updateHeader
            return-void
        """)
        val bindHeader = target(native.bindHeader)
        for (index in bindHeader.findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID)) {
            bindHeader.addInstructions(index,
                "invoke-static/range {p0 .. p0}, $NATIVE_QUEUE_EXTENSION->onQueueHeader(Ljava/lang/Object;)V")
        }
        // A delayed global mode event must not overwrite the local header's selected states.
        updateHeader.addInstructions(0, """
            invoke-static {p1, p0}, $NATIVE_QUEUE_EXTENSION->repeatMode(ILjava/lang/Object;)I
            move-result p1
            invoke-static {p2, p0}, $NATIVE_QUEUE_EXTENSION->shuffleMode(ZLjava/lang/Object;)Z
            move-result p2
        """)
        for ((name, member) in listOf("refreshPanel" to panelRefresh, "refreshNavigator" to navigatorRefresh)) {
            bridge(name, """
                check-cast p0, ${member.definingClass}
                check-cast p1, ${native.responseType}
                invoke-virtual {p0, p1}, $member
                return-void
            """)
        }
    }

    private fun installPanelEdits() {
        val navigatorRefresh = target(native.navigatorRefresh)
        val adapterItems = field(native.adapterItems)
        val eventItems = field(native.eventItems)
        bridge("isMoveForAdapter", """
            check-cast p0, ${native.adapterType}
            check-cast p1, ${native.moveEventType}
            iget-object v0, p0, $adapterItems
            iget-object v1, p1, $eventItems
            const/4 v2, 0x0
            if-ne v0, v1, :done
            const/4 v2, 0x1
            :done
            return v2
        """)
        for ((name, member) in listOf("moveFrom" to native.moveFrom, "moveTo" to native.moveTo)) {
            val reference = field(member)
            bridge(name, """
                check-cast p0, ${native.moveEventType}
                iget v0, p0, $reference
                return v0
            """)
        }
        val removed = field(native.removed)
        val entryId = field(native.entryId)
        bridge("removedEntry", """
            check-cast p0, ${native.removalType}
            iget-object v0, p0, $removed
            instance-of v1, v0, ${native.rowType}
            if-eqz v1, :unknown
            check-cast v0, ${native.rowType}
            iget-object v0, v0, $entryId
            return-object v0
            :unknown
            const-string v0, ""
            return-object v0
        """)

        navigatorRefresh.addInstructions(0, "invoke-static/range {p0 .. p1}, $NATIVE_QUEUE_EXTENSION->onResponse(Ljava/lang/Object;Ljava/lang/Object;)V")
        target(native.descriptorUpdate).addInstructions(0,
            "invoke-static/range {p1 .. p1}, $NATIVE_QUEUE_EXTENSION->onPlaybackDescriptor(Ljava/lang/Object;)V")

        // Switching the native store always dispatches playback with stop=true, which stops the stream even if the
        // selected video is unchanged. Block playback dispatch only during our own edits.
        target(native.preservePlayback).addInstructions(0, """
            invoke-static {}, $NATIVE_QUEUE_EXTENSION->preservePlayback()Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            nop
        """)
        val setModel = target(native.setModel)
        setModel.addInstructions(0, "invoke-static/range {p0 .. p1}, $NATIVE_QUEUE_EXTENSION->onPanelModel(Ljava/lang/Object;Ljava/lang/Object;)V")

        // A local refresh must not be rejected because the preceding playback descriptor was
        // for a different video. The host normally checks this to discard stale responses.
        val identityFields = native.identityFields.map { field(it, true) }
        val refreshBridge = context.mutableClassDefBy(NATIVE_QUEUE_BRIDGE).methods.single { it.name == "refreshPanel" }
        refreshBridge.addInstructions(1, """
            const/4 v0, 0x0
            ${identityFields.joinToString("\n") { "iput-object v0, p0, $it" }}
        """)

        val playbackContext = target(native.contextUpdate)
        playbackContext.addInstructions(0, """
            invoke-static/range {p0 .. p3}, $NATIVE_QUEUE_EXTENSION->onPlaybackContext(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            nop
        """)
        val gestures = target(native.gestures)
        gestures.addInstructions(0, """
            move-object/from16 v0, p0
            move-object/from16 v1, p2
            move/from16 v2, p3
            invoke-static {v0, v1, v2}, $NATIVE_QUEUE_EXTENSION->onGesture(Ljava/lang/Object;Ljava/lang/Object;I)Z
            move-result v0
            if-eqz v0, :original
            const/4 v0, 0x0
            return-object v0
            :original
            nop
        """)

        // Use the editable compact native row. Other density variants do not implement drag.
        val rowFactory = target(native.rowFactory)
        val rowStyleField = field(native.rowStyle)
        val styleIndex = rowFactory.implementation!!.instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == rowStyleField
        }
        check(styleIndex >= 0) { "Native row style selection missing" }
        val styleRegister = rowFactory.getInstruction<TwoRegisterInstruction>(styleIndex).registerA
        rowFactory.addInstructions(styleIndex + 1, """
            invoke-static {v$styleRegister, p0}, $NATIVE_QUEUE_EXTENSION->rowStyle(ILjava/lang/Object;)I
            move-result v$styleRegister
        """)
        val rowBind = target(native.rowBind)
        val wrapper = field(native.wrapper)
        val rowEntry = field(native.rowEntry)
        val rowView = field(native.rowView)
        val localRow = "${native.rowPresenterType}->patch_isLocalQueueRow:Z"
        context.mutableClassDefBy(native.rowPresenterType).fields.add(ImmutableField(
            native.rowPresenterType, "patch_isLocalQueueRow", "Z", AccessFlags.PRIVATE.value,
            null, null, null
        ).toMutable())
        rowBind.addInstructions(0, """
            move-object/from16 v0, p2
            check-cast v0, ${native.rowModelType}
            iget-object v0, v0, $wrapper
            iget-object v0, v0, $entryId
            invoke-static {v0}, $NATIVE_QUEUE_EXTENSION->isLocalEntry(Ljava/lang/String;)Z
            move-result v0
            move-object/from16 v1, p0
            iput-boolean v0, v1, $localRow
        """)
        val returns = rowBind.findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID)
        for (index in returns) rowBind.addInstructions(index, """
            move-object/from16 v0, p0
            iget-object v1, v0, $rowEntry
            iget-object v0, v0, $rowView
            invoke-static {v0, v1}, $NATIVE_QUEUE_EXTENSION->onRowBound(Landroid/view/View;Ljava/lang/String;)V
        """)
        target(native.editableRow).addInstructions(0, """
            iget-boolean v0, p0, $localRow
            if-eqz v0, :original
            const/4 v0, 0x1
            return v0
            :original
            nop
        """)

        val visibility = target(native.visibility)
        val flagIndex = visibility.implementation!!.instructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
            reference?.toString() == native.visibilityGate.toString()
        }
        check(flagIndex >= 0) { "Native playlist visibility gate missing" }
        val flagRegister = visibility.getInstruction<OneRegisterInstruction>(flagIndex + 1).registerA
        visibility.addInstructions(flagIndex + 2, """
            invoke-static {v$flagRegister, p0}, $NATIVE_QUEUE_EXTENSION->allowPanel(ZLjava/lang/Object;)Z
            move-result v$flagRegister
        """)
        val switchIndex = navigatorRefresh.implementation!!.instructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
            reference?.toString() == native.navigatorGate.toString()
        }
        check(switchIndex >= 0) { "Native queue navigator switch missing" }
        val switchRegister = navigatorRefresh.getInstruction<OneRegisterInstruction>(switchIndex + 1).registerA
        navigatorRefresh.addInstructions(switchIndex + 2, """
            invoke-static {v$switchRegister, p1}, $NATIVE_QUEUE_EXTENSION->allowNavigator(ZLjava/lang/Object;)Z
            move-result v$switchRegister
        """)
    }
}
