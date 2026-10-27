package com.aevoreth.abcmm.domain.optimizer;

import java.time.Duration;

/**
 * Named search limit stored on a band profile. The CLI flag {@code --budget-seconds}
 * overrides it for one run.
 */
public enum SearchBudgetPreset {
    QUICK(Duration.ofSeconds(2)),
    NORMAL(Duration.ofSeconds(10)),
    THOROUGH(Duration.ofSeconds(60));

    private final Duration budget;

    SearchBudgetPreset(Duration budget) {
        this.budget = budget;
    }

    public Duration budget() {
        return budget;
    }
}
