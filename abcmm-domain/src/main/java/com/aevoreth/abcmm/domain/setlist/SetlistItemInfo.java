package com.aevoreth.abcmm.domain.setlist;

/**
 * Setlist item joined with song title/composers/parts/duration for the editor table.
 */
public record SetlistItemInfo(
        long id,
        long setlistId,
        long songId,
        String songTitle,
        String songComposers,
        Integer songDurationSeconds,
        int partCount,
        int position,
        Integer overrideChangeDurationSeconds,
        Long songLayoutId) {
}
