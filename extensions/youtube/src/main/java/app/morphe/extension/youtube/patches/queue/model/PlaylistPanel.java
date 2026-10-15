/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.queue.model;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
            // Native swipe-button renderer. HideEnclosingCommand emits the tagged bfzy row
            // as ahmg; our owned-panel gesture hook removes that exact local entry.
            // No playlist-edit or network endpoint is included in this action.
            Wire remove = new Wire().number(1, 1) // Host STYLE_DEFAULT.
                    .message(5, new Wire().text(4, removeLabel))
                    .message(8, new Wire().number(1, 55)) // Host DELETE icon.
                    .message(9, new Wire().message(77297742, new Wire()))
                    .message(10, new Wire().text(2, removeLabel)); // Native accessibility label.
            row.message(22, new Wire().message(65153809, remove));
            panel.message(2, new Wire().message(51779701, row));
        }
        return panel.bytes();
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
