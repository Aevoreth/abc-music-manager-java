package com.aevoreth.abcmm.domain.setlist;

/**
 * Setlist item joined with song title/duration for the editor table.
 */
public record SetlistItemInfo(
        long id,
        long setlistId,
        long songId,
        String songTitle,
        Integer songDurationSeconds,
        int position,
        Integer overrideChangeDurationSeconds,
        Long songLayoutId) {
}
