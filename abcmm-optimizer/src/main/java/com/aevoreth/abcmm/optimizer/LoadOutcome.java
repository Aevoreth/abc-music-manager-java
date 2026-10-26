package com.aevoreth.abcmm.optimizer;

/**
 * Read-only load result. {@code problem} is null when the database cannot yield a snapshot.
 */
public record LoadOutcome(
        long setlistId,
        Long seed,
        Long budgetSeconds,
        Integer requestedPlayers,
        OptimizationProblem problem,
        FeasibilityReport report) {

    public LoadOutcome {
        if (setlistId <= 0) {
            throw new IllegalArgumentException("setlistId");
        }
        if (report == null) {
            throw new IllegalArgumentException("report");
        }
    }
}
