/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import com.google.protobuf.GeneratedMessageLite
import java.io.File
import java.net.URLClassLoader
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests compiled queue ownership and feedback timing across external Android boundaries. */
class QueueRuntimeBoundaryTest {
    @Test
    fun `excluded Morphe queue does not suppress native queue`() = withFixture("excludedPatch")

    @Test
    fun `disabled Local queue does not suppress native queue`() = withFixture("disabledSetting")

    @Test
    fun `handoff waits for setting cache and rejects late responses without restoring old session`() =
        withFixture("acceptedHandoff")

    @Test
    fun `failed detach retains ownership for retry but cancels pending work`() = withFixture("failedDetach")

    @Test
    fun `handoff never clears a foreign backend`() = withFixture("foreignBackend")

    @Test
    fun `Morphe priority cancels a pending first video start`() = withFixture("pendingStart")

    @Test
    fun `local Undo follows normal confirmation timing and replacement`() = withFixture("normalUndoTiming")

    @Test
    fun `local Undo follows accessible confirmation timing`() = withFixture("accessibleUndoTiming")

    @Test
    fun `closing an owned player restores the queue on a new backend at its saved position`() =
        withFixture("closedPlayerRestore")

    @Test
    fun `new playback and provider handoff invalidate closed player Undo`() = withFixture("closedPlayerSuperseded")

    @Test
    fun `foreign playback and unavailable position never create close Undo`() = withFixture("closedPlayerGuards")

    @Test
    fun `single row removal is silent and still protects the playing row`() = withFixture("singleRemovalSilent")

    @Test
    fun `menu Clear Undo reattaches without reopening or seeking playback`() = withFixture("menuClearUndo")

    // NativePlaylistQueuePatch depends on Android settings, scheduling and UI classes.
    // Isolate those boundaries while executing its actual compiled coordinator.
    private fun withFixture(scenario: String) {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val classes = File(root,
            "extensions/youtube/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val output = createTempDirectory("playlist-queue-runtime").toFile()
        // Only external settings/UI/scheduling boundaries are replaced. Queue state,
        // preference callback, cancellation, release, feedback and timeout guards are real.
        val stubs = mapOf(
            "SharedPreferences" to """
                package android.content;
                public interface SharedPreferences {
                    interface OnSharedPreferenceChangeListener {
                        void onSharedPreferenceChanged(SharedPreferences prefs, String key);
                    }
                }
            """,
            "Bundle" to "package android.os; public class Bundle {}",
            "AccessibilityManager" to """
                package android.view.accessibility;
                public class AccessibilityManager {
                    public boolean enabled, touchExploration;
                    public boolean isEnabled() { return enabled; }
                    public boolean isTouchExplorationEnabled() { return touchExploration; }
                }
            """,
            "Context" to """
                package android.content;
                public class Context {
                    public static final String ACCESSIBILITY_SERVICE = "accessibility";
                    public final android.view.accessibility.AccessibilityManager accessibility =
                        new android.view.accessibility.AccessibilityManager();
                    public Object getSystemService(String service) { return accessibility; }
                }
            """,
            "View" to "package android.view; public class View {}",
            "PlayerType" to """
                package app.morphe.extension.youtube.shared;
                public enum PlayerType {
                    NONE, HIDDEN, INLINE_MINIMAL, WATCH_WHILE_MINIMIZED;
                    public static PlayerType current = NONE;
                    public static PlayerType getCurrent() { return current; }
                    public boolean isNoneOrHidden() { return this == NONE || this == HIDDEN; }
                }
            """,
            "ShortsPlayerState" to """
                package app.morphe.extension.youtube.shared;
                public class ShortsPlayerState {
                    public static boolean open;
                    public static boolean isOpen() { return open; }
                }
            """,
            "VideoInformation" to """
                package app.morphe.extension.youtube.patches;
                public class VideoInformation {
                    public static String id = "abcdefghijk", playlist = "";
                    public static long time = 75432;
                    public static String getVideoId() { return id; }
                    public static String getPlaylistId() { return playlist; }
                    public static long getVideoTime() { return time; }
                }
            """,
            "LoadVideoPatch" to """
                package app.morphe.extension.youtube.patches;
                public class LoadVideoPatch {
                    public static String url;
                    public static int opens;
                    public static void openVideoIntent(String value, boolean dismiss) {
                        if (dismiss) throw new AssertionError("Undo must not dismiss a replacement player");
                        opens++; url = value;
                    }
                }
            """,
            "StringRef" to """
                package app.morphe.extension.shared;
                public class StringRef { public static String str(String key) { return key; } }
            """,
            "Base64" to """
                package android.util;
                public class Base64 {
                    public static final int NO_WRAP = 2;
                    public static String encodeToString(byte[] bytes, int flags) {
                        return java.util.Base64.getEncoder().encodeToString(bytes);
                    }
                }
            """,
            "Uri" to """
                package android.net;
                public class Uri {
                    public static String encode(String value) {
                        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            """,
            "RestoreOriginalTitlesPatch" to """
                package app.morphe.extension.youtube.patches.originaltitles;
                public class RestoreOriginalTitlesPatch {
                    public static String getTitle(String id, String title) { return title; }
                }
            """,
            "ChannelIdRequest" to """
                package app.morphe.extension.youtube.patches.utils.requests;
                public class ChannelIdRequest {
                    public static String getVideoTitleIfAvailable(String id) { return ""; }
                }
            """,
            "ByteTrieSearch" to """
                package app.morphe.extension.shared;
                public class ByteTrieSearch {
                    public ByteTrieSearch(byte[]... values) {}
                    public static byte[][] convertStringsToBytes(String... values) { return new byte[0][]; }
                }
            """,
            "Utils" to """
                package app.morphe.extension.shared;
                public class Utils {
                    private static final java.util.ArrayDeque<Runnable> pending = new java.util.ArrayDeque<>();
                    public static <K,V> java.util.Map<K,V> createSizeRestrictedMap(int size) {
                        return new java.util.LinkedHashMap<>();
                    }
                    private record Delayed(Runnable action, long when) {}
                    private static final java.util.List<Delayed> delayed = new java.util.ArrayList<>();
                    private static long now;
                    private static final android.content.Context context = new android.content.Context();
                    public static android.content.Context getContext() { return context; }
                    public static void runOnMainThreadDelayed(Runnable action, long delay) {
                        delayed.add(new Delayed(action, now + delay));
                    }
                    public static void advanceBy(long milliseconds) {
                        now += milliseconds;
                        for (var item : new java.util.ArrayList<>(delayed)) {
                            if (item.when() <= now) { delayed.remove(item); item.action().run(); }
                        }
                    }
                    public static void runOnMainThread(Runnable action) { pending.add(action); }
                    public static void runOnMainThreadNowOrLater(Runnable action) { action.run(); }
                    public static void runOnBackgroundThread(Runnable action) { /* Network boundary. */ }
                    public static int toasts;
                    public static void showToastShort(String text) { toasts++; }
                    public static void drain() { while (!pending.isEmpty()) pending.remove().run(); }
                }
            """,
            "Logger" to """
                package app.morphe.extension.shared;
                public class Logger {
                    public interface LogMessage { String buildMessageString(); }
                    public static int errors;
                    public static void printException(LogMessage message) { message.buildMessageString(); errors++; }
                    public static void printException(LogMessage message, Throwable e) { printException(message); }
                }
            """,
            "BooleanSetting" to """
                package app.morphe.extension.shared.settings;
                public class BooleanSetting {
                    public String key = "morphe_local_queue";
                    public boolean value;
                    public Boolean get() { return value; }
                }
            """,
            "Settings" to """
                package app.morphe.extension.youtube.settings;
                public class Settings {
                    public static final app.morphe.extension.shared.settings.BooleanSetting LOCAL_QUEUE =
                        new app.morphe.extension.shared.settings.BooleanSetting();
                }
            """,
            "AddToQueuePatch" to """
                package app.morphe.extension.youtube.patches;
                public class AddToQueuePatch {
                    public static boolean included;
                    public static boolean isPatchIncluded() { return included; }
                }
            """,
            "LocalQueuePatch" to """
                package app.morphe.extension.youtube.patches;
                public class LocalQueuePatch {
                    public static final java.util.List<String> savedItems = java.util.List.of("saved-video");
                    public static boolean isEnabled() {
                        return app.morphe.extension.youtube.settings.Settings.LOCAL_QUEUE.get();
                    }
                }
            """,
            "OpenVideosFullscreenHookPatch" to """
                package app.morphe.extension.youtube.patches;
                public class OpenVideosFullscreenHookPatch {
                    public static String minimized = "pending";
                    public static void keepVideoMinimized(String id) { minimized = id; }
                }
            """,
            "QueueFullscreenActions" to """
                package app.morphe.extension.youtube.patches.queue;
                public class QueueFullscreenActions {
                    public static int refreshes;
                    static void refresh() { refreshes++; }
                }
            """
        ).map { (name, source) -> File(output, "$name.java").apply { writeText(source.trimIndent()) } }
        val fixture = File(output, "RuntimeFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import android.content.SharedPreferences;
                import app.morphe.extension.shared.*;
                import app.morphe.extension.youtube.patches.*;
                import app.morphe.extension.youtube.settings.Settings;
                import java.lang.ref.WeakReference;
                import java.lang.reflect.*;
                import java.util.*;
                public class RuntimeFixture {
                    static Class<?> owner = NativePlaylistQueuePatch.class;
                    static Field field(String name) throws Exception {
                        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
                    }
                    static Object call(String name) throws Exception {
                        Method method = owner.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(null);
                    }
                    static void require(boolean value, String message) {
                        if (!value) throw new AssertionError(message);
                    }
                    static int clears;
                    static NativePlaylistQueuePatch.NativePlayback backend(boolean local, boolean clearSucceeds) {
                        return (NativePlaylistQueuePatch.NativePlayback) Proxy.newProxyInstance(owner.getClassLoader(),
                            new Class<?>[]{NativePlaylistQueuePatch.NativePlayback.class}, (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "patch_isLocalQueue": return local;
                                    case "patch_clearQueue": clears++; return clearSucceeds;
                                    default: throw new AssertionError("Unexpected playback operation: " + method.getName());
                                }
                            });
                    }
                    static PlaylistQueue install(NativePlaylistQueuePatch.NativePlayback playback) throws Exception {
                        PlaylistQueue queue = new PlaylistQueue("abcdefghijk", "Playing").playNext("ZYXWVUTSRQP");
                        field("queue").set(null, queue);
                        field("nativePlaybackRef").set(null, new WeakReference<>(playback));
                        field("publishedModel").set(null, new Object());
                        field("pendingQueueStart").set(null, new QueueStart(queue, ""));
                        Class<?> undo = Class.forName(owner.getName() + "${'$'}PendingUndo");
                        Constructor<?> create = undo.getDeclaredConstructors()[0]; create.setAccessible(true);
                        field("pendingUndo").set(null, create.newInstance("token", queue, new WeakReference<>(playback), null));
                        clears = 0; Logger.errors = 0; QueueFullscreenActions.refreshes = 0;
                        return queue;
                    }
                    static SharedPreferences.OnSharedPreferenceChangeListener listener() throws Exception {
                        return (SharedPreferences.OnSharedPreferenceChangeListener) field("QUEUE_SETTING_LISTENER").get(null);
                    }
                    static com.google.protobuf.MessageLite feedback(String token) {
                        return QueueUndoActions.feedback(token, "Queue cleared", "Undo")
                            .getAddToToastAction().getItem().getToastRenderer();
                    }
                    // These native boundaries record calls; they do not implement a
                    // second playback engine. The coordinator and serialized models are real.
                    static class Playback implements NativePlaylistQueuePatch.NativePlayback {
                        boolean local = true;
                        int clears, attaches, repeat = PlaylistQueue.REPEAT_ONE;
                        byte[] panel;
                        public boolean patch_isLocalQueue() { return local; }
                        public boolean patch_attachQueue(byte[] value) { attaches++; panel = value; local = true; return true; }
                        public void patch_updateSnapshot(byte[] value) { panel = value; }
                        public int patch_getSelectedIndex() { return 0; }
                        public int patch_getRepeatMode() { return repeat; }
                        public void patch_restoreQueueRepeat(int value) { repeat = value; }
                        public boolean patch_clearQueue() { clears++; local = false; return true; }
                    }
                    static class Controller implements NativePlaylistQueuePatch.PlaylistController {
                        final Playback playback;
                        final Object row = (NativePlaylistQueuePatch.QueueRow) () -> true;
                        final Object panel = (NativePlaylistQueuePatch.QueuePanel) () -> true;
                        boolean attached = true;
                        Object model = new Object();
                        byte[] feedback;
                        int feedbacks;
                        Controller(Playback value) { playback = value; }
                        public String patch_getVideoId() { return VideoInformation.id; }
                        public String patch_getPlaylistId() { return VideoInformation.playlist; }
                        public Object patch_getModel() { return model; }
                        public Object patch_getPanel() { return panel; }
                        public int patch_findQueueRow(Object target) { return target == row ? 1 : -1; }
                        public void patch_markQueueRows() {}
                        public boolean patch_isAttached() { return attached; }
                        public Object patch_getNavigator() {
                            return new NativePlaylistQueuePatch.ServiceNavigator() {
                                public String patch_getVideoId() { return VideoInformation.id; }
                                public NativePlaylistQueuePatch.NativePlayback patch_getQueuePlayback() { return playback; }
                                public void patch_navigateQueue(boolean next) { throw new AssertionError("Unexpected navigation"); }
                                public boolean patch_canNavigateQueue(boolean next) { return false; }
                            };
                        }
                        public Object patch_updatePanel(byte[] value) { model = new Object(); return model; }
                        public void patch_restorePanel(Object expected, Object original) {
                            require(model == expected, "Restore must only touch the accepted model"); model = original;
                        }
                        public void patch_showQueueFeedback(byte[] value) { feedbacks++; feedback = value; }
                    }
                    static PlaylistQueue installOwned(Controller controller) throws Exception {
                        var queue = new PlaylistQueue("abcdefghijk", "A").playLast("ZYXWVUTSRQP")
                            .playLast("abcdefghijk").select(2).withRepeatMode(PlaylistQueue.REPEAT_ONE);
                        field("queue").set(null, queue);
                        field("controllerRef").set(null, new WeakReference<>(controller));
                        field("nativePlaybackRef").set(null, new WeakReference<>(controller.playback));
                        field("publishedModel").set(null, controller.model);
                        field("originalModel").set(null, null);
                        field("pendingQueueStart").set(null, null);
                        field("pendingUndo").set(null, null);
                        app.morphe.extension.youtube.shared.PlayerType.current =
                            app.morphe.extension.youtube.shared.PlayerType.WATCH_WHILE_MINIMIZED;
                        return queue;
                    }
                    static com.google.protobuf.MessageLite closePlayer(Controller controller) throws Exception {
                        NativePlaylistQueuePatch.onQueuePlayerClosing();
                        require(controller.feedbacks == 0, "Confirmation must wait until native close runs");
                        controller.attached = false;
                        app.morphe.extension.youtube.shared.PlayerType.current =
                            app.morphe.extension.youtube.shared.PlayerType.NONE;
                        // Exercise actual lifecycle invalidation as the old watch screen disappears.
                        call("onPlaybackContextChanged"); Utils.drain();
                        require(controller.feedbacks == 1, "Close must show exactly one native confirmation");
                        var action = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .parseFrom(controller.feedback).getAddToToastAction().getItem().getToastRenderer();
                        require(action.getResponseText().getSimpleText().equals("morphe_native_playlist_queue_cleared"),
                            "Close must use the localized Queue cleared label");
                        require(QueueSaveFeedback.confirmationDuration(action) == 5000,
                            "Player-close Undo uses the same five-second presentation window");
                        return action.getActionButton().getButtonRenderer().getServiceEndpoint();
                    }
                    public static void closedPlayerRestore() throws Exception {
                        var old = new Controller(new Playback()); var before = installOwned(old);
                        var undo = closePlayer(old);
                        var accessible = Utils.getContext().accessibility;
                        accessible.enabled = true; accessible.touchExploration = true;
                        var toast = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .parseFrom(old.feedback).getAddToToastAction().getItem().getToastRenderer();
                        require(QueueSaveFeedback.confirmationDuration(toast) == 0,
                            "Accessible close Undo must retain the native twenty-second timeout");
                        Utils.advanceBy(10001);
                        require(old.playback.clears == 1 && field("queue").get(null) == null,
                            "Close must release the live local sequence");
                        require(((WeakReference<?>) field("nativePlaybackRef").get(null)).get() == null,
                            "Disposed playback must not be retained as the restore target");
                        require(NativePlaylistQueuePatch.onQueueUndoCommand(undo), "Native button must consume local Undo");
                        require(LoadVideoPatch.opens == 1 && LoadVideoPatch.url.equals(
                            "https://www.youtube.com/watch?v=abcdefghijk&t=75s"), "Reopen at the saved position");
                        var start = (QueueStart) field("pendingQueueStart").get(null);
                        require(start.candidate() == before && start.restoresClosedPlayer(),
                            "Restore must preserve the original selected occurrence and order");
                        call("tryStartQueue");
                        require(old.playback.attaches == 0 && field("queue").get(null) == null,
                            "Do not attach through the detached old controller");
                        var watch = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .newBuilder().setWatchEndpoint(app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
                            .WatchEndpoint.newBuilder().setVideoId("abcdefghijk")).build();
                        require(Boolean.TRUE.equals(NativePlaylistQueuePatch.prepareQueueStartContext(watch, Map.of())
                            .get("start_watch_minimized")), "Undo must reopen in the mini player");
                        var replacement = new Controller(new Playback()); replacement.playback.repeat = 0;
                        field("controllerRef").set(null, new WeakReference<>(replacement));
                        app.morphe.extension.youtube.shared.PlayerType.current =
                            app.morphe.extension.youtube.shared.PlayerType.WATCH_WHILE_MINIMIZED;
                        call("tryStartQueue");
                        require(replacement.playback.attaches == 1 && old.playback.attaches == 0,
                            "Attach the full restored queue only to the newly accepted backend");
                        var panel = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.Panel
                            .parseFrom(replacement.playback.panel);
                        require(panel.getItemsCount() == 3 && panel.getSelectedIndex() == 2,
                            "Native panel must retain the selected duplicate, not just the first video");
                        require(replacement.playback.repeat == PlaylistQueue.REPEAT_ONE,
                            "Restore repeat after the new backend attaches");
                        require(field("pendingQueueStart").get(null) == null && Utils.toasts == 0,
                            "Successful Undo must finish without an unrelated Added toast");
                        NativePlaylistQueuePatch.onQueueUndoCommand(undo);
                        require(LoadVideoPatch.opens == 1, "Undo can activate only once");
                    }
                    public static void closedPlayerSuperseded() throws Exception {
                        var controller = new Controller(new Playback()); installOwned(controller);
                        var undo = closePlayer(controller);
                        var watch = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .newBuilder().setWatchEndpoint(app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer
                            .WatchEndpoint.newBuilder().setVideoId("abcdefghijk")).build();
                        NativePlaylistQueuePatch.prepareQueueStartContext(watch, Map.of());
                        NativePlaylistQueuePatch.onQueueUndoCommand(undo);
                        require(LoadVideoPatch.opens == 0, "A new watch, even the same video, cancels old close Undo");
                        controller = new Controller(new Playback()); installOwned(controller);
                        undo = closePlayer(controller);
                        AddToQueuePatch.included = true; Settings.LOCAL_QUEUE.value = true;
                        call("yieldToMorpheQueue"); NativePlaylistQueuePatch.onQueueUndoCommand(undo);
                        require(LoadVideoPatch.opens == 0, "Morphe priority cancels closed queue Undo");
                    }
                    public static void closedPlayerGuards() throws Exception {
                        var controller = new Controller(new Playback()); var before = installOwned(controller);
                        app.morphe.extension.youtube.shared.PlayerType.current =
                            app.morphe.extension.youtube.shared.PlayerType.NONE;
                        call("onPlaybackContextChanged"); Utils.drain();
                        require(field("queue").get(null) == before && field("pendingUndo").get(null) == null
                            && controller.feedbacks == 0, "Backgrounding alone must not clear a live queue or show Undo");
                        app.morphe.extension.youtube.shared.PlayerType.current =
                            app.morphe.extension.youtube.shared.PlayerType.WATCH_WHILE_MINIMIZED;
                        VideoInformation.playlist = "PL-account";
                        NativePlaylistQueuePatch.onQueuePlayerClosing();
                        require(controller.playback.clears == 0 && field("queue").get(null) == before,
                            "Account playlist playback must fall through unchanged");
                        VideoInformation.playlist = ""; VideoInformation.time = -1;
                        NativePlaylistQueuePatch.onQueuePlayerClosing();
                        require(controller.playback.clears == 0 && field("pendingUndo").get(null) == null,
                            "Unknown playback position must not create misleading restoration feedback");
                        VideoInformation.time = 75432; controller.playback.local = false;
                        NativePlaylistQueuePatch.onQueuePlayerClosing();
                        require(controller.playback.clears == 0, "Never clear a foreign playback backend");
                    }
                    public static void singleRemovalSilent() throws Exception {
                        var controller = new Controller(new Playback()); installOwned(controller);
                        var hide = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .newBuilder().setHideEnclosingAction(app.morphe.extension.youtube.innertube.queue
                            .PlaylistQueueRenderer.HideEnclosingAction.newBuilder().setHack(true)).build();
                        require(NativePlaylistQueuePatch.onQueueMenuCommand(hide, Map.of(
                            "com.google.android.libraries.youtube.innertube.endpoint.tag", controller.row)) == null,
                            "Successful owned removal must consume native Hide without feedback");
                        var current = (PlaylistQueue) field("queue").get(null);
                        require(current.entries().size() == 2 && current.selectedIndex() == 1,
                            "Remove the requested row and keep the selected duplicate");
                        require(field("pendingUndo").get(null) == null && controller.feedbacks == 0 && Utils.toasts == 0,
                            "Single removal must be silent");
                        NativePlaylistQueuePatch.onQueueMenuCommand(hide, Map.of(
                            "com.google.android.libraries.youtube.innertube.endpoint.tag", controller.row));
                        require(field("queue").get(null) == current, "The playing row must still be protected");
                        require(NativePlaylistQueuePatch.onQueueMenuCommand(hide, Map.of()) == hide,
                            "Ordinary native removal must retain its original command");
                    }
                    public static void menuClearUndo() throws Exception {
                        var controller = new Controller(new Playback()); var before = installOwned(controller);
                        var hide = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .newBuilder().setHideEnclosingAction(app.morphe.extension.youtube.innertube.queue
                            .PlaylistQueueRenderer.HideEnclosingAction.newBuilder().setHack(true)).build();
                        var nativeCommand = (com.google.protobuf.MessageLite) Proxy.newProxyInstance(owner.getClassLoader(),
                            new Class<?>[]{com.google.protobuf.MessageLite.class, QueueUndoActions.Command.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("toByteArray")) return hide.toByteArray();
                                if (method.getName().equals("patch_parseQueueCommand")) return
                                    app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                                        .parseFrom((byte[]) args[0]);
                                throw new AssertionError("Unexpected native command operation: " + method.getName());
                            });
                        var feedback = NativePlaylistQueuePatch.onQueueMenuCommand(nativeCommand, Map.of(
                            "com.google.android.libraries.youtube.innertube.endpoint.tag", controller.panel));
                        require(field("queue").get(null) == null && controller.playback.clears == 1,
                            "Menu Clear must detach only the local sequence");
                        var undo = app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint
                            .parseFrom(feedback.toByteArray()).getAddToToastAction().getItem().getToastRenderer()
                            .getActionButton().getButtonRenderer().getServiceEndpoint();
                        NativePlaylistQueuePatch.onQueueUndoCommand(undo);
                        var restored = (PlaylistQueue) field("queue").get(null);
                        require(restored.entries().equals(before.entries()) && restored.selectedIndex() == 2,
                            "Menu Clear Undo must retain the selected duplicate and complete order");
                        require(controller.playback.attaches == 1 && LoadVideoPatch.opens == 0,
                            "Menu Clear Undo must reuse live playback without reopening or seeking");
                    }
                    public static void normalUndoTiming() throws Exception {
                        install(backend(true, true));
                        var accessible = Utils.getContext().accessibility;
                        // Enabled accessibility without touch exploration retains native normal timing.
                        accessible.enabled = true;
                        require(QueueSaveFeedback.confirmationDuration(feedback("token")) == 5000,
                            "Normal Clear snackbar must last five seconds");
                        var first = field("pendingUndo").get(null);
                        require(QueueSaveFeedback.confirmationDuration(feedback("foreign")) == 0,
                            "A stale/foreign token must not override a snackbar");
                        require(field("pendingUndo").get(null) == first,
                            "Foreign feedback must not replace the active capability");
                        Utils.advanceBy(1000);
                        QueueSaveFeedback.confirmationDuration(feedback("token"));
                        var renewed = field("pendingUndo").get(null);
                        require(renewed != first, "Re-created feedback must replace the timer identity");
                        Utils.advanceBy(4000);
                        require(field("pendingUndo").get(null) == renewed,
                            "An old timer must not expire re-created feedback");
                        Utils.advanceBy(999);
                        require(field("pendingUndo").get(null) == renewed, "Undo must remain through the visible window");
                        Utils.advanceBy(1);
                        require(field("pendingUndo").get(null) == null, "Normal Undo must expire with the five-second confirmation");
                    }
                    public static void accessibleUndoTiming() throws Exception {
                        install(backend(true, true));
                        var accessible = Utils.getContext().accessibility;
                        accessible.enabled = true; accessible.touchExploration = true;
                        require(QueueSaveFeedback.confirmationDuration(feedback("token")) == 0,
                            "Accessible feedback must retain the verified native twenty-second action timeout");
                        var current = field("pendingUndo").get(null);
                        Utils.advanceBy(10000);
                        require(field("pendingUndo").get(null) == current,
                            "Accessible Undo must not expire at the old ten-second deadline");
                        Utils.advanceBy(9999);
                        require(field("pendingUndo").get(null) == current, "Accessible Undo must remain through the visible window");
                        Utils.advanceBy(1);
                        require(field("pendingUndo").get(null) == null, "Accessible Undo must expire at twenty seconds");
                    }
                    public static void excludedPatch() throws Exception {
                        Settings.LOCAL_QUEUE.value = true; AddToQueuePatch.included = false;
                        require(!(boolean) call("morpheQueueTakesPriority"), "Excluded old patch must not block native queue");
                    }
                    public static void disabledSetting() throws Exception {
                        AddToQueuePatch.included = true; Settings.LOCAL_QUEUE.value = false;
                        require(!(boolean) call("morpheQueueTakesPriority"), "Disabled Local queue must not block native queue");
                    }
                    public static void acceptedHandoff() throws Exception {
                        AddToQueuePatch.included = true; Settings.LOCAL_QUEUE.value = false;
                        var live = backend(true, true);
                        PlaylistQueue before = install(live);
                        // Settings notification can precede the setting cache write.
                        listener().onSharedPreferenceChanged(null, Settings.LOCAL_QUEUE.key);
                        require(field("queue").get(null) == before && clears == 0, "Do not detach inside preference notification");
                        Settings.LOCAL_QUEUE.value = true;
                        Utils.drain();
                        require(clears == 1 && field("queue").get(null) == null, "Detach local backend once before releasing ownership");
                        require(((WeakReference<?>) field("nativePlaybackRef").get(null)).get() == null, "Release backend reference");
                        require(field("publishedModel").get(null) == null, "Release accepted panel ownership");
                        require(field("pendingQueueStart").get(null) == null && field("pendingUndo").get(null) == null,
                            "Cancel both late starts and stale Undo");
                        require(OpenVideosFullscreenHookPatch.minimized == null, "Cancel pending minimized start");
                        require(QueueFullscreenActions.refreshes == 1 && Logger.errors == 0, "Refresh after successful handoff");
                        require(LocalQueuePatch.savedItems.equals(List.of("saved-video")), "Preserve Morphe items");
                        NativePlaylistQueuePatch.onNativeQueueResponse(live); Utils.drain();
                        require(clears == 1 && field("queue").get(null) == null, "Late response must not attach native queue");
                        Settings.LOCAL_QUEUE.value = false;
                        listener().onSharedPreferenceChanged(null, Settings.LOCAL_QUEUE.key); Utils.drain();
                        require(field("queue").get(null) == null, "Turning Local queue off must not resurrect old queue");
                    }
                    public static void failedDetach() throws Exception {
                        AddToQueuePatch.included = true; Settings.LOCAL_QUEUE.value = true;
                        var rejected = backend(true, false); var before = install(rejected);
                        call("yieldToMorpheQueue");
                        require(clears == 1 && field("queue").get(null) == before, "Failed detach must retain ownership for retry");
                        require(((WeakReference<?>) field("nativePlaybackRef").get(null)).get() == rejected,
                            "Failed detach must retain backend reference");
                        require(field("pendingQueueStart").get(null) == null && field("pendingUndo").get(null) == null,
                            "Failed detach must still invalidate stale work");
                        require(Logger.errors == 1 && QueueFullscreenActions.refreshes == 0, "Report failure without false UI release");
                    }
                    public static void foreignBackend() throws Exception {
                        AddToQueuePatch.included = true; Settings.LOCAL_QUEUE.value = true;
                        install(backend(false, true)); call("yieldToMorpheQueue");
                        require(clears == 0 && field("queue").get(null) == null, "Never clear an unrelated backend");
                    }
                    public static void pendingStart() throws Exception {
                        AddToQueuePatch.included = true; Settings.LOCAL_QUEUE.value = true;
                        field("pendingQueueStart").set(null, new QueueStart(new PlaylistQueue("abcdefghijk", "Pending"), ""));
                        call("tryStartQueue");
                        require(field("pendingQueueStart").get(null) == null, "Cancel first-video start while Morphe has priority");
                    }
                }
            """.trimIndent())
        }
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-classpath", "${classes.path}${File.pathSeparator}${protobuf.path}",
                "-d", output.path, *stubs.map { it.path }.toTypedArray(), fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL(), classes.toURI().toURL()),
                GeneratedMessageLite::class.java.classLoader).use { loader ->
                loader.loadClass("app.morphe.extension.youtube.patches.queue.RuntimeFixture")
                    .getMethod(scenario).invoke(null)
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
