package com.aevoreth.abcmm.domain.library;

/**
 * One {@code PlayLog} row for history UI.
 */
public record PlayLogEntry(
        long id,
        String playedAt,
        String setlistName,
        String contextNote) {
}
