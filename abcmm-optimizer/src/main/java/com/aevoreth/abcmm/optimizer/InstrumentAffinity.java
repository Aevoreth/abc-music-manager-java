package com.aevoreth.abcmm.optimizer;

/**
 * Preference that a player be assigned an equivalent physical instrument.
 * When {@code required} is false, {@code missPenalty} is charged at most once per
 * assigned part, and only when no occurrence part preference replaces it.
 * When {@code required} is true, {@code missPenalty} is a priority, not a score.
 * Higher priority takes the instrument when a song has fewer matching parts than
 * players who always want it. Equal priority lets the search choose who takes it,
 * and the others may play anything else. The priority is not charged.
 * Zero is allowed.
 */
public record InstrumentAffinity(
        long playerId, long instrumentId, double missPenalty, boolean required) {

    public InstrumentAffinity(long playerId, long instrumentId, double missPenalty) {
        this(playerId, instrumentId, missPenalty, false);
    }

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
