package com.aevoreth.abcmm.optimizer;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Limits one search. A zero budget returns the repaired start without neighborhood
 * moves. A null cancellation supplier never cancels. A null listener receives nothing.
 */
public record SearchBudget(
        long seed,
        Duration budget,
        BooleanSupplier cancelled,
        SearchListener listener) {

    public SearchBudget {
        if (seed < 0) {
            throw new IllegalArgumentException("seed");
        }
        Objects.requireNonNull(budget, "budget");
        if (budget.isNegative()) {
            throw new IllegalArgumentException("budget");
        }
    }

    public static SearchBudget of(long seed, Duration budget) {
        return new SearchBudget(seed, budget, null, null);
    }
}
