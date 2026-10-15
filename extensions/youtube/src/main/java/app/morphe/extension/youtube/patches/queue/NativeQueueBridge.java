/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue;

import java.util.List;

/** Bodies are replaced with type-checked host access during patching. No runtime reflection. */
public final class NativeQueueBridge {
    private NativeQueueBridge() {}
    public static String ratingEntityKey(String video) { return ""; }
    public static List<?> panelRatingButtons(Object panel) { return List.of(); }
    public static void registerRatingEvents(Object presenter) {}
    public static String legacyRatingVideo(Object builder) { return ""; }
    public static int ratingNumber(Object status) { return -1; }
    public static String entityRatingVideo(Object entity) { return ""; }
    public static int entityRatingNumber(Object entity) { return -1; }
    public static String playerVideo(Object playerResponse) { return ""; }
    public static String playerTitle(Object playerResponse) { return ""; }
    public static String playerChannel(Object playerResponse) { return ""; }
    public static String typedMenuTitle(Object renderer) { return ""; }
    public static String typedMenuChannel(Object renderer) { return ""; }
    public static String typedMenuViews(Object renderer) { return ""; }
    public static String typedMenuAge(Object renderer) { return ""; }
    public static Object parsePanel(byte[] bytes) { return null; }
    public static String responseVideo(Object response) { return ""; }
    public static String responsePlaylist(Object response) { return ""; }
    public static Object responsePanel(Object response) { return null; }
    public static byte[] responseEndpoint(Object response) { return null; }
    public static String descriptorVideo(Object descriptor) { return ""; }
    public static byte[] descriptorEndpoint(Object descriptor) { return null; }
    public static Object responseNavigation(Object response) { return null; }
    public static void setResponseNavigation(Object response, Object navigation) {}
    public static void setResponsePanel(Object response, Object panel) {}
    public static void refreshNavigator(Object navigator, Object response) {}
    public static Object captureBackend(Object navigator) { return null; }
    public static Object installStorage(Object navigator, Object controller, Object response) { return null; }
    public static void restoreStorage(Object navigator, Object storage) {}
    public static void restoreBackend(Object navigator, Object backend) {}
    public static void setPlaybackModes(Object navigator, int repeatMode, boolean shuffled,
                                        boolean resetShuffle) {}
    public static Object headerPanel(Object header) { return null; }
    public static void enableHeaderControls(Object header, int repeatMode, boolean shuffled) {}
    public static void refreshPanel(Object controller, Object response) {}
    public static void openPanel(Object controller) {}
    public static boolean isPlayerTransport(Object transport) { return false; }
    public static void refreshTransport(Object transport) {}
    public static boolean canNavigateQueue(Object navigator, boolean next) { return false; }
    public static boolean navigateQueue(Object transport, Object navigator, boolean next) { return false; }
    public static Object controllerPanel(Object controller) { return null; }
    public static Object modelPanel(Object model) { return null; }
    public static String modelVideo(Object model) { return ""; }
    public static String modelPlaylist(Object model) { return ""; }
    public static void setModelPanel(Object model, Object panel) {}
    public static Object adapterPanel(Object adapter) { return null; }
    public static boolean isMoveForAdapter(Object adapter, Object event) { return false; }
    public static int moveFrom(Object event) { return -1; }
    public static int moveTo(Object event) { return -1; }
    public static String removedEntry(Object event) { return ""; }
}
