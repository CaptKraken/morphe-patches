/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.cloneParameters
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFieldFromToString
import app.morphe.util.findFreeRegister
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** PoC: supply local rows to the stock mutable queue; its sequencer owns navigation. */
internal fun installNativeQueueEngine(
    context: BytecodePatchContext,
    serviceType: String,
    descriptorType: String,
    commandType: String,
    builderConstructor: MethodReference,
    commandField: FieldReference,
    buildDescriptor: MethodReference
) = with(context) {
    val queueMatch = NativeQueueDescriptorFingerprint.matchSingle()
    val queueType = queueMatch.originalClassDef.type
    val requestType = queueMatch.originalMethod.parameterTypes.single().toString()
    check(queueMatch.originalMethod.returnType == descriptorType)
    val queueClass = queueMatch.originalClassDef
    val navigatorType = queueClass.interfaces.single { type -> classDefBy(type).methods.any {
        it.returnType == descriptorType && it.parameterTypes == listOf(requestType)
    } }
    val repeatType = queueClass.interfaces.single { type -> classDefBy(type).methods.any {
        it.parameterTypes == listOf("I") && it.returnType == "V"
    } }
    val shuffleType = queueClass.interfaces.single { it != repeatType && it != navigatorType }
    val wrapper = nativeQueueWrapperFingerprint(navigatorType, repeatType, shuffleType).matchSingle().originalClassDef
    val wrapperType = wrapper.type
    val nativeNavigator = wrapper.fields.single { it.type == navigatorType }
    val accept = queueClass.methods.single { method ->
        method.returnType == "V" && method.parameterTypes.size == 1 &&
                method.implementation?.instructions?.any {
                    it.opcode == Opcode.IPUT_OBJECT &&
                            it.getReference<FieldReference>()?.type == method.parameterTypes.single()
                } == true
    }
    val responseType = accept.parameterTypes.single().toString()
    val storedResponse = accept.implementation!!.instructions.mapNotNull { it.getReference<FieldReference>() }
        .single { it.definingClass == queueType && it.type == responseType }
    val receive = Fingerprint(definingClass = wrapperType, parameters = listOf(responseType), returnType = "V")
        .matchSingle()
    val containerFactory = receive.originalMethod.implementation!!.instructions.mapNotNull {
        it.getReference<MethodReference>()
    }.single { it.returnType == navigatorType && it.parameterTypes == listOf(descriptorType) }
    val container = wrapper.fields.single { it.type == containerFactory.definingClass }
    val response = wrapper.fields.single { it.type == responseType }
    val nativeQueueFlag = receive.originalMethod.implementation!!.instructions.mapNotNull {
        if (it.opcode == Opcode.IPUT_BOOLEAN) it.getReference<FieldReference>() else null
    }.distinct().single()
    val switchNavigator = wrapper.methods.single {
        it.parameterTypes == listOf(navigatorType, navigatorType) && it.returnType == "V"
    }
    val queueDelegate = queueMatch.originalMethod.implementation!!.instructions.mapNotNull {
        it.getReference<MethodReference>()
    }.single { it.returnType == descriptorType && it.parameterTypes == listOf(requestType) }
    val queueOwner = queueClass.fields.single { it.type == queueDelegate.definingClass }
    val queueInterface = queueOwner.type
    val selectionBase = NativeQueueSelectionFingerprint.matchSingle().originalClassDef.type
    val mutableQueue = Fingerprint(name = "<init>", custom = { _, type ->
        type.interfaces.contains(shuffleType) && generateSequence(type.superclass) {
            classDefByOrNull(it)?.superclass
        }.any { it == selectionBase }
    }).matchSingle().originalClassDef
    val mutableType = mutableQueue.type
    val localLoad = mutableQueue.methods.single {
        it.returnType == requestType && it.parameterTypes.firstOrNull() == descriptorType
    }
    val playlistId = localLoad.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
        .single { it.definingClass == descriptorType && it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty() }
    val repeatGetter = classDefBy(repeatType).methods.single { it.parameterTypes.isEmpty() && it.returnType == "I" }
    val repeatSetter = classDefBy(repeatType).methods.single { it.parameterTypes == listOf("I") && it.returnType == "V" }
    val position = classDefBy(queueInterface).interfaces.flatMap { classDefBy(it).methods }
        .single { it.parameterTypes.isEmpty() && it.returnType == "I" }
    val responseClass = classDefBy(responseType)
    val responseConstructor = responseClass.methods.single { it.name == "<init>" && it.parameterTypes.size == 1 &&
            it.parameterTypes.single() != "Landroid/os/Parcel;" }
    val sourceResponse = responseClass.fields.single { it.type == responseConstructor.parameterTypes.single() }
    val watchCommand = responseClass.fields.single { it.type == commandType }
    val panelType = PlaylistModelDescriptionFingerprint.matchSingle().originalMethod
        .findFieldFromToString(", playlistPanel=").type
    val responsePanel = responseClass.fields.single { it.type == panelType }
    val panelDefault = classDefBy(panelType).fields.single {
        it.type == panelType && AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val parser = generateSequence(classDefBy(panelType)) { it.superclass?.let(::classDefByOrNull) }
        .firstNotNullOf { type -> Fingerprint(definingClass = type.type, name = "parseFrom",
            parameters = listOf(type.type, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"),
            returnType = type.type).originalMethodOrNull }
    val editCompletion = queueClass.methods.single { it.parameterTypes == listOf("Ljava/lang/Object;", "Z") }
    val refresh = editCompletion.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
        .single { it.definingClass == queueType && it.parameterTypes == listOf("Z") }
    val localFlag = ImmutableField(wrapperType, "patch_localQueue", "Z", AccessFlags.PRIVATE.value,
        null, null, null).toMutable()
    val localItemsFlag = ImmutableField(mutableType, "patch_localQueue", "Z", AccessFlags.PUBLIC.value,
        null, null, null).toMutable()
    mutableClassDefBy(wrapperType).fields.add(localFlag)
    mutableClassDefBy(mutableType).fields.add(localItemsFlag)
    val playbackInterface = EXTENSION_CLASS.removeSuffix(";") + "\$NativePlayback;"
    mutableClassDefBy(wrapperType).interfaces.add(playbackInterface)

    fun bridge(type: String, name: String, parameters: List<String>, result: String, locals: Int, body: String) {
        mutableClassDefBy(type).methods.add(ImmutableMethod(type, name,
            parameters.map { ImmutableMethodParameter(it, null, null) }, result,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(locals + parameters.size + 1)).toMutable().apply {
            addInstructionsWithLabels(0, body)
        })
    }
    bridge(wrapperType, "patch_isLocalQueue", emptyList(), "Z", 1, """
        iget-boolean v0, p0, $localFlag
        return v0
    """)
    // Copy the response envelope. Other native consumers retain the untouched server model.
    bridge(wrapperType, "patch_localPanel", listOf("[B"), responseType, 4, """
        iget-object v1, p0, $response
        if-eqz v1, :missing
        iget-object v1, v1, $sourceResponse
        new-instance v0, $responseType
        invoke-direct {v0, v1}, $responseConstructor
        sget-object v1, $panelDefault
        invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
        move-result-object v2
        invoke-static {v1, p1, v2}, $parser
        move-result-object v1
        iput-object v1, v0, $responsePanel
        return-object v0
        :missing
        const/4 v0, 0x0
        return-object v0
    """)
    bridge(wrapperType, "patch_attachQueue", listOf("[B"), "Z", 7, """
        invoke-virtual {p0, p1}, $wrapperType->patch_localPanel([B)$responseType
        move-result-object v0
        if-eqz v0, :missing
        iget-object v1, p0, $nativeNavigator
        invoke-interface {v1}, $repeatGetter
        move-result v6
        new-instance v2, ${builderConstructor.definingClass}
        invoke-direct {v2}, $builderConstructor
        iget-object v3, v0, $watchCommand
        iput-object v3, v2, $commandField
        invoke-virtual {v2}, $buildDescriptor
        move-result-object v2
        invoke-virtual {v2}, $playlistId
        move-result-object v3
        invoke-virtual {v3}, Ljava/lang/String;->isEmpty()Z
        move-result v3
        if-eqz v3, :missing
        instance-of v3, v1, $queueType
        if-nez v3, :existing
        iget-object v3, p0, $container
        invoke-virtual {v3, v2}, $containerFactory
        move-result-object v3
        goto :populate
        :existing
        move-object v3, v1
        :populate
        check-cast v3, $queueType
        iget-object v4, v3, $queueOwner
        check-cast v4, $mutableType
        invoke-virtual {v3, v0}, $accept
        const/4 v5, 0x1
        iput-boolean v5, v4, $localItemsFlag
        invoke-direct {p0, v1, v3}, $switchNavigator
        iput-boolean v5, p0, $nativeQueueFlag
        iput-boolean v5, p0, $localFlag
        invoke-interface {v3, v6}, $repeatSetter
        invoke-virtual {v3, v5}, $refresh
        return v5
        :missing
        const/4 v0, 0x0
        return v0
    """)
    bridge(wrapperType, "patch_updateSnapshot", listOf("[B"), "V", 2, """
        iget-boolean v0, p0, $localFlag
        if-eqz v0, :done
        invoke-virtual {p0, p1}, $wrapperType->patch_localPanel([B)$responseType
        move-result-object v0
        iget-object v1, p0, $nativeNavigator
        check-cast v1, $queueType
        invoke-virtual {v1, v0}, $queueType->patch_updateSnapshot($responseType)V
        :done
        return-void
    """)
    bridge(queueType, "patch_updateSnapshot", listOf(responseType), "V", 0, """
        iput-object p1, p0, $storedResponse
        return-void
    """)
    for ((name, method) in listOf("patch_getSelectedIndex" to position, "patch_getRepeatMode" to repeatGetter)) {
        val target = if (name == "patch_getSelectedIndex") "iget-object v0, v0, $queueOwner" else ""
        bridge(wrapperType, name, emptyList(), "I", 1, """
            iget-object v0, p0, $nativeNavigator
            check-cast v0, $queueType
            $target
            invoke-interface {v0}, $method
            move-result v0
            return v0
        """)
    }
    // Required native response feed, separate from the engagement-panel presentation feed.
    // The native parser reuses its receiver register for the delegated navigator.
    // Preserve parameter registers before adding the post-response ownership callback.
    val receiveMethod = receive.method.cloneParameters(mutableClassDefBy(wrapperType))
    receiveMethod.findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
        receiveMethod.addInstruction(index,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onNativeQueueResponse($playbackInterface)V")
    }
    val receiveRegister = receiveMethod.findFreeRegister(0)
    receiveMethod.addInstructionsWithLabels(0, """
        iget-boolean v$receiveRegister, p0, $localFlag
        if-eqz v$receiveRegister, :server_queue
        iput-object p1, p0, $response
        return-void
        :server_queue
        nop
    """)
    val autoplay = wrapper.methods.single { method -> method.returnType == "V" && method.parameterTypes.size == 1 &&
            method.parameterTypes != listOf(responseType) &&
            method.implementation?.instructions?.any { it.getReference<MethodReference>()?.toString() == switchNavigator.toString() } == true }
    var autoplayMethod = mutableClassDefBy(wrapperType).methods.single { it.toString() == autoplay.toString() }
    if (autoplayMethod.implementation!!.registerCount <= 2) autoplayMethod = autoplayMethod.cloneParameters()
    val autoplayRegister = autoplayMethod.findFreeRegister(0)
    autoplayMethod.addInstructionsWithLabels(0, """
        iget-boolean v$autoplayRegister, p0, $localFlag
        if-eqz v$autoplayRegister, :server_autoplay
        return-void
        :server_autoplay
        nop
    """)
    val queueDispose = queueClass.methods.single { it.parameterTypes.isEmpty() && it.returnType == "V" &&
            it.implementation?.instructions?.any { instruction -> instruction.getReference<MethodReference>()?.let {
                it.definingClass == "Ljava/lang/ref/WeakReference;" && it.name == "get"
            } == true } == true }
    bridge(queueType, "patch_releaseLocalQueue", emptyList(), "V", 2, """
        iget-object v0, p0, $queueOwner
        instance-of v1, v0, $mutableType
        if-eqz v1, :done
        check-cast v0, $mutableType
        const/4 v1, 0x0
        iput-boolean v1, v0, $localItemsFlag
        :done
        return-void
    """)
    mutableClassDefBy(queueType).methods.single { it.toString() == queueDispose.toString() }.addInstruction(0,
        "invoke-virtual/range {p0 .. p0}, $queueType->patch_releaseLocalQueue()V")
    val dispose = classDefBy(navigatorType).methods.single { it.name == queueDispose.name }
    val wrapperDispose = wrapper.methods.single { it.parameterTypes.isEmpty() && it.returnType == "V" &&
            it.implementation?.instructions?.any { instruction ->
                instruction.getReference<MethodReference>()?.toString() == dispose.toString()
            } == true }
    val disposeMethod = mutableClassDefBy(wrapperType).methods.single { it.toString() == wrapperDispose.toString() }
    val disposeRegister = disposeMethod.findFreeRegister(0)
    disposeMethod.addInstructionsWithLabels(0, """
        const/4 v$disposeRegister, 0x0
        iput-boolean v$disposeRegister, p0, $localFlag
    """)
    // Local row loads must be JUMP within the native queue, rather than new ordinary playback.
    val insert = localLoad
    val requestConstructor = classDefBy(requestType).methods.single { it.name == "<init>" && it.parameterTypes.size == 3 }
    val intentType = requestConstructor.parameterTypes.first().toString()
    val jump = playlistNavigationIntentFingerprint(intentType, "JUMP").matchSingle().instructionMatches.last()
        .instruction.getReference<FieldReference>()!!
    var insertMethod = mutableClassDefBy(mutableType).methods.single { it.toString() == insert.toString() }
    insertMethod = insertMethod.cloneParameters()
    // Only one native local is needed: the request result occupies it after the flag check.
    val insertRegister = insertMethod.findFreeRegister(0)
    bridge(mutableType, "patch_localJump", listOf(descriptorType, requestConstructor.parameterTypes.last().toString()),
        requestType, 2, """
        new-instance v0, $requestType
        sget-object v1, $jump
        invoke-direct {v0, v1, p1, p2}, $requestConstructor
        return-object v0
    """)
    insertMethod.addInstructionsWithLabels(0, """
        iget-boolean v$insertRegister, p0, $localItemsFlag
        if-eqz v$insertRegister, :account_insert
        invoke-virtual {p0, p1, p2}, $mutableType->patch_localJump(${insert.parameterTypes.joinToString("")})$requestType
        move-result-object v$insertRegister
        return-object v$insertRegister
        :account_insert
        nop
    """)
    val loader = nativeQueueLoaderFingerprint(serviceType, descriptorType).matchSingle().originalMethod
    val sequencerOwner = loader.implementation!!.instructions.firstNotNullOf { it.getReference<FieldReference>() }
    val serviceLoad = classDefBy(serviceType).methods.single { it.parameterTypes == loader.parameterTypes &&
            it.returnType == "V" && it.toString() != loader.toString() }
    val sequencerType = serviceLoad.implementation!!.instructions.filter { it.opcode == Opcode.CHECK_CAST }
        .mapNotNull { it.getReference<TypeReference>()?.type }.distinct().single { type ->
            classDefByOrNull(type)?.fields?.any { it.type == navigatorType } == true
        }
    val sequencerField = classDefBy(sequencerType).fields.single { it.type == navigatorType }
    val sequencer = Fingerprint(definingClass = serviceType, parameters = loader.parameterTypes.map { it.toString() },
        custom = { method, _ -> method.toString() == serviceLoad.toString() },
        filters = listOf(app.morphe.patcher.fieldAccess(definingClass = sequencerOwner.type, type = "Ljava/lang/Object;"),
            app.morphe.patcher.checkCast(sequencerType, app.morphe.patcher.InstructionLocation.MatchAfterWithin(6))))
        .matchSingle().instructionMatches.first().instruction.getReference<FieldReference>()!!
    bridge(serviceType, "patch_getQueuePlayback", emptyList(), playbackInterface, 1, """
        iget-object v0, p0, $sequencerOwner
        iget-object v0, v0, $sequencer
        if-eqz v0, :missing
        check-cast v0, $sequencerType
        iget-object v0, v0, $sequencerField
        instance-of p0, v0, $wrapperType
        if-eqz p0, :missing
        check-cast v0, $wrapperType
        return-object v0
        :missing
        const/4 v0, 0x0
        return-object v0
    """)
    // Selection can commit after the shared video-ID event. Observe native completion
    // without passing a receiver register that the native method may have reused.
    val refreshMethod = mutableClassDefBy(queueType).methods.single { it.toString() == refresh.toString() }
    refreshMethod.findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
        refreshMethod.addInstructionsAtControlFlowLabel(index,
            "invoke-static {}, $EXTENSION_CLASS->onNativeQueueSelection()V")
    }
}
