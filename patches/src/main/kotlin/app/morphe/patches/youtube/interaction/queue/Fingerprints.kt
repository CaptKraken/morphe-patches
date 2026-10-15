/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string

// These are protobuf extension numbers, not resource IDs or obfuscated DEX symbols.
internal object QueuePanelExtensionFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(literal(50631000), methodCall(name = "newSingularGeneratedExtension")),
)

internal object QueueRatingExtensionFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(literal(124608045), methodCall(name = "newSingularGeneratedExtension")),
)

internal object QueueLikeEntityFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(string("LikeStatusEntityModel{")),
)

internal object QueueEntityKeyFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(string("/device/orientation"), string("/app/mdx")),
)

internal object QueueOldAccountStoreFingerprint : Fingerprint(
    filters = listOf(string("com/google/android/libraries/youtube/account/identity/DefaultIdentityStoreOld")),
)

internal object QueueAccountStoreFingerprint : Fingerprint(
    filters = listOf(string("com/google/android/libraries/youtube/account/identity/DefaultIdentityStoreLockFix")),
)

internal object QueueControllerFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(string("engagement-panel-playlist")),
)

internal object QueueHeaderFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.LAYOUT, "compact_playlist_panel_header")),
)

internal object QueueRowFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "playlist_panel_video_item"),
        resourceLiteral(ResourceType.ID, "swipe_layout"),
    ),
)

internal object QueueCommitFingerprint : Fingerprint(
    filters = listOf(string("commitIntentToNavigate for %sreturned NULL")),
)

internal object QueueNavigatorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(literal(45377877L)),
)

internal object QueueShuffleFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(methodCall(smali = "Ljava/util/Collections;->shuffle(Ljava/util/List;)V")),
)

internal object QueueEditableRowFingerprint : Fingerprint(
    classFingerprint = QueueRowFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(literal(45368623L)),
)

internal object QueueModelFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(string("EngagementPanelPlaylistPanelModel{videoId=")),
)
