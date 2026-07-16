package com.aevoreth.abcmm.domain.setlist;

/**
 * Setlist row from the {@code Setlist} table.
 */
public record SetlistInfo(
        long id,
        String name,
        Long bandLayoutId,
        Long folderId,
        int sortOrder,
        boolean locked,
        Integer defaultChangeDurationSeconds,
        String notes,
        String setDate,
        String setTime,
        Integer targetDurationSeconds) {
}
