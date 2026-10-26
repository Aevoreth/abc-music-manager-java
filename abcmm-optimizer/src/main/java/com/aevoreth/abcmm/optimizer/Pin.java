package com.aevoreth.abcmm.optimizer;

/**
 * Absolute assignment of one part on one setlist-item occurrence to one player.
 * Pins are not stored in the database during the proof of concept; callers supply them.
 */
public record Pin(long setlistItemId, int partNumber, long playerId) {

    public Pin {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
    }
}
