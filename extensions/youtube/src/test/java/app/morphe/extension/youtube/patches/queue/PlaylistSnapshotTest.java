/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;

public class PlaylistSnapshotTest {
    private static final String A = "abcdefghijk";
    private static final String B = "lmnopqrstuv";
    private static final String C = "0123456789_";
    private static final String D = "ZYXWVUTSRQP";
    private static final String PLAYLIST = "PL-saved";

    private static PlaylistQueueRenderer.Panel.Builder panel(List<String> videos, int selected) {
        var result = PlaylistQueueRenderer.Panel.newBuilder().setPlaylistId(PLAYLIST)
                .setVideoCount(videos.size()).setSelectedIndex(selected).setLocalSelectedIndex(selected);
        for (int index = 0; index < videos.size(); index++) {
            result.addItems(PlaylistQueueRenderer.Item.newBuilder().setVideo(
                    PlaylistQueueRenderer.Video.newBuilder().setVideoId(videos.get(index))
                            .setSelected(index == selected)
                            .setTitle(PlaylistQueueRenderer.Text.newBuilder().addRuns(
                                    PlaylistQueueRenderer.TextRun.newBuilder().setText("Title " + index)))
                            .setShortBylineText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText("Channel"))
                            .setLengthText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText("1:23"))));
        }
        return result;
    }

    @Test public void retainsEarlierAndLaterEntriesWhenAddingAfterThePlayingVideo() throws Exception {
        var snapshot = PlaylistSnapshot.fromPanel(panel(List.of(A, B, C), 1).build(), PLAYLIST, B);
        assertNotNull(snapshot);
        var updated = snapshot.queue().playNext(D);
        assertEquals(List.of(A, B, D, C), updated.entries().stream().map(PlaylistQueue.Entry::videoId).toList());
        assertEquals(1, updated.selectedIndex());
        assertEquals(List.of("Title 0", "Title 1", "Title 2"),
                snapshot.queue().entries().stream().map(PlaylistQueue.Entry::title).toList());
        assertEquals("Channel", snapshot.metadata().get(1).channelName());
        assertEquals("1:23", snapshot.metadata().get(1).duration());
        assertTrue(snapshot.metadata().get(1).isComplete());
    }

    @Test public void preservesDuplicateOccurrencesAndTheirExactSelection() throws Exception {
        var snapshot = PlaylistSnapshot.fromPanel(panel(List.of(A, B, A, C), 2).build(), PLAYLIST, A);
        assertNotNull(snapshot);
        assertEquals(List.of(A, B, A, D, C), snapshot.queue().playNext(D).entries().stream()
                .map(PlaylistQueue.Entry::videoId).toList());
        assertEquals(2, snapshot.queue().selectedIndex());
    }

    @Test public void rejectsPartialOrUnknownPlaylistsWithoutTurningTheirLoadedWindowIntoAQueue() throws Exception {
        var loaded = panel(List.of(A, B, C), 1);
        assertNull(PlaylistSnapshot.fromPanel(loaded.clone().setVideoCount(2396)
                .setSelectedIndex(416).build(), PLAYLIST, B));
        assertNull(PlaylistSnapshot.fromPanel(loaded.clone().setSelectedIndex(416).build(), PLAYLIST, B));
        assertNull(PlaylistSnapshot.fromPanel(loaded.clone().clearVideoCount().build(), PLAYLIST, B));
    }

    @Test public void rejectsStaleOrAmbiguousSelection() throws Exception {
        var loaded = panel(List.of(A, B, C), 1);
        assertNull(PlaylistSnapshot.fromPanel(loaded.build(), "PL-other", B));
        assertNull(PlaylistSnapshot.fromPanel(loaded.build(), PLAYLIST, A));
        assertNull(PlaylistSnapshot.fromPanel(loaded.clone().setItems(0,
                loaded.getItems(0).toBuilder().setVideo(loaded.getItems(0).getVideo().toBuilder()
                        .setSelected(true))).build(), PLAYLIST, B));
        assertNull(PlaylistSnapshot.fromPanel(new Object(), PLAYLIST, B));
    }

    @Test public void usesNativePositionWhenRowsOmitTheSelectedFlag() throws Exception {
        var loaded = panel(List.of(A, B, A), 2);
        loaded.setItems(2, loaded.getItems(2).toBuilder()
                .setVideo(loaded.getItems(2).getVideo().toBuilder().clearSelected()));
        var snapshot = PlaylistSnapshot.fromPanel(loaded.build(), PLAYLIST, A);
        assertNotNull(snapshot);
        assertEquals(2, snapshot.queue().selectedIndex());
    }

    @Test public void rejectsUnavailableRowsInsteadOfDroppingThem() throws Exception {
        var loaded = panel(List.of(A, B, C), 1);
        loaded.setItems(2, loaded.getItems(2).toBuilder().setVideo(loaded.getItems(2).getVideo().toBuilder()
                .setUnplayableText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText("Unavailable"))));
        assertNull(PlaylistSnapshot.fromPanel(loaded.build(), PLAYLIST, B));
    }
}
