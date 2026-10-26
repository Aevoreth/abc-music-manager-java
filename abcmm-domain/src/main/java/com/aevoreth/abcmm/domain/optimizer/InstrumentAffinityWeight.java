package com.aevoreth.abcmm.domain.optimizer;

/**
 * Soft preference that a player be assigned an equivalent physical instrument.
 * An occurrence part preference replaces this for that slot. It does not stack.
 */
public record InstrumentAffinityWeight(long playerId, long instrumentId, double missPenalty) {

    public InstrumentAffinityWeight {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        if (instrumentId <= 0) {
            throw new IllegalArgumentException("instrumentId");
        }
        OptimizationWeights.requireNonNegative(missPenalty, "missPenalty");
    }
}
