/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import com.google.protobuf.GeneratedMessageLite
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoMetadataTest {
    private class Parser(private val method: Method, private val searchMethod: Method) {
        operator fun invoke(
            url: String,
            title: String,
            info: String = "",
            channel: String = "",
            avatarLabel: String = "",
            description: String = ""
        ): List<String>? = (method.invoke(null, url, title, info, channel, avatarLabel, description) as Array<*>?)
            ?.map { it as String }

        fun merge(json: String, videoId: String): List<String> =
            (searchMethod.declaringClass.getMethod("merge", String::class.java, String::class.java)
                .invoke(null, json, videoId) as Array<*>).map { it as String }

        fun search(json: String, videoId: String): List<String>? =
            (searchMethod.invoke(null, json, videoId) as Array<*>?)?.map { it as String }
    }

    private fun withParser(block: (Parser) -> Unit) {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val sources = File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue")
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube/queue/QueueCardRenderer.java")
        val output = createTempDirectory("playlist-queue-metadata").toFile()
        val protobufLibrary = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val jsonLibrary = File(JSONObject::class.java.protectionDomain.codeSource.location.toURI())
        val fixture = File(output, "MetadataFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import app.morphe.extension.youtube.innertube.queue.QueueCardRenderer.*;
                public class MetadataFixture {
                    private static AttributedText text(String content) {
                        return AttributedText.newBuilder().setContent(content).build();
                    }
                    public static String[] parse(String url, String title, String info,
                            String channel, String label, String description) throws Exception {
                        var row = MetadataRow.newBuilder().addMetadataParts(MetadataPart.newBuilder().setText(text(info)));
                        var metadata = LockupMetadataViewModel.newBuilder().setTitle(text(title)).setMetadata(
                            ContentMetadata.newBuilder().setContentMetadataViewModel(
                                ContentMetadataViewModel.newBuilder().addMetadataRows(row)));
                        var video = VideoData.newBuilder().setAvatar(Avatar.newBuilder().setAccessibilityText(label))
                            .setDragAndDropUrl(url).setLockupMetadata(
                                LockupMetadata.newBuilder().setLockupMetadataViewModel(metadata));
                        var contextual = VideoWithContextModel.newBuilder().setVideoWithContextData(
                            VideoWithContextData.newBuilder().setVideoData(video).setAccessibilityText(description));
                        var element = Element.newBuilder().setType(ElementType.newBuilder().setComponentType(
                            ComponentType.newBuilder().setModel(CardModel.newBuilder().setVideoWithContextModel(contextual))));
                        var parsed = VideoMetadata.fromElement(element.build().toByteArray());
                        if (parsed == null) return null;
                        parsed = parsed.withChannelName(channel);
                        return new String[]{parsed.videoId(), parsed.title(), parsed.channelName(), parsed.videoInfo()};
                    }
                    public static String[] merge(String json, String videoId) throws Exception {
                        var card = new VideoMetadata(videoId, "Native title", "Starbound Extra", "157 views · 46 minutes ago", "29:04", false);
                        var lookup = VideoMetadata.fromSearch(new org.json.JSONObject(json), videoId);
                        var merged = card.withLookup(lookup).withCapture(card).withChannelName("Starbound Extra");
                        var reverse = lookup.withCapture(card).withChannelName("Starbound Extra");
                        merged = merged.withLookup(card);
                        return new String[]{Boolean.toString(card.isComplete()), merged.channelName(),
                            reverse.channelName(), merged.title(), Boolean.toString(merged.isComplete())};
                    }
                    public static String[] search(String json, String videoId) throws Exception {
                        var parsed = VideoMetadata.fromSearch(new org.json.JSONObject(json), videoId);
                        if (parsed == null) return null;
                        return new String[]{parsed.title(), parsed.channelName(), parsed.videoInfo(), parsed.duration()};
                    }
                }
            """.trimIndent())
        }
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "17", "-classpath", protobufLibrary.path + File.pathSeparator + jsonLibrary.path,
                "-d", output.path, File(sources, "PlaylistQueue.java").path,
                File(sources, "VideoMetadata.java").path, generated.path, fixture.path
            ))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                val parse = loader.loadClass("app.morphe.extension.youtube.patches.queue.MetadataFixture")
                    .getMethod("parse", *Array(6) { String::class.java })
                val search = loader.loadClass("app.morphe.extension.youtube.patches.queue.MetadataFixture")
                    .getMethod("search", String::class.java, String::class.java)
                block(Parser(parse, search))
            }
        } finally {
            output.deleteRecursively()
        }
    }

    @Test
    fun `lookup preserves collaborating channels and localized details only for the requested video`() = withParser { parse ->
        val response = """
            {"contents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
                {"compactVideoRenderer":{"videoId":"other_video","title":{"runs":[{"text":"Wrong result"}]}}},
                {"compactVideoRenderer":{"videoId":"waZyE4Xl6bo",
                    "title":{"runs":[{"text":"A native title"}]},
                    "longBylineText":{"runs":[{"text":"Starbound Extra"},{"text":" and "},{"text":"Starbound HFY"}]},
                    "shortBylineText":{"runs":[{"text":"Starbound Extra"}]},
                    "shortViewCountText":{"runs":[{"text":"1,8 M de vues"}]},
                    "publishedTimeText":{"runs":[{"text":"il y a 2 ans"}]},
                    "lengthText":{"runs":[{"text":"29:04"}]}}}
            ]}}]}}}
        """.trimIndent()
        assertEquals(listOf("A native title", "Starbound Extra and Starbound HFY", "1,8 M de vues · il y a 2 ans", "29:04"),
            parse.search(response, "waZyE4Xl6bo"))
        assertNull(parse.search(response, "abcdefghijk"))
        assertNull(parse.search("{}", "waZyE4Xl6bo"))
    }

    @Test
    fun `uploader-only cards require full byline lookup and cannot overwrite its collaborators`() = withParser { parse ->
        val response = """
            {"contents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
                {"compactVideoRenderer":{"videoId":"waZyE4Xl6bo", "title":{"runs":[{"text":"Search title"}]},
                    "longBylineText":{"runs":[{"text":"Starbound Extra"},{"text":" and "},{"text":"Starbound HFY"}]},
                    "shortViewCountText":{"runs":[{"text":"157 views"}]},
                    "publishedTimeText":{"runs":[{"text":"46 minutes ago"}]}}}
            ]}}]}}}
        """.trimIndent()
        assertEquals(listOf("false", "Starbound Extra and Starbound HFY", "Starbound Extra and Starbound HFY",
            "Native title", "true"), parse.merge(response, "waZyE4Xl6bo"))
    }

    @Test
    fun `metadata is accepted only with a complete native card identity and title`() = withParser { parse ->
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        assertEquals(listOf("abcdefghijk", "A title", "", ""), parse(url, "A title"))
        assertNull(parse("https://www.youtube.com/playlist?list=PLabcdefghijk", "A playlist"))
        assertNull(parse("$url&list=RDabcdefghijk", "A mix"))
        assertEquals(listOf("abcdefghijk", "A video in a playlist", "", ""),
            parse("$url&list=PLabcdefghijk&index=2", "A video in a playlist"))
        assertNull(parse("https://www.youtube.com/watch?v=invalid", "A title"))
        assertNull(parse(url, ""))
    }

    @Test
    fun `native localized text is preserved and only an exact known channel prefix is split`() = withParser { parse ->
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        val channel = "La chaîne · 🎵"
        val info = "1,8 M de vues · il y a 2 ans"
        assertEquals(listOf("abcdefghijk", "Une vidéo 🎵", channel, info),
            parse(url, "Une vidéo 🎵", "$channel · $info", channel = channel))
        assertEquals(listOf("abcdefghijk", "Une vidéo 🎵", "", "$channel · $info"),
            parse(url, "Une vidéo 🎵", "$channel · $info", channel = "Another channel"))
    }

    @Test
    fun `native accessibility text identifies a long channel without flyout metadata`() = withParser { parse ->
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        val channel = "Starbound Extra and Starbound HFY"
        val info = "157 views · 46 minutes ago"
        assertEquals(listOf("abcdefghijk", "We Sent Earth Our Mightiest Weapons", channel, info),
            parse(url, "We Sent Earth Our Mightiest Weapons", "$channel · $info",
                avatarLabel = "Go to channel $channel"))
        assertEquals(listOf("abcdefghijk", "A title", channel, info),
            parse(url, "A title", "$channel · $info",
                description = "A title - 10 minutes - $channel - 157 views - 46 minutes ago - play video"))
        assertEquals(listOf("abcdefghijk", "A title", "Stars · HFY", info),
            parse(url, "A title", "Stars · HFY · $info", avatarLabel = "Accéder à la chaîne Stars · HFY"))
        assertEquals(listOf("abcdefghijk", "A title", "", "$channel · $info"),
            parse(url, "A title", "$channel · $info", avatarLabel = "Go to channel Another channel"))
    }
}
