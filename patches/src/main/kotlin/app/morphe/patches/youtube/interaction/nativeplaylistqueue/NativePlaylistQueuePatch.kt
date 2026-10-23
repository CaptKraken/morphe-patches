/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.fix.proto.fixProtoLibraryPatch
import app.morphe.patches.shared.misc.proto.hookElement
import app.morphe.patches.youtube.layout.flyout.flyoutPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playertype.playerTypeHookPatch
import app.morphe.patches.youtube.misc.playservice.is_21_15_or_greater
import app.morphe.patches.youtube.misc.playservice.versionCheckPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.hookVideoIntentContext
import app.morphe.patches.youtube.shared.openVideoIntentPatch
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.videoid.hookBackgroundPlayVideoId
import app.morphe.patches.youtube.video.videoid.hookVideoId
import app.morphe.patches.youtube.video.videoid.videoIdPatch
import app.morphe.util.setExtensionIsPatchIncluded
import java.util.logging.Logger

private const val EXTENSION_PACKAGE = "Lapp/morphe/extension/youtube/patches/queue"
internal const val EXTENSION_CLASS = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch;"
internal const val CONTROLLER_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$PlaylistController;"
internal const val NAVIGATOR_OWNER_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$NavigatorOwner;"
internal const val PLAYER_CONTROLS_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$PlayerControls;"
internal const val MEDIA_CONTROLS_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$MediaControls;"
internal const val NOTIFICATION_CONTROLS_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$NotificationControls;"
internal const val SERVICE_NAVIGATOR_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$ServiceNavigator;"
internal const val NATIVE_PLAYBACK_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$NativePlayback;"
internal const val REPEAT_HEADER_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$RepeatHeader;"
internal const val ROW_CLICK_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$RowClick;"
internal const val SWIPE_BACKGROUND_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$SwipeBackground;"
internal const val QUEUE_PANEL_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$QueuePanel;"
internal const val QUEUE_ROW_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$QueueRow;"
internal const val REORDER_ITEMS_INTERFACE = "$EXTENSION_PACKAGE/NativePlaylistQueuePatch\$ReorderItems;"
internal const val FULLSCREEN_ACTIONS_CLASS = "$EXTENSION_PACKAGE/QueueFullscreenActions;"
internal const val FULLSCREEN_CONTROLLER_INTERFACE = "$EXTENSION_PACKAGE/QueueFullscreenActions\$Controller;"
internal const val SAVE_ACTIONS_CLASS = "$EXTENSION_PACKAGE/QueueSaveActions;"
internal const val SAVE_PICKER_INTERFACE = "$EXTENSION_PACKAGE/QueueSaveActions\$Picker;"
internal const val SAVE_FEEDBACK_CLASS = "$EXTENSION_PACKAGE/QueueSaveFeedback;"
internal const val SAVE_CALLBACK_INTERFACE = "$EXTENSION_PACKAGE/QueueSaveFeedback\$Callback;"
internal const val SAVE_SELECTION_CLASS = "$EXTENSION_PACKAGE/QueueSaveFeedback\$Selection;"
internal const val UNDO_COMMAND_INTERFACE = "$EXTENSION_PACKAGE/QueueUndoActions\$Command;"

@Suppress("unused")
val nativePlaylistQueuePatch = bytecodePatch(
    name = "Native playlist queue",
    description = "Adds a temporary video queue using YouTube's playlist interface and playback controls. " +
            "Supports Play next/last, reordering, shuffle, repeat, fullscreen playlist controls, " +
            "saving the queue to a playlist, and Undo when clearing the queue or closing the player. " +
            "Morphe's Local queue takes priority when enabled. Experimental. " +
            "This patch works with YouTube 21.16.256 and newer.",
    default = true
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        flyoutPatch,
        videoInformationPatch,
        videoIdPatch,
        playerTypeHookPatch,
        fixProtoLibraryPatch,
        openVideoIntentPatch,
        versionCheckPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        // 20.x builds the native playlist queue differently.
        if (!is_21_15_or_greater) {
            return@execute Logger.getLogger(this::class.java.name).warning(
                "'Native playlist queue' requires YouTube 21.16+"
            )
        }

        hookVideoIntentContext(EXTENSION_CLASS, "prepareQueueStartContext")

        installPlaylistPanel(this)
        installRatingCapture(this)
        installPlaylistRemoval(this)
        installFullscreenPlaylist(this)

        hookElement("$EXTENSION_CLASS->onElementLoaded")
        hookVideoId("$EXTENSION_CLASS->onVideoChanged(Ljava/lang/String;)V")
        hookBackgroundPlayVideoId("$EXTENSION_CLASS->onVideoChanged(Ljava/lang/String;)V")
        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
