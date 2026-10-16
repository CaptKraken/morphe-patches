/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import static app.morphe.extension.shared.StringRef.str;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.youtube.settings.Settings;

/** A native playlist entry beside the server-provided fullscreen quick actions. UI thread only. */
final class QueueFullscreenButton implements View.OnClickListener {
    // A button owns its listener; the registry must not keep an old player/activity alive.
    private static final List<WeakReference<QueueFullscreenButton>> buttons = new ArrayList<>();
    private final ViewGroup actionsContainer;
    private final View button;
    private final int buttonSizePx;
    private int originalEndMarginPx;
    private boolean spaceReserved;

    static void bind(ViewGroup actionsContainer, boolean active) {
        buttons.removeIf(reference -> reference.get() == null);
        for (WeakReference<QueueFullscreenButton> reference : buttons) {
            QueueFullscreenButton entry = reference.get();
            if (entry != null && entry.actionsContainer == actionsContainer) {
                entry.update(active);
                return;
            }
        }
        if (!(actionsContainer.getParent() instanceof FrameLayout parent)) return;
        QueueFullscreenButton entry = new QueueFullscreenButton(actionsContainer, parent);
        buttons.add(new WeakReference<>(entry));
        entry.update(active);
    }

    static void refresh(boolean active) {
        buttons.removeIf(reference -> reference.get() == null);
        for (WeakReference<QueueFullscreenButton> reference : buttons) {
            QueueFullscreenButton entry = reference.get();
            if (entry != null) entry.update(active);
        }
    }

    private QueueFullscreenButton(ViewGroup actionsContainer, FrameLayout parent) {
        this.actionsContainer = actionsContainer;
        buttonSizePx = ResourceUtils.getDimensionPixelSize("controls_overlay_action_button_size");
        button = LayoutInflater.from(actionsContainer.getContext()).inflate(ResourceUtils.getIdentifier(
                ResourceType.LAYOUT, "fullscreen_engagement_action_bar_button"), parent, false);
        ImageView icon = button.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "button_icon"));
        icon.setImageResource(ResourceUtils.getIdentifier(
                ResourceType.DRAWABLE, "yt_outline_list_play_arrow_white_24"));
        if (icon.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params) {
            params.setMarginEnd(0);
            icon.setLayoutParams(params);
        }
        button.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "button_text")).setVisibility(View.GONE);
        button.setContentDescription(str("morphe_local_queue_title"));
        button.setOnClickListener(this);
        button.setVisibility(View.GONE);
        parent.addView(button, new FrameLayout.LayoutParams(
                buttonSizePx, buttonSizePx, Gravity.END | Gravity.CENTER_VERTICAL));
    }

    private void update(boolean active) {
        boolean visible = active && !Settings.HIDE_QUICK_ACTIONS.get()
                && !Settings.HIDE_QUICK_ACTIONS_PLAYLIST_BUTTON.get();
        if (!(actionsContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)) return;
        if (visible != spaceReserved) {
            if (visible) originalEndMarginPx = params.getMarginEnd();
            params.setMarginEnd(visible ? originalEndMarginPx + buttonSizePx : originalEndMarginPx);
            actionsContainer.setLayoutParams(params);
            spaceReserved = visible;
        }
        // The native parent retains control of orientation, controls visibility and animation.
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View view) {
        NativeQueuePatch.showQueue();
    }
}
