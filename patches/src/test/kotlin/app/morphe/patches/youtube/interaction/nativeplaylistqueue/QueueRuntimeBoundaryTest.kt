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

    // NativePlaylistQueuePatch depends on Android settings, scheduling and UI classes.
    // Isolate those boundaries while executing its actual compiled handoff code.
    private fun withFixture(scenario: String) {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val classes = File(root,
            "extensions/youtube/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val output = createTempDirectory("playlist-queue-provider").toFile()
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
            "PlayerType" to "package app.morphe.extension.youtube.shared; public enum PlayerType { NONE }",
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
                        field("pendingUndo").set(null, create.newInstance("token", queue, null,
                            new WeakReference<>(playback)));
                        clears = 0; Logger.errors = 0; QueueFullscreenActions.refreshes = 0;
                        return queue;
                    }
                    static SharedPreferences.OnSharedPreferenceChangeListener listener() throws Exception {
                        return (SharedPreferences.OnSharedPreferenceChangeListener) field("QUEUE_SETTING_LISTENER").get(null);
                    }
                    static com.google.protobuf.MessageLite feedback(String token) {
                        return QueueUndoActions.feedback(token, "Removed", "Undo")
                            .getAddToToastAction().getItem().getToastRenderer();
                    }
                    public static void normalUndoTiming() throws Exception {
                        install(backend(true, true));
                        var accessible = Utils.getContext().accessibility;
                        // Enabled accessibility without touch exploration retains native normal timing.
                        accessible.enabled = true;
                        require(QueueSaveFeedback.confirmationDuration(feedback("token")) == 5000,
                            "Normal removal/Clear snackbar must last five seconds");
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
