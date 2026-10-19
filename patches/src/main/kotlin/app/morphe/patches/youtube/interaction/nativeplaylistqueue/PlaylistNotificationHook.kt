/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Notification skips must load through the service, not a deferred watch-screen endpoint. */
internal fun installPlaylistNotification(
    context: BytecodePatchContext,
    wrapperType: String,
    enumType: String,
    commandType: String
) = with(context) {
    val next = playlistNavigationIntentFingerprint(enumType, "NEXT").matchSingle()
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val nextRequest = playlistNavigationSingletonFingerprint(wrapperType, next).matchSingle()
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val callback = notificationSkipCallbackFingerprint("onSkipToNext").matchSingle().instructionMatches.last()
        .instruction.getReference<MethodReference>()!!
    // Ignore wrappers, casting callbacks and no-op listeners. Require the delegate
    // whose original next method contains the same native navigation singleton.
    val delegates = notificationDelegateFingerprint(callback).matchAll().map { match ->
        match to match.instructionMatches[1].instruction.getReference<MethodReference>()!!
    }.filter { (_, reference) ->
        classDefBy(reference.definingClass).methods.single { it.toString() == reference.toString() }
            .implementation?.instructions?.any { it.getReference<FieldReference>() == nextRequest } == true
    }.toList()
    check(delegates.size == 1) { "Native playlist queue: expected one notification Next delegate" }
    val (callbackDelegate, nextDelegate) = delegates.single()
    val owner = nextDelegate.definingClass
    val notification = notificationNextDispatchFingerprint(owner, nextRequest).matchSingle()
    check(notification.originalMethod.toString() == nextDelegate.toString()) {
        "Native playlist queue: media next callback and service dispatch must agree"
    }
    val foregroundCallback = notification.instructionMatches.first().instruction.getReference<FieldReference>()!!
    val foregroundType = notificationForegroundSkipFingerprint(foregroundCallback.type, nextRequest).matchSingle()
        .originalClassDef.type
    val previousCallback = notificationSkipCallbackFingerprint("onSkipToPrevious").matchSingle()
        .instructionMatches.last().instruction.getReference<MethodReference>()!!
    val previousDelegate = notificationDelegateFingerprint(previousCallback, callbackDelegate.originalClassDef.type)
        .matchSingle().instructionMatches[1].instruction.getReference<MethodReference>()!!
    check(previousDelegate.definingClass == owner) {
        "Native playlist queue: notification skips must share their media owner"
    }
    val previous = notificationPreviousDispatchFingerprint(previousDelegate).matchSingle()
    val previousForeground = previous.instructionMatches.first().instruction.getReference<FieldReference>()!!
    val previousIntent = playlistNavigationIntentFingerprint(enumType, "PREVIOUS").matchSingle()
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val previousRequest = playlistNavigationSingletonFingerprint(wrapperType, previousIntent).matchSingle()
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val previousForegroundType = notificationForegroundSkipFingerprint(previousForeground.type, previousRequest)
        .matchSingle().originalClassDef.type
    val provider = notification.instructionMatches[2].instruction.getReference<FieldReference>()!!
    val getProvider = notification.instructionMatches[3].instruction.getReference<MethodReference>()!!
    val serviceType = notification.instructionMatches[5].instruction.getReference<TypeReference>()!!.type
    val service = playlistServiceNavigatorFingerprint(wrapperType).matchSingle()
    val navigatorType = service.originalClassDef.type
    val playerFields = service.instructionMatches.take(3).map { it.instruction.getReference<FieldReference>()!! }
    val videoId = service.instructionMatches[4].instruction.getReference<MethodReference>()!!
    check(playerFields.first().definingClass == navigatorType &&
            playerFields.zipWithNext().all { (parent, child) -> parent.type == child.definingClass } &&
            playerFields.last().type == videoId.definingClass) {
        "Native playlist queue: current player must belong to the playback service"
    }
    val constructor = classDefBy(wrapperType).methods.single {
        it.name == "<init>" && it.parameterTypes.size == 3 && it.parameterTypes.first() == enumType
    }
    val descriptorType = constructor.parameterTypes[1].toString()
    val descriptor = playlistPlaybackDescriptorBuilderFingerprint(descriptorType).matchSingle()
    val builderType = descriptor.originalClassDef.type
    val builderConstructor = classDefBy(builderType).methods.single {
        it.name == "<init>" && it.parameterTypes.isEmpty()
    }
    val command = classDefBy(builderType).fields.single {
        AccessFlags.PUBLIC.isSet(it.accessFlags) && it.type == commandType
    }
    installNativeQueueEngine(context, navigatorType, descriptorType, commandType,
        builderConstructor, command, descriptor.originalMethod)
    val navigatorInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$ServiceNavigator;"
    val mediaInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$MediaControls;"
    fun bridge(type: String, name: String, parameters: List<String>, returnType: String, locals: Int, body: String) {
        mutableClassDefBy(type).methods.add(ImmutableMethod(type, name,
            parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(locals + parameters.size + 1)).toMutable().apply {
            addInstructionsWithLabels(0, body)
        })
    }
    mutableClassDefBy(navigatorType).interfaces.add(navigatorInterface)
    bridge(navigatorType, "patch_getVideoId", emptyList(), "Ljava/lang/String;", 1, """
        iget-object v0, p0, ${playerFields[0]}
        iget-object v0, v0, ${playerFields[1]}
        iget-object v0, v0, ${playerFields[2]}
        if-eqz v0, :missing
        invoke-interface {v0}, $videoId
        move-result-object v0
        return-object v0
        :missing
        const/4 v0, 0x0
        return-object v0
    """)
    val canNavigate = classDefBy(navigatorType).methods.single {
        it.returnType == "Z" && it.parameterTypes == listOf(wrapperType)
    }
    for ((name, resultType, target) in listOf(
        Triple("patch_navigateQueue", "V", service.originalMethod),
        Triple("patch_canNavigateQueue", "Z", canNavigate)
    )) {
        val result = if (resultType == "Z") "move-result v0\nreturn v0" else "return-void"
        bridge(navigatorType, name, listOf("Z"), resultType, 1, """
            if-eqz p1, :previous
            sget-object v0, $nextRequest
            goto :dispatch
            :previous
            sget-object v0, $previousRequest
            :dispatch
            invoke-virtual {p0, v0}, $target
            $result
        """)
    }
    mutableClassDefBy(owner).interfaces.add(mediaInterface)
    bridge(owner, "patch_getQueueNavigator", emptyList(), navigatorInterface, 2, """
        iget-object v0, p0, $foregroundCallback
        if-eqz v0, :previous
        instance-of v0, v0, $foregroundType
        if-eqz v0, :missing
        :previous
        iget-object v0, p0, $previousForeground
        if-eqz v0, :local
        instance-of v0, v0, $previousForegroundType
        if-eqz v0, :missing
        :local
        iget-object v0, p0, $provider
        invoke-interface {v0}, $getProvider
        move-result-object v0
        check-cast v0, $serviceType
        instance-of v1, v0, $navigatorInterface
        if-eqz v1, :missing
        check-cast v0, $navigatorInterface
        return-object v0
        :missing
        const/4 v0, 0x0
        return-object v0
    """)
    installPlaylistNotificationControls(context, owner)
    for ((match, hook) in listOf(notification to "onNotificationNext", previous to "onNotificationPrevious")) {
        val method = match.method
        val index = match.instructionMatches.first().index
        val result = method.findFreeRegister(index)
        method.addInstructionsAtControlFlowLabel(index, """
            invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->$hook($mediaInterface)Z
            move-result v$result
            if-eqz v$result, :native_skip
            return-void
            :native_skip
            nop
        """)
    }
}
