package com.aevoreth.abcmm.domain.optimizer;

/**
 * Keep-assignment multiplier for one part of one setlist item.
 * Replaces the item keep multiplier for that part.
 */
public record PartKeepWeight(long setlistItemId, int partNumber, double multiplier) {

    public PartKeepWeight {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber");
        }
        OptimizationWeights.requireNonNegative(multiplier, "multiplier");
    }
}
