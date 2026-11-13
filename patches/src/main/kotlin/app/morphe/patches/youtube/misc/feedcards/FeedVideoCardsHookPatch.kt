/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3758
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.misc.feedcards

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.shared.misc.litho.relayout.COMPONENT_HOST_CLASS
import app.morphe.patches.shared.misc.proto.hookElement
import app.morphe.patches.youtube.layout.originaltitles.hookCompactVideoTitle
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playertype.playerTypeHookPatch
import app.morphe.patches.youtube.misc.proto.elementProtoParserHookPatch
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.videoid.hookVideoId
import app.morphe.patches.youtube.video.videoid.videoIdPatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.findFieldFromToString
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/utils/FeedVideoCardsPatch;"

/**
 * Litho is not obfuscated, and the method overrides the framework,
 * so this matches by name on every supported app version.
 */
internal object FeedCardComponentHostLayoutFingerprint : Fingerprint(
    definingClass = COMPONENT_HOST_CLASS,
    name = "onLayout",
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I")
)

/**
 * Finds the full width video cards of the feeds and the video of each card,
 * for the patches that draw on the thumbnails of feed videos.
 */
internal val feedVideoCardsHookPatch = patch(
    description = "Hooks the video cards of the feeds and finds their video ids.",
) {
    dependsOn(
        sharedExtensionPatch,
        elementProtoParserHookPatch,
        // What is drawn on a thumbnail is hidden while its video plays in the feed.
        playerTypeHookPatch,
        videoInformationPatch,
        videoIdPatch,
    )

    execute {
        // The elements of the videos include the thumbnail url with the video id,
        // and the texts of the video, which are matched to the texts of the laid out cards.
        hookElement("$EXTENSION_CLASS->onElementLoaded")

        // The next video that plays in the feed redraws the cards.
        hookVideoId("$EXTENSION_CLASS->onVideoLoaded(Ljava/lang/String;)V")

        // Feed videos are Litho components and have no views of their own,
        // so the root host of each video is found when it is laid out.
        FeedCardComponentHostLayoutFingerprint.method.apply {
            findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { index ->
                addInstruction(
                    index,
                    "invoke-static { p0 }, $EXTENSION_CLASS->" +
                            "onComponentHostLayout(Lcom/facebook/litho/ComponentHost;)V"
                )
            }
        }

        // Compact videos are not drawn by Litho, such as the videos of the watch history.
        hookCompactVideoTitle("$EXTENSION_CLASS->onCompactVideoBound")
    }
}

/** Shared by thumbnail actions, without requiring image hooks for count-only patches. */
internal fun hookFeedVideoThumbnails() {
    // Bind the exact mounted image to its selected source. Unlike card geometry, this
    // contract is shared by full-width and horizontal Elements templates.
    val requestSource = FeedImageRequestFingerprint.originalMethod
        .findFieldFromToString(", imageSource=")
    val mount = FeedImageMountFingerprint
    val imageMatch = mount.instructionMatches[0]
    val stateMatch = mount.instructionMatches[1]
    val requestMatch = mount.instructionMatches[2]
    val stateType = stateMatch.getInstruction<ReferenceInstruction>()
        .getReference<TypeReference>()!!.type
    check(requestMatch.fieldReference.definingClass == stateType &&
            requestMatch.getInstruction<TwoRegisterInstruction>().registerB ==
            stateMatch.getInstruction<OneRegisterInstruction>().registerA) {
        "Elements image request must be read from the mounted layout state"
    }
    check(requestMatch.fieldReference.type in setOf(requestSource.definingClass, "Ljava/lang/Object;")) {
        "Elements image layout state must contain its native request"
    }
    val imageRegister = imageMatch.getInstruction<OneRegisterInstruction>().registerA
    val requestRegister = requestMatch.getInstruction<OneRegisterInstruction>().registerA
    val instructions = mount.originalMethod.implementation!!.instructions.toList()
    check(imageRegister <= 15 && requestRegister <= 15 && imageRegister != requestRegister) {
        "Elements image mount needs distinct invoke registers"
    }
    check(instructions.subList(imageMatch.index + 1, requestMatch.index + 1).none {
        it.opcode.setsRegister() && (it as OneRegisterInstruction).registerA == imageRegister
    }) { "Elements image content was overwritten before its request was read" }
    val sourceUrl = instructions.mapNotNull {
        it.getReference<MethodReference>()
    }.filter {
        it.definingClass == requestSource.type && it.parameterTypes.isEmpty()
                && it.returnType == "Ljava/lang/String;"
    }.distinct().single()

    // Newer releases store the request as Object. Check its native type in the helper.
    // Native getters remain here; the extension receives only View and String.
    // Neither getter nor hook replaces an image request or a listener.
    val bindImage = ImmutableMethod(
        mount.originalMethod.definingClass,
        "patch_bindFeedImage",
        listOf(
            ImmutableMethodParameter("Landroid/view/View;", null, null),
            ImmutableMethodParameter("Ljava/lang/Object;", null, null)
        ),
        "V",
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        null, null, MutableMethodImplementation(4)
    ).toMutable().apply {
        addInstructions(
            0,
            """
                const/4 v0, 0x0
                instance-of v1, p1, ${requestSource.definingClass}
                if-eqz v1, :bound
                check-cast p1, ${requestSource.definingClass}
                iget-object v1, p1, $requestSource
                if-eqz v1, :bound
                invoke-interface { v1 }, $sourceUrl
                move-result-object v0
                :bound
                invoke-static { p0, v0 }, $EXTENSION_CLASS->onImageBound(Landroid/view/View;Ljava/lang/String;)V
                return-void
            """
        )
    }
    mount.classDef.methods.add(bindImage)
    mount.method.apply {
        // These are the live registers at the matched read, before either is reused.
        addInstruction(
            requestMatch.index + 1,
            "invoke-static { v$imageRegister, v$requestRegister }, $bindImage"
        )
        // A failed native source resolution must not retain a recycled image's old id.
        addInstruction(
            imageMatch.index + 1,
            "invoke-static { v$imageRegister }, $EXTENSION_CLASS->onImageUnbound(Landroid/view/View;)V"
        )
    }
    FeedImageUnmountFingerprint.let {
        val image = it.instructionMatches.first()
        val register = image.getInstruction<OneRegisterInstruction>().registerA
        it.method.addInstruction(
            image.index + 1,
            "invoke-static { v$register }, $EXTENSION_CLASS->onImageUnbound(Landroid/view/View;)V"
        )
    }
}
