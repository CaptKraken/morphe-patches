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
import kotlin.test.assertNull

class VideoMetadataTest {
    private fun withParser(block: ((String, String, String, String) -> List<String>?) -> Unit) {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val sources = File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue")
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube/queue/QueueCardRenderer.java")
        val output = createTempDirectory("playlist-queue-metadata").toFile()
        val protobufLibrary = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val fixture = File(output, "MetadataFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import app.morphe.extension.youtube.innertube.queue.QueueCardRenderer.*;
                public class MetadataFixture {
                    private static AttributedText text(String content) {
                        return AttributedText.newBuilder().setContent(content).build();
                    }
                    public static String[] parse(String url, String title, String info, String channel) throws Exception {
                        var row = MetadataRow.newBuilder().addMetadataParts(MetadataPart.newBuilder().setText(text(info)));
                        var metadata = LockupMetadataViewModel.newBuilder().setTitle(text(title)).setMetadata(
                            ContentMetadata.newBuilder().setContentMetadataViewModel(
                                ContentMetadataViewModel.newBuilder().addMetadataRows(row)));
                        var video = VideoData.newBuilder().setDragAndDropUrl(url).setLockupMetadata(
                            LockupMetadata.newBuilder().setLockupMetadataViewModel(metadata));
                        var contextual = VideoWithContextModel.newBuilder().setVideoWithContextData(
                            VideoWithContextData.newBuilder().setVideoData(video));
                        var element = Element.newBuilder().setType(ElementType.newBuilder().setComponentType(
                            ComponentType.newBuilder().setModel(CardModel.newBuilder().setVideoWithContextModel(contextual))));
                        var parsed = VideoMetadata.fromElement(element.build().toByteArray());
                        if (parsed == null) return null;
                        parsed = parsed.withChannelName(channel);
                        return new String[]{parsed.videoId(), parsed.title(), parsed.channelName(), parsed.videoInfo()};
                    }
                }
            """.trimIndent())
        }
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "17", "-classpath", protobufLibrary.path,
                "-d", output.path, File(sources, "PlaylistQueue.java").path,
                File(sources, "VideoMetadata.java").path, generated.path, fixture.path
            ))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                val parse = loader.loadClass("app.morphe.extension.youtube.patches.queue.MetadataFixture")
                    .getMethod("parse", *Array(4) { String::class.java })
                block { url, title, info, channel ->
                    (parse.invoke(null, url, title, info, channel) as Array<*>?)?.map { it as String }
                }
            }
        } finally {
            output.deleteRecursively()
        }
    }

    @Test
    fun `metadata is accepted only with a complete native card identity and title`() = withParser { parse ->
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        assertEquals(listOf("abcdefghijk", "A title", "", ""), parse(url, "A title", "", ""))
        assertNull(parse("https://www.youtube.com/playlist?list=PLabcdefghijk", "A playlist", "", ""))
        assertNull(parse("$url&list=RDabcdefghijk", "A mix", "", ""))
        assertEquals(listOf("abcdefghijk", "A video in a playlist", "", ""),
            parse("$url&list=PLabcdefghijk&index=2", "A video in a playlist", "", ""))
        assertNull(parse("https://www.youtube.com/watch?v=invalid", "A title", "", ""))
        assertNull(parse(url, "", "", ""))
    }

    @Test
    fun `native localized text is preserved and only an exact known channel prefix is split`() = withParser { parse ->
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        val channel = "La chaîne · 🎵"
        val info = "1,8 M de vues · il y a 2 ans"
        assertEquals(listOf("abcdefghijk", "Une vidéo 🎵", channel, info),
            parse(url, "Une vidéo 🎵", "$channel · $info", channel))
        assertEquals(listOf("abcdefghijk", "Une vidéo 🎵", "", "$channel · $info"),
            parse(url, "Une vidéo 🎵", "$channel · $info", "Another channel"))
    }
}
