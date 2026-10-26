package com.aevoreth.abcmm.optimizer;

import java.util.List;

/**
 * One setlist item. Identity is the item id, so the same song can occur more than once.
 */
public record SongOccurrence(
        long setlistItemId,
        long songId,
        int position,
        List<RequiredPart> parts) {

    public SongOccurrence {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (songId <= 0) {
            throw new IllegalArgumentException("songId");
        }
        parts = List.copyOf(parts);
    }
}
