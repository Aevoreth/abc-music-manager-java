package com.aevoreth.abcmm.domain.optimizer;

/**
 * Per-player multiplier for a penalized instrument switch. Replaces the band default
 * for that player. It is not added to the default.
 */
public record PlayerSwitchWeight(long playerId, double multiplier) {

    public PlayerSwitchWeight {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        OptimizationWeights.requireNonNegative(multiplier, "multiplier");
    }
}
