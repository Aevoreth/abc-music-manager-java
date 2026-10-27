package com.aevoreth.abcmm.domain.optimizer;

import java.util.List;

/** Soft instrument-first preferences. Ownership and pins remain independent hard constraints. */
public record InstrumentPlacementRule(
        long instrumentId,
        List<Long> preferredPlayerIds,
        List<Long> avoidedPlayerIds,
        double missPenalty) {
    public InstrumentPlacementRule {
        if (instrumentId <= 0) throw new IllegalArgumentException("instrumentId");
        OptimizationWeights.requireNonNegative(missPenalty, "missPenalty");
        preferredPlayerIds = canonical(preferredPlayerIds);
        avoidedPlayerIds = canonical(avoidedPlayerIds);
        if (preferredPlayerIds.stream().anyMatch(avoidedPlayerIds::contains)) {
            throw new IllegalArgumentException(
                    "A character cannot both prefer and avoid the same instrument.");
        }
    }

    private static List<Long> canonical(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Character ids must be positive.");
        }
        return ids.stream().distinct().sorted().toList();
    }
}
