/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val NAVIGATOR = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$PlaylistNavigator;"

/** Defer the original intent before either native service commits its target. */
internal fun installPlaylistPagingNavigation(
    context: BytecodePatchContext,
    type: String,
    requestType: String,
    enumType: String,
    navigate: MethodReference
) = with(context) {
    val intent = queueNavigationIntentFieldFingerprint(requestType, enumType)
        .instructionMatches.single().instruction.getReference<FieldReference>()!!
    val owner = mutableClassDefBy(type)
    owner.interfaces.add(NAVIGATOR)
    owner.addBridgeMethod(
        "patch_getPlaylistIntent",
        listOf("Ljava/lang/Object;"),
        "Ljava/lang/Enum;",
        1,
        """
            check-cast p1, $requestType
            iget-object v0, p1, $intent
            return-object v0
        """
    )
    owner.addBridgeMethod(
        "patch_resumePlaylistNavigation",
        listOf("Ljava/lang/Object;"),
        "V",
        0,
        """
            check-cast p1, $requestType
            invoke-virtual {p0, p1}, $navigate
            return-void
        """
    )
    var dispatch = owner.methods.single { it.name == navigate.name && it.parameterTypes == navigate.parameterTypes }
    // Shared autoplay/loop hooks can also prepend guards. Replaying the original
    // method rechecks those settings; this hook never substitutes a manual intent.
    dispatch = dispatch.cloneParameters()
    val result = dispatch.findFreeRegister(0)
    dispatch.addInstructionsAtControlFlowLabel(
        0,
        """
            invoke-static/range {p0 .. p1}, $EXTENSION_CLASS->deferPlaylistNavigation(${NAVIGATOR}Ljava/lang/Object;)Z
            move-result v$result
            if-eqz v$result, :native_navigation
            return-void

            :native_navigation
            nop
        """
    )
}

/** Keep source gaps out of native descriptor/prefetch lookups as well as button routing. */
internal fun installPlaylistPagingSequence(
    context: BytecodePatchContext,
    wrapperType: String,
    navigator: FieldReference,
    queueType: String,
    requestType: String,
    descriptorType: String,
    enumType: String
) = with(context) {
    val intent = queueNavigationIntentFieldFingerprint(requestType, enumType)
        .instructionMatches.single().instruction.getReference<FieldReference>()!!
    mutableClassDefBy(wrapperType).addBridgeMethod(
        "patch_getQueueSequence",
        emptyList(),
        "Ljava/lang/Object;",
        1,
        """
            iget-object v0, p0, $navigator
            return-object v0
        """
    )
    val descriptor = NativeQueueDescriptorFingerprint.method
    val commit = playlistQueueCommitFingerprint(queueType, requestType, descriptorType).method
    val availability = playlistQueueAvailabilityFingerprint(queueType, requestType).method
    mutableClassDefBy(queueType).addBridgeMethod(
        "patch_isPlaylistBoundary",
        listOf(requestType),
        "Z",
        1,
        """
            iget-object v0, p1, $intent
            invoke-static {p0, v0}, $EXTENSION_CLASS->isUnloadedPlaylistBoundary(Ljava/lang/Object;Ljava/lang/Enum;)Z
            move-result v0
            return v0
        """
    )
    for ((method, status) in listOf(descriptor to false, commit to false, availability to true)) {
        val target = method.cloneParameters()
        val result = target.findFreeRegister(0)
        // Use a small bridge so the original receiver/request survive register reuse.
        target.addInstructionsAtControlFlowLabel(
            0,
            """
                invoke-virtual/range {p0 .. p1}, $queueType->patch_isPlaylistBoundary($requestType)Z
                move-result v$result
                if-eqz v$result, :native_target
                const/4 v$result, ${if (status) "0x2" else "0x0"}
                ${if (status) "return" else "return-object"} v$result

                :native_target
                nop
            """
        )
    }
}
