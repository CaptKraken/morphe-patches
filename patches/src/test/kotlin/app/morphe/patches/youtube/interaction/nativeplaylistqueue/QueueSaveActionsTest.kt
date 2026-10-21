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

class QueueSaveActionsTest {
    @Test
    fun `native picker request owns the full immutable queue selection and ordinary saves pass through`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val output = createTempDirectory("playlist-queue-save").toFile()
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val fixture = File(output, "SaveFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import java.util.*;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.*;
                public class SaveFixture {
                    static void require(boolean value) { if (!value) throw new AssertionError(); }
                    static void rejects(Runnable action) {
                        try { action.run(); throw new AssertionError("Expected invalid selection rejection"); }
                        catch (IllegalArgumentException expected) {}
                    }
                    static class Endpoint implements QueueSaveActions.Endpoint {
                        List<String> videoIds;
                        public List<String> patch_getQueueSaveVideoIds() { return videoIds; }
                        public void patch_setQueueSaveVideoIds(List<String> ids) { videoIds = ids; }
                    }
                    public static String run() throws Exception {
                        var ids = new ArrayList<>(List.of("abcdefghijk", "lmnopqrstuv", "abcdefghijk"));
                        var open = QueueSaveActions.openPicker(ids);
                        require(open.getAddToPlaylistEndpoint().getVideoId().equals("abcdefghijk"));
                        var endpoint = new Endpoint();
                        require(QueueSaveActions.copyVideoIds(endpoint) == null);
                        QueueSaveActions.prepare(endpoint, ids);
                        var expected = List.copyOf(ids);
                        ids.clear(); // Queue clear/reorder cannot change the already opened selection.
                        var savedArguments = QueueSaveActions.copyVideoIds(endpoint);
                        require(savedArguments.equals(expected));
                        savedArguments.add("ZYXWVUTSRQP");
                        require(QueueSaveActions.copyVideoIds(endpoint).equals(expected));
                        var request = new ArrayList<>(List.of("abcdefghijk"));
                        QueueSaveActions.onPickerRequest(() -> endpoint.videoIds, request);
                        require(request.equals(expected)); // Real separate IDs, including duplicates and order.
                        QueueSaveActions.onPickerRequest(() -> endpoint.videoIds, request);
                        require(request.equals(expected)); // Recreating the native request must not duplicate IDs.
                        var ordinary = List.of("0123456789_"); // Immutable to catch any unowned write.
                        QueueSaveActions.onPickerRequest(() -> null, ordinary);
                        require(ordinary.equals(List.of("0123456789_")));
                        rejects(() -> QueueSaveActions.openPicker(List.of()));
                        rejects(() -> QueueSaveActions.prepare(endpoint, List.of()));
                        return "ok";
                    }
                }
            """.trimIndent())
        }
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-classpath", protobuf.path, "-d", output.path,
                File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueSaveActions.java").path,
                File(generated, "IconOuterClass.java").path,
                File(generated, "queue/PlaylistQueueRenderer.java").path, fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                assertEquals("ok", loader.loadClass("app.morphe.extension.youtube.patches.queue.SaveFixture")
                    .getMethod("run").invoke(null))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
