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
    fun `first video save uses a real target without batch ownership`() {
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
                    public static String run() throws Exception {
                        var ids = new ArrayList<>(List.of("abcdefghijk", "lmnopqrstuv", "abcdefghijk"));
                        var original = Map.of(QueueSaveActions.ENDPOINT_TAG, new Object(), "other", "preserved");
                        var open = QueueSaveActions.openSheet(ids);
                        var context = QueueSaveActions.prepareContext(open, original, ids);
                        require(context != original && context.get("other").equals("preserved"));
                        require(!context.containsKey(QueueSaveActions.ENDPOINT_TAG));
                        require(original.containsKey(QueueSaveActions.ENDPOINT_TAG));
                        String params = open.getShowSheetCommand().getPanelLoadingStrategy().getRequestTemplate().getParams();
                        var target = SavePanelParams.parseFrom(Base64.getUrlDecoder().decode(params.replace("%3D", "=")));
                        require(target.getTarget().getVideoId().equals("abcdefghijk"));
                        require(QueueSaveActions.openSheet(List.of("abcdefghijk")).equals(open));
                        rejects(() -> QueueSaveActions.prepareContext(open, original, List.of("new selection")));
                        rejects(() -> QueueSaveActions.openSheet(List.of()));
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
