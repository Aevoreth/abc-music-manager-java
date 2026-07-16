package com.aevoreth.abcmm.domain.playback;

import java.util.Objects;

/**
 * One entry in the runtime playback playlist / queue.
 */
public record PlayQueueItem(
        long songId,
        String title,
        Long setlistId,
        Long setlistItemId) {

    public PlayQueueItem {
        if (songId <= 0) {
            throw new IllegalArgumentException("songId must be positive");
        }
        title = title == null ? "" : title;
    }

    public static PlayQueueItem ofSong(long songId, String title) {
        return new PlayQueueItem(songId, title, null, null);
    }

    public static PlayQueueItem ofSetlistItem(long songId, String title, long setlistId, long setlistItemId) {
        Objects.requireNonNull(title, "title");
        return new PlayQueueItem(songId, title, setlistId, setlistItemId);
    }
}
