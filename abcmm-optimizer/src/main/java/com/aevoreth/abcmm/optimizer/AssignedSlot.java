package com.aevoreth.abcmm.optimizer;

/**
 * One player's part on one setlist item. A null {@code partNumber} means idle.
 * {@code instrumentId} is optional metadata. Scoring uses the required part's
 * instrument; a stored id that is not the same physical instrument is invalid.
 */
public record AssignedSlot(long setlistItemId, long playerId, Integer partNumber, Long instrumentId) {

    public AssignedSlot {
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
