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

class QueueRatingActionsTest {
    @Test
    fun `rating commands retain opaque parameters and cannot mix video targets`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "extensions/youtube/src/main/proto").isDirectory }
        val sources = File(root, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue")
        val generated = File(root,
            "extensions/youtube/build/generated/java/generateReleaseProto/java/app/morphe/extension/youtube/innertube")
        val output = createTempDirectory("playlist-queue-ratings").toFile()
        val protobuf = File(GeneratedMessageLite::class.java.protectionDomain.codeSource.location.toURI())
        val fixture = File(output, "RatingFixture.java").apply {
            writeText("""
                package app.morphe.extension.youtube.patches.queue;
                import com.google.protobuf.ByteString;
                import com.google.protobuf.CodedOutputStream;
                import java.io.ByteArrayOutputStream;
                import app.morphe.extension.youtube.innertube.NextResponseOuterClass.*;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;
                import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer.LikeEndpoint.Status;
                public class RatingFixture {
                    private static ByteString endpoint(String id, Status status) throws Exception {
                        var target = PlaylistQueueRenderer.RatingTarget.newBuilder().setVideoId(id);
                        var body = new ByteArrayOutputStream();
                        body.write(PlaylistQueueRenderer.LikeEndpoint.newBuilder().setStatus(status).setTarget(target).build().toByteArray());
                        var parameters = CodedOutputStream.newInstance(body);
                        parameters.writeString(6, "opaque server parameters");
                        parameters.flush();
                        var result = new ByteArrayOutputStream();
                        var command = CodedOutputStream.newInstance(result);
                        command.writeBytes(63158558, ByteString.copyFrom(body.toByteArray()));
                        command.flush();
                        return ByteString.copyFrom(result.toByteArray());
                    }
                    private static RatingButtonContainer button(ByteString endpoint) {
                        return RatingButtonContainer.newBuilder().setButtonViewModel(SecondaryButtonViewModel.newBuilder()
                            .setOnTap(Commands.newBuilder().setSerialCommand(SerialCommands.newBuilder()
                                .addCommands(Commands.getDefaultInstance())
                                .addCommands(Commands.newBuilder().setInnertubeCommand(endpoint)))))
                            .build();
                    }
                    public static String[] parse(String dislikeId, boolean omitUndo, int initialStatus) throws Exception {
                        String id = "abcdefghijk";
                        var likeEndpoint = endpoint(id, Status.LIKE);
                        var like = LikeButtonViewModel.newBuilder().setLikeStatusEntityKey("native entity key")
                            .setLikeStatusEntity(RatingStatusEntity.newBuilder().setLikeStatus(initialStatus))
                            .setToggleButtonViewModel(RatingToggleContainer.newBuilder().setToggleButtonViewModel(
                                RatingToggleViewModel.newBuilder().setDefaultButtonViewModel(button(likeEndpoint))
                                    .setToggledButtonViewModel(button(endpoint(id, Status.INDIFFERENT)))));
                        var dislike = DislikeButtonViewModel.newBuilder().setDislikeEntityKey("native entity key")
                            .setToggleButtonViewModel(RatingToggleContainer.newBuilder().setToggleButtonViewModel(
                                RatingToggleViewModel.newBuilder().setDefaultButtonViewModel(button(endpoint(dislikeId, Status.DISLIKE)))
                                    .setToggledButtonViewModel(button(omitUndo ? ByteString.EMPTY : endpoint(id, Status.INDIFFERENT)))));
                        var segmented = SegmentedLikeDislikeButtonViewModel.newBuilder()
                            .setLikeButtonViewModel(RatingLikeContainer.newBuilder().setLikeButtonViewModel(like))
                            .setDislikeButtonViewModel(RatingDislikeContainer.newBuilder().setDislikeButtonViewModel(dislike));
                        var element = NewElement.newBuilder().setType(Type.newBuilder().setComponentType(ComponentType.newBuilder()
                            .setModel(Model.newBuilder().setVideoActionBarModel(VideoActionBarModel.newBuilder()
                                .setVideoActionBarData(VideoActionBarData.newBuilder().addActionButtons(ActionButtons.newBuilder()
                                    .setPrimaryButtonViewModel(PrimaryButtonViewModel.newBuilder().setSegmentedLikeDislikeButtonViewModel(segmented))))))))
                            .build();
                        var actions = QueueRatingActions.fromElement(NewElement.parseFrom(element.toByteArray()));
                        if (actions == null) return null;
                        var buttons = actions.buttons("Like", "Dislike");
                        var nativeLike = buttons.get(0).getSlimMetadataToggleButton().getButton().getToggleButtonRenderer();
                        var nativeDislike = buttons.get(1).getSlimMetadataToggleButton().getButton().getToggleButtonRenderer();
                        return new String[]{actions.videoId(), actions.entityKey(),
                            Boolean.toString(likeEndpoint.equals(nativeLike.getDefaultServiceEndpoint())),
                            Boolean.toString(nativeLike.getIsToggled()), Boolean.toString(nativeDislike.getIsToggled()),
                            nativeLike.getAccessibilityData().getAccessibilityData().getLabel()};
                    }
                }
            """.trimIndent())
        }
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-classpath", protobuf.path, "-d", output.path,
                File(sources, "PlaylistQueue.java").path, File(sources, "QueueRatingActions.java").path,
                File(generated, "NextResponseOuterClass.java").path, File(generated, "IconOuterClass.java").path,
                File(generated, "queue/PlaylistQueueRenderer.java").path, fixture.path))
            URLClassLoader(arrayOf(output.toURI().toURL()), GeneratedMessageLite::class.java.classLoader).use { loader ->
                val method = loader.loadClass("app.morphe.extension.youtube.patches.queue.RatingFixture")
                    .getMethod("parse", String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                val result = (method.invoke(null, "abcdefghijk", false, 1) as Array<*>).toList()
                assertEquals(listOf("abcdefghijk", "native entity key", "true", "false", "true", "Like"), result)
                val liked = (method.invoke(null, "abcdefghijk", false, 0) as Array<*>).toList()
                assertEquals(listOf("abcdefghijk", "native entity key", "true", "true", "false", "Like"), liked)
                assertNull(method.invoke(null, "lmnopqrstuv", false, 1))
                assertNull(method.invoke(null, "abcdefghijk", true, 1))
            }
        } finally {
            output.deleteRecursively()
        }
    }
}
