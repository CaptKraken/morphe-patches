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

class QueueUndoActionsTest {
    @Test
    fun `undo restores duplicate rows without rewinding selection and rejects stale edits or sessions`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val output = createTempDirectory("playlist-queue-undo").toFile()
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube")
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val fixture = File(output, "UndoFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.NavigationEndpoint;
                public class UndoFixture {
                    static void require(boolean value) { if (!value) throw new AssertionError(); }
                    public static String run() throws Exception {
                        String a = "abcdefghijk", b = "ZYXWVUTSRQP", c = "12345678901";
                        var before = new PlaylistQueue(a, "A").playLast(b).playLast(a).playLast(c);
                        var after = before.remove(1);
                        // The second A is a distinct row. Undo must retain that row's selection.
                        var current = after.select(1).withRepeatMode(PlaylistQueue.REPEAT_ONE);
                        var restored = QueueUndoActions.restore(before, after, current, a);
                        require(restored.entries().equals(before.entries()));
                        require(restored.selectedIndex() == 2);
                        require(restored.repeatMode() == PlaylistQueue.REPEAT_ONE);
                        require(QueueUndoActions.restore(before, after, after.move(0, 1), a) == null);
                        require(QueueUndoActions.restore(before, after, after.select(2), c).selectedIndex() == 3);
                        // Removing a preceding row adjusts selection; Undo adjusts it back.
                        before = before.select(2);
                        after = before.remove(0);
                        restored = QueueUndoActions.restore(before, after, after, a);
                        require(restored.selectedIndex() == 2);
                        require(QueueUndoActions.restore(before, after, after.playLast(b), a) == null);
                        require(QueueUndoActions.restore(before, after, after.move(0, 2), a) == null);
                        require(QueueUndoActions.restore(before, after, after, c) == null);
                        require(QueueUndoActions.restore(before, after, null, a) == null);
                        // Clearing is reversible only while the same ordinary video is playing.
                        require(QueueUndoActions.restore(before, null, null, a) == before);
                        require(QueueUndoActions.restore(before, null, null, b) == null);
                        require(QueueUndoActions.restore(before, null, new PlaylistQueue(a, "new"), a) == null);
                        var feedback = NavigationEndpoint.parseFrom(
                            QueueUndoActions.feedback("capability", "Removed", "Undo").toByteArray());
                        var toast = feedback.getAddToToastAction().getItem().getToastRenderer();
                        require(toast.getResponseText().getSimpleText().equals("Removed"));
                        var undo = toast.getActionButton().getButtonRenderer().getServiceEndpoint();
                        require(undo.getQueueUndoToken().equals("capability"));
                        require(!undo.hasHideEnclosingAction() && !undo.hasPlaylistEditEndpoint());
                        // Native snackbar callbacks have no authoritative queue row/menu tag.
                        // A matching capability alone reaches Undo; expired ones are consumed.
                        int[] actions = {0};
                        require(QueueUndoActions.dispatchUndo(undo, "capability", () -> actions[0]++));
                        require(actions[0] == 1);
                        require(QueueUndoActions.dispatchUndo(undo, null, () -> actions[0]++));
                        require(QueueUndoActions.dispatchUndo(undo, "newer", () -> actions[0]++));
                        require(actions[0] == 1);
                        require(!QueueUndoActions.dispatchUndo(NavigationEndpoint.getDefaultInstance(),
                            "capability", () -> actions[0]++));
                        require(!QueueUndoActions.dispatchUndo(null, "capability", () -> actions[0]++));
                        require(actions[0] == 1);
                        return "ok";
                    }
                }
            """.trimIndent())
        }
        try {
            val sources = File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue")
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-classpath", protobuf.path, "-d", output.path,
                File(sources, "PlaylistQueue.java").path, File(sources, "QueueUndoActions.java").path,
                File(generated, "IconOuterClass.java").path,
                File(generated, "queue/PlaylistQueueRenderer.java").path, fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                assertEquals("ok", loader.loadClass("app.morphe.extension.youtube.patches.queue.UndoFixture")
                    .getMethod("run").invoke(null))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
