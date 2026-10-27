package com.aevoreth.abcmm.optimizer;

/** Soft instrument-first preferences, independent of actual ownership. */
final class PlacementRules {
    private PlacementRules() {}

    static double penalty(
            OptimizationProblem problem, OptimizationRules rules, long player, long instrument) {
        double penalty = 0;
        for (var rule : rules.instrumentPlacements()) {
            if (PhysicalInstrumentEquivalence.sameInstrument(
                            instrument, rule.instrumentId(), problem.equivalentInstrumentIds())
                    && (rule.avoidedPlayerIds().contains(player)
                            || (!rule.preferredPlayerIds().isEmpty()
                                    && !rule.preferredPlayerIds().contains(player))))
                penalty += rule.missPenalty();
        }
        return penalty;
    }
}
