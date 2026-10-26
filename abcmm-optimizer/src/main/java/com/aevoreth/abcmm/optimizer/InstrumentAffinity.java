package com.aevoreth.abcmm.optimizer;

/**
 * Soft preference that a player be assigned an equivalent physical instrument.
 * {@code missPenalty} is charged at most once per assigned part, and only when no
 * occurrence part preference replaces it. Zero is allowed.
 */
public record InstrumentAffinity(long playerId, long instrumentId, double missPenalty) {

    public InstrumentAffinity {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId");
        }
        if (instrumentId <= 0) {
            throw new IllegalArgumentException("instrumentId");
        }
        if (!Double.isFinite(missPenalty) || missPenalty < 0.0d) {
            throw new IllegalArgumentException("missPenalty");
        }
    }
}
