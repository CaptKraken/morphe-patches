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

class QueueStartTest {
    @Test
    fun `only the requested video starts minimized and unrelated navigation supersedes the request`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val output = createTempDirectory("playlist-queue-start").toFile()
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val stubs = mapOf(
            "Nullable" to "package androidx.annotation; public @interface Nullable {}",
            "Logger" to """
                package app.morphe.extension.shared;
                public class Logger {
                    public static void printDebug(java.util.function.Supplier<String> message) {}
                    public static void printException(java.util.function.Supplier<String> message, Exception error) {}
                    public static void printException(java.util.function.Supplier<String> message) {}
                }
            """,
            "Utils" to """
                package app.morphe.extension.shared;
                public class Utils {
                    public static Runnable expiration;
                    public static void verifyOnMainThread() {}
                    public static void runOnMainThreadDelayed(Runnable action, long delay) { expiration = action; }
                }
            """,
            "VideoInformation" to """
                package app.morphe.extension.youtube.patches;
                public class VideoInformation {
                    public interface PlaybackController {}
                    public static String videoId = "";
                    public static String getVideoId() { return videoId; }
                }
            """,
            "PlayerType" to """
                package app.morphe.extension.youtube.shared;
                public enum PlayerType {
                    WATCH_WHILE_FULLSCREEN, WATCH_WHILE_MINIMIZED;
                    public static PlayerType getCurrent() { return WATCH_WHILE_MINIMIZED; }
                }
            """,
            "Settings" to """
                package app.morphe.extension.youtube.settings;
                import app.morphe.extension.youtube.patches.OpenVideosFullscreenHookPatch.OpenFullscreenMode;
                public class Settings {
                    public static java.util.function.Supplier<OpenFullscreenMode> OPEN_VIDEOS_FULLSCREEN =
                        () -> OpenFullscreenMode.LANDSCAPE;
                }
            """
        ).map { (name, source) -> File(output, "$name.java").apply { writeText(source.trimIndent()) } }
        val fixture = File(output, "StartFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import java.util.*;
                import app.morphe.extension.youtube.patches.OpenVideosFullscreenHookPatch;
                import app.morphe.extension.youtube.patches.VideoInformation;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.*;
                public class StartFixture {
                    static void require(boolean value) { if (!value) throw new AssertionError(); }
                    enum Status { VIDEO_PLAYING }
                    static void verifyFullscreenCoexistence() {
                        int[] fullscreenCalls = {0};
                        OpenVideosFullscreenHookPatch.setFullscreenInterface(
                            new OpenVideosFullscreenHookPatch.FullscreenInterface() {
                                public void patch_exitFullscreen() {}
                                public void patch_enterFullscreen() { fullscreenCalls[0]++; }
                            });
                        VideoInformation.videoId = "abcdefghijk";
                        OpenVideosFullscreenHookPatch.keepVideoMinimized("abcdefghijk");
                        require(OpenVideosFullscreenHookPatch.doNotOpenVideoFullscreenPortrait(false));
                        OpenVideosFullscreenHookPatch.initialize(null);
                        OpenVideosFullscreenHookPatch.playerStatusChanged(Status.VIDEO_PLAYING);
                        require(fullscreenCalls[0] == 0);
                        // The next ordinary video retains the user's automatic fullscreen mode.
                        VideoInformation.videoId = "ZYXWVUTSRQP";
                        OpenVideosFullscreenHookPatch.initialize(null);
                        OpenVideosFullscreenHookPatch.playerStatusChanged(Status.VIDEO_PLAYING);
                        require(fullscreenCalls[0] == 1);
                        OpenVideosFullscreenHookPatch.keepVideoMinimized("ZYXWVUTSRQP");
                        OpenVideosFullscreenHookPatch.keepVideoMinimized(null); // Cancelled queue start.
                        OpenVideosFullscreenHookPatch.initialize(null);
                        OpenVideosFullscreenHookPatch.playerStatusChanged(Status.VIDEO_PLAYING);
                        require(fullscreenCalls[0] == 2);
                        OpenVideosFullscreenHookPatch.keepVideoMinimized("abcdefghijk");
                        var oldExpiration = app.morphe.extension.shared.Utils.expiration;
                        OpenVideosFullscreenHookPatch.keepVideoMinimized("ZYXWVUTSRQP");
                        oldExpiration.run();
                        require(OpenVideosFullscreenHookPatch.doNotOpenVideoFullscreenPortrait(false));
                        app.morphe.extension.shared.Utils.expiration.run();
                        require(!OpenVideosFullscreenHookPatch.doNotOpenVideoFullscreenPortrait(false));
                    }
                    public static String run() throws Exception {
                        var start = new QueueStart(new PlaylistQueue("abcdefghijk", "Selected video"), "lmnopqrstuv");
                        var requested = NavigationEndpoint.newBuilder().setWatchEndpoint(
                            WatchEndpoint.newBuilder().setVideoId("abcdefghijk")).build();
                        var unrelated = NavigationEndpoint.newBuilder().setWatchEndpoint(
                            WatchEndpoint.newBuilder().setVideoId("ZYXWVUTSRQP")).build();
                        var context = Map.of("native-context", new Object(), "start_watch_minimized", false);
                        var minimized = start.minimizedContext(requested, context);
                        require(minimized.get("start_watch_minimized").equals(true));
                        require(minimized.get("native-context") == context.get("native-context"));
                        require(context.get("start_watch_minimized").equals(false));
                        require(start.minimizedContext(requested, null).get("start_watch_minimized").equals(true));
                        require(start.minimizedContext(unrelated, context) == context);
                        require(start.minimizedContext(NavigationEndpoint.getDefaultInstance(), context) == context);
                        require(!start.isSupersededBy(""));
                        require(!start.isSupersededBy("lmnopqrstuv")); // Late callback from prior playback.
                        require(!start.isSupersededBy("abcdefghijk"));
                        require(start.isSupersededBy("ZYXWVUTSRQP"));
                        verifyFullscreenCoexistence();
                        return "ok";
                    }
                }
            """.trimIndent())
        }
        try {
            val sources = File(root,
                "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue")
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-classpath", protobuf.path, "-d", output.path,
                File(sources, "PlaylistQueue.java").path, File(sources, "QueueStart.java").path,
                File(generated, "IconOuterClass.java").path,
                File(generated, "queue/PlaylistQueueRenderer.java").path,
                File(sources.parentFile, "OpenVideosFullscreenHookPatch.java").path,
                fixture.path, *stubs.map { it.path }.toTypedArray()))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                assertEquals("ok", loader.loadClass("app.morphe.extension.youtube.patches.queue.StartFixture")
                    .getMethod("run").invoke(null))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
