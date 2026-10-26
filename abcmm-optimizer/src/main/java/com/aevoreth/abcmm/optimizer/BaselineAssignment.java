package com.aevoreth.abcmm.optimizer;

/**
 * Effective part for one layout player on one setlist item.
 * A null {@code partNumber} means the player is idle. {@code instrumentId} is the
 * required part's instrument when assigned, otherwise null.
 */
public record BaselineAssignment(
        long setlistItemId,
        long playerId,
        Integer partNumber,
        Long instrumentId) {

    public BaselineAssignment {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        if (partNumber == null) {
            instrumentId = null;
        } else if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
    }
}
