/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import static org.junit.Assert.*;

import com.google.protobuf.ByteString;
import org.junit.Test;

import java.util.List;

import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;

public class PlaylistPagingTest {
    private static final String A = "aaaaaaaaaaa", B = "bbbbbbbbbbb", C = "ccccccccccc";
    private static final String D = "ddddddddddd", X = "xxxxxxxxxxx", Y = "yyyyyyyyyyy";
    private static final PlaylistQueue.Source SOURCE = new PlaylistQueue.Source("WL",
            new PlaylistPage.Account("account", false));

    private static PlaylistPage page(int offset, int selected, String... ids) {
        var rows = new java.util.ArrayList<PlaylistPage.Row>();
        for (int index = 0; index < ids.length; index++) {
            var metadata = new VideoMetadata(ids[index], ids[index], "", "", "", false);
            rows.add(new PlaylistPage.Row(new PlaylistQueue.Entry(ids[index], ids[index]), metadata,
                    new PlaylistPage.Anchor("WL", ids[index], offset + index, "entry" + (offset + index),
                            "params", ByteString.EMPTY)));
        }
        return new PlaylistPage(rows, selected, 2401);
    }

    private static List<String> ids(PlaylistQueue queue) {
        return queue.entries().stream().map(PlaylistQueue.Entry::videoId).toList();
    }

    @Test public void pagesAppearBeforeAppendedTailAndAfterPlayNextInserts() {
        var initial = page(0, 1, A, B);
        var queue = PlaylistQueue.fromPage(initial, SOURCE).playNext(X).playLast(Y);
        var gap = queue.firstGap();
        queue = queue.extend(gap, page(0, 1, A, B, C, D), false);
        assertEquals(List.of(A, B, X, C, D, Y), ids(queue));
        assertSame(initial.rows().get(1).entry(), queue.currentEntry());
        assertEquals(1, queue.selectedIndex());
        assertNotNull(queue.nearbyGap(4, true, 0));
        assertNotNull(queue.nearbyGap(5, false, 0));
    }

    @Test public void overlappingPagesNeverReAddRemovedOrMovedSourceOccurrences() {
        var queue = PlaylistQueue.fromPage(page(0, 0, A, B, A), SOURCE);
        var gap = queue.firstGap();
        queue = queue.remove(1).move(1, 0);
        queue = queue.extend(gap, page(0, 2, A, B, A, C), false);
        assertEquals(List.of(A, A, C), ids(queue));
        assertEquals(1, queue.selectedIndex());
    }

    @Test public void prefixFetchRetainsSelectedDuplicateAndLocalTailSelection() {
        var initial = page(2, 1, A, B);
        var queue = PlaylistQueue.fromPage(initial, SOURCE).playLast(X).select(2);
        var prefix = queue.firstGap();
        queue = queue.extend(prefix, page(0, 2, C, D, A, B), true);
        assertEquals(List.of(C, D, A, B, X), ids(queue));
        assertEquals(X, queue.currentEntry().videoId());
        assertEquals(4, queue.selectedIndex());
        assertTrue(queue.firstGap().next());
    }

    @Test public void countDoesNotMarkTheSourceCompleteAndShuffleRequiresCompletion() {
        var queue = PlaylistQueue.fromPage(page(0, 0, A, B), SOURCE).playLast(X);
        assertFalse(queue.isComplete());
        assertThrows(IllegalStateException.class, queue::shuffle);
        queue = queue.extend(queue.firstGap(), page(0, 1, A, B), true);
        assertTrue(queue.isComplete());
        assertEquals(List.of(A, B, X), ids(queue));
        assertSame(queue.currentEntry(), queue.shuffle().currentEntry());
    }

    @Test public void reportedTotalStaysStableAsWindowsLoadIncludingHiddenInclusiveEnd() {
        var queue = PlaylistQueue.fromPage(page(0, 0, A, B), SOURCE).playNext(X);
        assertEquals(2402, queue.totalCount());
        assertEquals(3, queue.entries().size());
        var updated = queue.extend(queue.firstGap(), page(0, 1, A, B, C, D), false);
        assertEquals(2402, updated.totalCount());
        assertEquals(5, updated.entries().size());
        updated = updated.extend(updated.firstGap(), page(0, 3, A, B, C, D), true);
        assertTrue(updated.isComplete());
        assertEquals(2402, updated.totalCount());
        assertEquals(2402, updated.shuffle().totalCount());
        assertEquals(2402, queue.totalCount());
    }

    @Test public void onlyLocalContentEditsChangeReportedTotalAndOrdinaryQueuesCountTheirEntries() {
        var original = PlaylistQueue.fromPage(page(0, 0, A, B), SOURCE);
        var queue = original.playNext(X).playLast(Y);
        assertEquals(2403, queue.totalCount());
        queue = queue.move(3, 1).select(1).withRepeatMode(PlaylistQueue.REPEAT_ALL);
        assertEquals(2403, queue.totalCount());
        assertEquals(2403, queue.remove(queue.selectedIndex()).totalCount());
        queue = queue.remove(0);
        assertEquals(2402, queue.totalCount());
        queue = queue.append(List.of(new PlaylistQueue.Entry(X, X), new PlaylistQueue.Entry(X, X)));
        assertEquals(2404, queue.totalCount());
        assertEquals(2401, original.totalCount());

        var ordinary = new PlaylistQueue(A, A).playNext(B).playLast(C);
        assertEquals(ordinary.entries().size(), ordinary.totalCount());
        ordinary = ordinary.remove(1).shuffle();
        assertEquals(ordinary.entries().size(), ordinary.totalCount());
    }

    @Test public void convertedMixUsesItsCurrentGeneratedBatchWithoutUnloadedPrefixOrSuffix() {
        for (int size : List.of(50, 100)) {
            String list = "RD-mix";
            var rows = new java.util.ArrayList<PlaylistPage.Row>();
            for (int index = 0; index < size; index++) {
                String id = String.format(java.util.Locale.ROOT, "%011d", index);
                var metadata = new VideoMetadata(id, "Mix title " + index, "Channel", "", "1:00", false, true);
                rows.add(new PlaylistPage.Row(new PlaylistQueue.Entry(id, metadata.title()), metadata,
                        new PlaylistPage.Anchor(list, id, index + 200, "mix" + index, "", ByteString.EMPTY)));
            }
            var page = new PlaylistPage(rows, size / 2, 5000);
            var queue = PlaylistQueue.fromPage(page, new PlaylistQueue.Source(list, SOURCE.account())).playNext(X);
            assertTrue(queue.isComplete());
            assertNull(queue.firstGap());
            assertEquals(size + 1, queue.totalCount());
            assertEquals(size + 1, queue.entries().size());
            assertSame(rows.get(size / 2).entry(), queue.shuffle().currentEntry());
        }
        var ordinary = PlaylistQueue.fromPage(page(200, 1, A, B, C), SOURCE);
        assertFalse(ordinary.isComplete());
        assertNotNull(ordinary.nearbyGap(0, false, 0));
        assertNotNull(ordinary.nearbyGap(2, true, 0));
    }

    @Test public void staleMissingOrShiftedAnchorCannotAdvanceQueue() {
        var queue = PlaylistQueue.fromPage(page(0, 0, A, B), SOURCE);
        var gap = queue.firstGap();
        assertThrows(IllegalArgumentException.class, () -> queue.extend(gap, page(1, 0, C, D), false));
        assertThrows(IllegalArgumentException.class, () -> queue.extend(gap, page(0, 1, A, B), false));
        var newer = queue.extend(gap, page(0, 1, A, B, C), false);
        assertFalse(newer.hasGap(gap));
        assertThrows(IllegalArgumentException.class, () -> newer.extend(gap, page(0, 1, A, B, C), false));
    }

    @Test public void parserAcceptsHiddenInclusiveCountsButRequiresSourceOccurrenceCommands() throws Exception {
        var panel = PlaylistQueueRenderer.Panel.newBuilder().setPlaylistId("WL").setVideoCount(2401);
        for (int index = 0; index < 3; index++) {
            String video = List.of(A, B, A).get(index);
            panel.addItems(PlaylistQueueRenderer.Item.newBuilder().setVideo(
                    PlaylistQueueRenderer.Video.newBuilder().setVideoId(video).setSelected(index == 2)
                            .setNavigationEndpoint(PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                                    .setWatchEndpoint(PlaylistQueueRenderer.WatchEndpoint.newBuilder()
                                            .setVideoId(video).setPlaylistId("WL").setIndex(index)))));
        }
        var parsed = PlaylistPage.fromPanel(panel.build(), "WL");
        assertNotNull(parsed);
        assertEquals(2, parsed.selectedIndex());
        assertEquals(3, parsed.rows().size());
        assertEquals(2401, parsed.totalCount());
        assertNull(PlaylistPage.fromPanel(panel.build(), "PL-other"));
        panel.setItems(2, panel.getItems(2).toBuilder().setVideo(panel.getItems(2).getVideo().toBuilder()
                .setNavigationEndpoint(PlaylistQueueRenderer.NavigationEndpoint.newBuilder()
                        .setWatchEndpoint(PlaylistQueueRenderer.WatchEndpoint.newBuilder()
                                .setVideoId(A).setPlaylistId("WL").setIndex(0)))));
        assertNull(PlaylistPage.fromPanel(panel.build(), "WL"));
    }

    @Test public void missingIndexAndUnknownCommandsNeverImplyTheSourceHasEnded() throws Exception {
        var source = PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setWatchEndpoint(
                PlaylistQueueRenderer.WatchEndpoint.newBuilder().setPlaylistId("WL").setVideoId(A)).build();
        assertFalse(PlaylistPage.endsSource(source, "WL"));
        assertTrue(PlaylistPage.endsSource(PlaylistQueueRenderer.NavigationEndpoint.getDefaultInstance(), "WL"));
        var nextVideo = source.toBuilder().setWatchEndpoint(source.getWatchEndpoint().toBuilder()
                .clearPlaylistId()).build();
        assertTrue(PlaylistPage.endsSource(nextVideo, "WL"));
        var bytes = new java.io.ByteArrayOutputStream();
        var output = com.google.protobuf.CodedOutputStream.newInstance(bytes);
        output.writeBytes(1234, ByteString.copyFromUtf8("unknown native command"));
        output.flush();
        var unknown = PlaylistQueueRenderer.NavigationEndpoint.parseFrom(bytes.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> PlaylistPage.endsSource(unknown, "WL"));
    }

    @Test public void unavailableUnselectedRowsDoNotNeedPlayableCommands() throws Exception {
        var available = PlaylistQueueRenderer.Item.newBuilder().setVideo(PlaylistQueueRenderer.Video.newBuilder()
                .setVideoId(A).setSelected(true).setNavigationEndpoint(
                        PlaylistQueueRenderer.NavigationEndpoint.newBuilder().setWatchEndpoint(
                                PlaylistQueueRenderer.WatchEndpoint.newBuilder().setVideoId(A)
                                        .setPlaylistId("PL-public").setIndex(0))));
        var unavailable = PlaylistQueueRenderer.Item.newBuilder().setVideo(PlaylistQueueRenderer.Video.newBuilder()
                .setUnplayableText(PlaylistQueueRenderer.Text.newBuilder().setSimpleText("Unavailable")));
        var following = available.clone().setVideo(available.getVideo().toBuilder().setSelected(false).setVideoId(B)
                .setNavigationEndpoint(available.getVideo().getNavigationEndpoint().toBuilder().setWatchEndpoint(
                        available.getVideo().getNavigationEndpoint().getWatchEndpoint().toBuilder()
                                .setVideoId(B).setIndex(2))));
        var panel = PlaylistQueueRenderer.Panel.newBuilder().setPlaylistId("PL-public").setVideoCount(3)
                .addItems(available).addItems(unavailable).addItems(following);
        var page = PlaylistPage.fromPanel(panel.build(), "PL-public");
        assertNotNull(page);
        assertEquals(List.of(A, B), page.rows().stream().map(row -> row.entry().videoId()).toList());
        assertEquals(0, page.selectedIndex());
        assertEquals(3, page.totalCount());
        panel.setItems(1, unavailable.setVideo(unavailable.getVideo().toBuilder().setSelected(true)));
        assertNull(PlaylistPage.fromPanel(panel.build(), "PL-public"));
    }
}
