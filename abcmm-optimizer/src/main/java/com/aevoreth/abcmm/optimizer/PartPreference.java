package com.aevoreth.abcmm.optimizer;

/**
 * Soft preference that one occurrence part be played by one player.
 * Keyed by setlist item id, so two copies of the same song stay independent.
 * A hard {@link Pin} is not a preference and is never overridden by {@code penalty}.
 */
public record PartPreference(long setlistItemId, int partNumber, long playerId, double penalty) {

    public PartPreference {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        if (!Double.isFinite(penalty) || penalty < 0.0d) {
            throw new IllegalArgumentException("penalty");
        }
    }
}
