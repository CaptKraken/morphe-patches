/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.youtube.layout.flyout.addToQueuePatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.util.setExtensionIsPatchIncluded

internal const val NATIVE_QUEUE_EXTENSION = "Lapp/morphe/extension/youtube/patches/queue/NativeQueuePatch;"
internal const val NATIVE_QUEUE_BRIDGE = "Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;"

/**
 * Only the validated target is advertised. Resolve its native contract before installing hooks;
 * supporting another version also requires APK verification and on-device testing.
 */
@Suppress("unused")
val nativeQueuePatch = bytecodePatch(
    name = "Native temporary queue (experimental)",
    description = "Uses the native playlist panel for a temporary local queue. YouTube 21.20.405 only.",
    default = false,
) {
    dependsOn(addToQueuePatch)
    compatibleWith(Compatibility(
        name = requireNotNull(COMPATIBILITY_YOUTUBE.name),
        packageName = requireNotNull(COMPATIBILITY_YOUTUBE.packageName),
        apkFileType = COMPATIBILITY_YOUTUBE.apkFileType,
        signatures = COMPATIBILITY_YOUTUBE.signatures,
        appIconColor = COMPATIBILITY_YOUTUBE.appIconColor,
        targets = COMPATIBILITY_YOUTUBE.targets.filter { it.version == "21.20.405" }
    ))

    execute {
        if (packageMetadata.versionName != "21.20.405") {
            throw PatchException("Native temporary queue currently requires YouTube 21.20.405")
        }

        val references = NativeQueueReferences(this)
        NativeQueueHooks(this, references).install()
        setExtensionIsPatchIncluded(NATIVE_QUEUE_EXTENSION)
    }
}
