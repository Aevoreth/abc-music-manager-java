package com.aevoreth.abcmm.optimizer;

import java.util.Objects;

import com.aevoreth.abcmm.domain.optimizer.SearchBudgetPreset;

/**
 * Read-only load result. {@code problem} is null when the database cannot yield a snapshot.
 * Saved rules and the search-budget preset are present whenever {@code problem} is.
 * {@code budgetSeconds} is the caller's override and stays null when the preset should be used.
 */
public record LoadOutcome(
        long setlistId,
        Long seed,
        Long budgetSeconds,
        Integer requestedPlayers,
        OptimizationProblem problem,
        FeasibilityReport report,
        OptimizationRules rules,
        SearchBudgetPreset searchBudget) {

    public LoadOutcome {
        if (setlistId <= 0) {
            throw new IllegalArgumentException("setlistId");
        }
        if (report == null) {
            throw new IllegalArgumentException("report");
        }
        if (problem != null) {
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(searchBudget, "searchBudget");
        }
    }
}
