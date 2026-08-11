package com.aevoreth.abcmm.domain.playback;

import java.util.Objects;

/**
 * One entry in the runtime playback playlist / queue.
 */
public record PlayQueueItem(
        long songId,
        String title,
        String composers,
        Integer durationSeconds,
        int partCount,
        Long setlistId,
        Long setlistItemId) {

    public PlayQueueItem {
        if (songId <= 0) {
            throw new IllegalArgumentException("songId must be positive");
        }
        title = title == null ? "" : title;
        composers = composers == null ? "" : composers;
        partCount = Math.max(0, partCount);
    }

    public static PlayQueueItem ofSong(
            long songId,
            String title,
            String composers,
            Integer durationSeconds,
            int partCount) {
        return new PlayQueueItem(songId, title, composers, durationSeconds, partCount, null, null);
    }

    public static PlayQueueItem ofSetlistItem(
            long songId,
            String title,
            String composers,
            Integer durationSeconds,
            int partCount,
            long setlistId,
            long setlistItemId) {
        Objects.requireNonNull(title, "title");
        return new PlayQueueItem(
                songId, title, composers, durationSeconds, partCount, setlistId, setlistItemId);
    }
}
