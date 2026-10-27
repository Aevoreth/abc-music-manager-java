package com.aevoreth.abcmm.domain.optimizer;

/**
 * Preference that a player be assigned an equivalent physical instrument.
 * An occurrence part preference replaces the soft penalty for that slot. It does not stack.
 *
 * <p>When {@code required} is false, {@code missPenalty} is charged when the player
 * is assigned a different instrument. When {@code required} is true, {@code missPenalty}
 * is the priority for taking that instrument if the song does not have enough parts
 * for everyone who always wants it. It is not charged.
 */
public record InstrumentAffinityWeight(
        long playerId, long instrumentId, double missPenalty, boolean required) {

    public InstrumentAffinityWeight(long playerId, long instrumentId, double missPenalty) {
        this(playerId, instrumentId, missPenalty, false);
    }

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
