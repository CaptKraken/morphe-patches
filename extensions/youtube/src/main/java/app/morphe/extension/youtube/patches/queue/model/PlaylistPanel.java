/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** YouTube 21.20.405 playlist panel wire format, decoded by the host's own protobuf runtime. */
public final class PlaylistPanel {
    private PlaylistPanel() {}

    public static byte[] encode(LocalQueue.Snapshot queue, String title) {
        return encode(queue, title, null);
    }

    /** Keep the actual playing video's endpoint, including its existing playback context. */
    public static byte[] encode(LocalQueue.Snapshot queue, String title, byte[] playingEndpoint) {
        return encode(queue, title, playingEndpoint, "Remove from queue");
    }

    public static byte[] encode(LocalQueue.Snapshot queue, String title, byte[] playingEndpoint,
                                String removeLabel) {
        return encode(queue, title, playingEndpoint, removeLabel, Map.of(),
                new QueueRatings.Labels("Like", "Remove like", "Dislike", "Remove dislike"));
    }

    public static byte[] encode(LocalQueue.Snapshot queue, String title, byte[] playingEndpoint,
                                String removeLabel, Map<String, QueueRatings.State> ratings,
                                QueueRatings.Labels labels) {
        return encode(queue, title, playingEndpoint, removeLabel, ratings, labels, "Save playlist");
    }

    public static byte[] encode(LocalQueue.Snapshot queue, String title, byte[] playingEndpoint,
                                String removeLabel, Map<String, QueueRatings.State> ratings,
                                QueueRatings.Labels labels, String saveLabel) {
        Wire panel = new Wire().text(1, title).number(3, queue.selected())
                .text(5, "").number(6, queue.entries().size()).number(8, 1);
        for (int i = 0; i < queue.entries().size(); i++) {
            LocalQueue.Entry entry = queue.entries().get(i);
            // Endpoints deliberately omit playlistId: the queue has no server-side playlist.
            Wire watch = new Wire().text(1, entry.videoId()).number(3, i).text(13, entry.id());
            Wire endpoint = new Wire().message(48687757, watch);
            Wire thumbnail = new Wire().message(1, new Wire()
                    .text(1, "https://i.ytimg.com/vi/" + entry.videoId() + "/hqdefault.jpg")
                    .number(2, 480).number(3, 360));
            Wire row = new Wire().message(1, new Wire().text(4, entry.title()))
                    .message(3, thumbnail).number(6, i == queue.selected() ? 1 : 0)
                    .data(7, i == queue.selected() && playingEndpoint != null
                            ? playingEndpoint : endpoint.bytes())
                    .text(10, entry.videoId()).text(26, entry.id())
                    .message(27, new Wire().number(1, 2)).number(36, 1);
            // Existing channel and video_info TextViews in YouTube's compact playlist row.
            if (!entry.details().channel().isEmpty()) {
                row.message(2, new Wire().text(4, entry.details().channel()));
            }
            if (!entry.details().duration().isEmpty()) {
                row.message(4, new Wire().text(4, entry.details().duration()));
            }
            if (!entry.details().statistics().isEmpty()) {
                row.message(31, new Wire().text(4, entry.details().statistics()));
            }
            QueueRatings.State rating = ratings.get(entry.videoId());
            if (rating != null) {
                row.message(32, ratingButton(entry.videoId(), rating, true, labels));
                row.message(32, ratingButton(entry.videoId(), rating, false, labels));
            }
            // The playing row has no native swipe removal action.
            if (i != queue.selected()) {
                // Native swipe-button renderer. HideEnclosingCommand emits the tagged bfzy row
                // as ahmg; our owned-panel gesture hook removes that exact local entry.
                // No playlist-edit or network endpoint is included in this action.
                Wire remove = new Wire().number(1, 1) // Host STYLE_DEFAULT.
                        // Omit visible text: the host's swipe presenter selects its compact
                        // icon-only layout when buttonRenderer.text is absent.
                        .message(8, new Wire().number(1, 426)) // Host VISIBILITY_OFF (crossed-out eye).
                        .message(9, new Wire().message(77297742, new Wire()))
                        .message(10, new Wire().text(2, removeLabel)); // Native accessibility label.
                row.message(22, new Wire().message(65153809, remove));
            }
            panel.message(2, new Wire().message(51779701, row));
        }
        if (!queue.entries().isEmpty()) {
            // The header's existing three-dot listener opens this native menu. The native
            // creation dialog owns title/privacy, sign-in, confirmation and the request.
            // Snapshot video IDs avoid a synthetic source playlist and remain stable while
            // the dialog is open. Saving does not turn the local queue into that playlist.
            Wire createDialog = new Wire();
            for (LocalQueue.Entry entry : queue.entries()) createDialog.text(1, entry.videoId());
            Wire save = new Wire().message(1, new Wire().text(4, saveLabel))
                    .message(2, new Wire().number(1, 58)) // Native SAVE_PLAYLIST bookmark icon.
                    .message(3, new Wire().message(85650004, createDialog));
            Wire menu = new Wire().message(1, new Wire().message(66441108, save));
            panel.message(26, new Wire().message(66439850, menu));
        }
        return panel.bytes();
    }

    private static Wire ratingButton(String videoId, QueueRatings.State state, boolean like,
                                     QueueRatings.Labels labels) {
        int status = like ? QueueRatings.LIKE : QueueRatings.DISLIKE;
        Wire button = new Wire().number(3, state.status() == status ? 1 : 0)
                .message(5, new Wire().number(1, like ? 160 : 51)) // Native LIKE / DISLIKE.
                .message(8, new Wire().number(1, like ? 301 : 302)) // Native selected icons.
                .message(7, ratingEndpoint(videoId, status))
                .message(10, ratingEndpoint(videoId, QueueRatings.NEUTRAL))
                .message(19, new Wire().message(1, new Wire().text(2, like ? labels.like() : labels.dislike())))
                .message(20, new Wire().message(1, new Wire().text(2, like ? labels.unlike() : labels.undoDislike())));
        Wire presenter = new Wire().message(17, new Wire().text(1, videoId)) // Video target; no playlist target.
                .number(like ? 20 : 21, 1)
                .message(26, new Wire().message(79971800, button))
                .text(27, state.entityKey());
        return new Wire().message(124608045, presenter);
    }

    private static Wire ratingEndpoint(String videoId, int status) {
        // Native LikeCommand, which performs sign-in, account-scoped state updates and requests.
        return new Wire().message(63158558, new Wire().number(1, status)
                .message(2, new Wire().text(1, videoId)));
    }

    private static final class Wire {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Wire number(int field, long value) {
            varint((long) field << 3);
            varint(value);
            return this;
        }
        Wire text(int field, String value) {
            return data(field, value.getBytes(StandardCharsets.UTF_8));
        }
        Wire message(int field, Wire value) { return data(field, value.bytes()); }
        Wire data(int field, byte[] data) {
            varint(((long) field << 3) | 2);
            varint(data.length);
            out.write(data, 0, data.length);
            return this;
        }
        void varint(long value) {
            while ((value & ~127L) != 0) {
                out.write(((int) value & 127) | 128);
                value >>>= 7;
            }
            out.write((int) value);
        }
        byte[] bytes() { return out.toByteArray(); }
    }
}
