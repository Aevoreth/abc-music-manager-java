package com.aevoreth.abcmm.domain.optimizer;

/**
 * Soft preference that one occurrence part be played by one player.
 * Keyed by setlist item id, so two copies of the same song stay independent.
 * A hard pin is a different constraint and is not stored as a preference.
 */
public record PartPreferenceWeight(long setlistItemId, int partNumber, long playerId, double penalty) {

    public PartPreferenceWeight {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        OptimizationWeights.requireNonNegative(penalty, "penalty");
    }
}
