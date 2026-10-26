/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.queue;

import androidx.annotation.Nullable;

import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.innertube.queue.PlaylistQueueRenderer;

/** Read-only access to YouTube's authenticated watch-page service, without player callbacks. */
public final class PlaylistPages {
    private PlaylistPages() {
    }

    public interface Source {
        @Nullable
        PlaylistPage.Account patch_getPlaylistAccount();
        @Nullable
        Future<?> patch_requestPlaylistPage(String playlistId, @Nullable String videoId, int index,
                String params, String entryId, byte[] tracking, PlaylistPage.Account account);
        @Nullable
        MessageLite patch_getPlaylistPage(Object response);
        @Nullable
        MessageLite patch_getPlaylistNavigation(Object response);
    }

    private static volatile WeakReference<Source> source = new WeakReference<>(null);

    /**
     * Injection point.
     * <p>
     * Capture after native initialization. Neither this callback nor the bridge publishes a watch response.
     */
    public static void attach(Source owner) {
        source = new WeakReference<>(owner);
    }

    @Nullable
    static Source current() {
        return source.get();
    }

    record Result(PlaylistPage page, boolean end) {}

    /** Safe diagnostic stage; never includes private playlist IDs or request parameters. */
    static final class LoadFailure extends IOException {
        final String stage;

        LoadFailure(String stage, String message) {
            super(message);
            this.stage = stage;
        }
    }

    /** Open a collection without invoking the watch response actor or loading a player. */
    static PlaylistQueue first(Source owner, PlaylistQueue.Source expected) throws Exception {
        Utils.verifyOffMainThread();
        Object response = request(owner, expected, null, 0, "", "", new byte[0]);
        PlaylistPage page = page(owner, expected, response, "first page");
        // This is an explicit collection action, not a playing-playlist capture.
        // Start at its first playable occurrence even when the response highlights a hidden row.
        return PlaylistQueue.fromPage(new PlaylistPage(page.rows(), 0, page.totalCount()), expected);
    }

    static Result load(Source owner, PlaylistQueue.Source expected, PlaylistQueue.Gap gap) throws Exception {
        Utils.verifyOffMainThread();
        var anchor = gap.anchor();
        Object response = request(owner, expected, anchor.videoId(), anchor.index(),
                anchor.params(), anchor.entryId(), anchor.tracking().toByteArray());
        PlaylistPage page = page(owner, expected, response, "anchor " + anchor.index());
        int position = position(page, anchor);
        boolean advanced = gap.next() ? position + 1 < page.rows().size() : position > 0;
        if (advanced) {
            // A read-only page may still highlight the original playing video.
            // Merge validated source rows; never adopt its remote selection.
            return new Result(page, !gap.next() && page.rows().get(0).anchor().index() == 0);
        }
        // Navigation targets describe the selected response row. They establish
        // this boundary's end only when that row is the requested occurrence.
        if (page.selectedIndex() != position) {
            throw new LoadFailure("selection", "Playlist window selected a different occurrence");
        }
        MessageLite nativeNavigation = owner.patch_getPlaylistNavigation(response);
        if (nativeNavigation == null) {
            throw new LoadFailure("navigation", "Playlist navigation is unavailable");
        }
        var navigation = PlaylistQueueRenderer.PlaylistNavigation.parseFrom(nativeNavigation.toByteArray());
        var target = PlaylistPage.target(navigation, gap.next());
        if (target == null) {
            throw new LoadFailure("command", "Unrecognized playlist navigation");
        }
        if (PlaylistPage.endsSource(target, expected.playlistId())) {
            return new Result(page, true);
        }
        if (gap.next() && target.hasWatchPlaylistEndpoint()) {
            var watch = target.getWatchPlaylistEndpoint();
            if (!expected.playlistId().equals(watch.getPlaylistId()) || !watch.hasIndex()
                    || watch.getIndex() <= anchor.index()) {
                throw new LoadFailure("command", "Playlist position does not follow its source boundary");
            }
            // Native playlist-only commands have no video or occurrence ID.
            // One read-only request is enough: do not loop on an unchanged command.
            Object following = request(owner, expected, null, watch.getIndex(), watch.getParams(), "",
                    target.getClickTrackingParams().toByteArray());
            PlaylistPage continued = page(owner, expected, following, "position " + watch.getIndex());
            int boundary = position(continued, anchor);
            if (boundary + 1 < continued.rows().size()) {
                return new Result(continued, false);
            }
            // At a hidden tail the native command can wrap to an earlier playable
            // occurrence. Require the original last row too; an unknown, shifted,
            // missing or unchanged selection does not establish completion.
            if (continued.selectedIndex() >= 0 && continued.selectedIndex() < boundary) {
                return new Result(continued, true);
            }
            throw new LoadFailure("progress", "Native playlist position did not advance or wrap");
        }
        // A no-progress response is not proof of EOF, even if video_count is larger.
        throw new LoadFailure("progress", "Playlist window did not advance to its native navigation target");
    }

    private static Object request(Source owner, PlaylistQueue.Source expected, @Nullable String videoId,
                                  int index, String params, String entryId, byte[] tracking) throws Exception {
        if (owner != current() || !expected.account().equals(owner.patch_getPlaylistAccount())) {
            throw new LoadFailure("account", "Playlist account changed");
        }
        Future<?> future = owner.patch_requestPlaylistPage(expected.playlistId(), videoId, index,
                params, entryId, tracking, expected.account());
        if (future == null) {
            throw new LoadFailure("account", "Playlist account is unavailable");
        }
        Object response;
        try {
            response = future.get(15, TimeUnit.SECONDS);
        } finally {
            // A timed-out request must not retain its callback indefinitely.
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        if (owner != current() || !expected.account().equals(owner.patch_getPlaylistAccount())) {
            throw new LoadFailure("account", "Playlist account changed during request");
        }
        return response;
    }

    private static PlaylistPage page(Source owner, PlaylistQueue.Source expected, Object response, String request)
            throws Exception {
        MessageLite panel = owner.patch_getPlaylistPage(response);
        if (panel == null) {
            throw new LoadFailure("no-panel", request);
        }
        try {
            return PlaylistPage.readPanel(panel, expected.playlistId());
        } catch (PlaylistPage.InvalidPanel ex) {
            throw new LoadFailure(ex.reason, request + "; " + ex.getMessage());
        }
    }

    private static int position(PlaylistPage page, PlaylistPage.Anchor anchor) throws LoadFailure {
        int position = page.find(anchor);
        if (position < 0) {
            throw new LoadFailure("anchor", "Playlist window no longer contains its requested occurrence");
        }
        return position;
    }
}
