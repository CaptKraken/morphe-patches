/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3758
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.feedactions

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.patch
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.layout.flyout.flyoutPatch
import app.morphe.patches.youtube.misc.auth.authHookPatch
import app.morphe.patches.youtube.misc.commandrouter.commandRouterHookPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.feedcards.feedVideoCardsHookPatch
import app.morphe.patches.youtube.misc.feedcards.hookFeedVideoThumbnails
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.util.setExtensionIsPatchIncluded

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/FeedVideoActionsPatch;"

/** Shares thumbnail touches without selecting the Watch later feature. */
internal val feedVideoActionsTouchHookPatch = patch {
    dependsOn(sharedExtensionPatch, feedVideoCardsHookPatch)

    execute {
        hookFeedVideoThumbnails()
        FeedComponentHostTouchFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0, p1 }, $EXTENSION_CLASS->onComponentHostTouch(Lcom/facebook/litho/ComponentHost;Landroid/view/MotionEvent;)Z
                move-result v0
                if-eqz v0, :original
                return v0
                :original
                nop
            """
        )
    }
}

val feedVideoActionsPatch = patch(
    name = "Feed video actions",
    description = "Adds a Watch later button to video thumbnails in feeds, " +
            "and opens the video menu by tapping and holding anywhere on a video.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        addResourcesPatch,
        // Finds the video id of the menu, to learn the description of the menu button.
        flyoutPatch,
        // Finds the video of each card, to hide the button while it plays in the feed.
        feedVideoActionsTouchHookPatch,
        // Runs the commands of the Watch later button.
        commandRouterHookPatch,
        // Loads the videos of the Watch later playlist, to show if the video is saved.
        authHookPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.FEED.addPreferences(
            SwitchPreference("morphe_feed_long_press_menu", summary = true),
            SwitchPreference("morphe_feed_watch_later_button", summary = true),
        )

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
