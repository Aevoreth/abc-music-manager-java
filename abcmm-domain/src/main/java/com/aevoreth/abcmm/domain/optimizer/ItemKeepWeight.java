package com.aevoreth.abcmm.domain.optimizer;

/**
 * Keep-assignment multiplier for every part of one setlist item.
 * A part keep weight replaces this for that part. Neither is a pin.
 */
public record ItemKeepWeight(long setlistItemId, double multiplier) {

    public ItemKeepWeight {
        if (setlistItemId <= 0) {
            throw new IllegalArgumentException("setlistItemId");
        }
        OptimizationWeights.requireNonNegative(multiplier, "multiplier");
    }
}
