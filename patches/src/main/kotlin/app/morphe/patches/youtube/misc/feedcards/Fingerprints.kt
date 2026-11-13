/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.misc.feedcards

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patches.youtube.misc.playercontrols.ElementsImageComponentFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** The native request retains the image source chosen for the mounted ImageView. */
internal object FeedImageRequestFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string("GlideRequest{requestBuilder="), string(", imageSource=")),
    // Some APKs contain another Glide integration with the same diagnostics.
    // Only use the request read by the Elements image mount method.
    custom = { _, classDef ->
        ElementsImageComponentFingerprint.originalMethod.implementation!!.instructions.any {
            it.getReference<FieldReference>()?.type == classDef.type
                    || it.getReference<TypeReference>()?.type == classDef.type
        }
    }
)

internal object FeedImageMountFingerprint : Fingerprint(
    classFingerprint = ElementsImageComponentFingerprint,
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;", "L"),
    filters = listOf(
        checkCast("Landroid/widget/ImageView;"),
        opcode(Opcode.CHECK_CAST, location = MatchAfterWithin(30)),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterWithin(2))
    )
)

/** Unmount resets image readiness before Litho returns the ImageView to its content pool. */
internal object FeedImageUnmountFingerprint : Fingerprint(
    classFingerprint = ElementsImageComponentFingerprint,
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;"),
    filters = listOf(
        checkCast("Landroid/widget/ImageView;"),
        methodCall(smali = "Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V")
    )
)
