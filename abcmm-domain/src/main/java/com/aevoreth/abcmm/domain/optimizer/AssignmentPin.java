package com.aevoreth.abcmm.domain.optimizer;

/**
 * Absolute assignment of one part on one setlist-item occurrence to one player.
 * Keyed by setlist item id, so two copies of the same song stay independent.
 * A pin is not a weight and is not a {@link PartPreferenceWeight}.
 */
public record AssignmentPin(long setlistItemId, int partNumber, long playerId) {

    public AssignmentPin {
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
